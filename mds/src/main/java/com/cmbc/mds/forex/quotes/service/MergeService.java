package com.cmbc.mds.forex.quotes.service;

import com.cmbc.mds.forex.distribution.service.QuoteDistributionService;
import com.cmbc.mds.forex.engine.port.MarketDataQueueGateway;
import com.cmbc.mds.forex.provider.event.SourceDisconnectedEvent;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesLatchedCacheService;
import com.cmbc.mds.forex.quotes.dto.MergeMessage;
import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.constants.InterConstants;
import com.cmbc.mds.forex.common.utils.DateAndTimeUtils;
import com.cmbc.mds.forex.common.utils.JsonUtils;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesCacheService;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.quotes.dto.ProviderInfo;
import com.cmbc.mds.forex.subscription.core.model.topic.MergeDataTopic;
import com.cmbc.mds.forex.subscription.dto.MergeBaseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MergeService {

    private static final Logger log = LoggerFactory.getLogger(MergeService.class);

    @Autowired
    MergeQuotesCacheService mergeQuotesCacheService;

    @Autowired
    MergeQuotesLatchedCacheService mergeQuotesLatchedCacheService;

    @Autowired
    private QuoteDistributionService distributionService;

    @Autowired
    private MarketDataQueueGateway queueGateway;

    // 本地聚合上下文：MergeTopicKey -> PloyPrices。
    private final Map<String, PloyPrices> stateContext = new ConcurrentHashMap<>();

    /**
     * 处理聚合队列中的行情或控制事件。
     */
    public void handleMergeEvent(String mergeId, MergeMessage message) {
        if (message == null) {
            log.warn("[Merge] 忽略空聚合消息. mergeId={}", mergeId);
            return;
        }

        PloyPrices currentPrices = stateContext.computeIfAbsent(mergeId, k -> initPloyPrices(mergeId, message));

        // 断线事件必须进入对应聚合队列，保证状态变更串行化。
        if (message.isDisconnectEvent()) {
            String disconnectedSource = message.getDisconnectedSource();
            
            // 精准剔除 Bid 和 Ask 价格树中该 Source 下的所有报价
            this.removeSourcePricesByPrefix(currentPrices.getFdBid(), disconnectedSource);
            this.removeSourcePricesByPrefix(currentPrices.getFdAsk(), disconnectedSource);
            
            // 记录更新时间
            currentPrices.setUpdateTime(System.currentTimeMillis());
            
            // 生成最新的快照
            PloyPrices snapshot = deepCopy(currentPrices);
            
            // 更新公共缓存
            // mergeId 就是当前聚合通道的完整 TopicKey，直接复用可避免每笔断线重算 MergeDataTopic key。
            doCache(mergeId, snapshot);
            
            // 主动下发给下游
            // 分发侧同样使用当前通道 key，保证缓存 key 与分发 sourceKey 完全一致。
            distributionService.distribute(mergeId, snapshot);
            
            log.info("[Merge] 聚合通道 [{}] 已在单线程内完成断线价源 [{}] 的剔除并重新推送快照.", mergeId, disconnectedSource);
            return;
        }

        try {
            PloyPrices workingCopy = deepCopy(currentPrices);
            PloyPrices result = doMerge(workingCopy, message);
            if (result == null) {
                return;
            }
            replaceMergeState(currentPrices, result);
        } catch (Exception e) {
            log.error("[Merge] 聚合失败，保留旧状态且不缓存/分发. mergeId={}, provider={}, symbol={}",
                    mergeId, message.getProvider(), message.getSymbol(), e);
            return;
        }

        // 对外发布快照，避免暴露内部可变聚合状态。
        PloyPrices snapshot = deepCopy(currentPrices);
        // mergeId 来自队列通道，已经是完整聚合 TopicKey，避免重复 buildKey 带来的字符串和列表遍历开销。
        doCache(mergeId, snapshot);

        distributionService.distribute(mergeId, snapshot);
    }

    /**
     * 清理指定聚合通道的上下文和普通缓存。
     */
    public void removeStrategyState(String mergeId) {
        PloyPrices removed = stateContext.remove(mergeId);

        if (removed != null) {
            log.info("策略 [{}] 下线，已清理聚合上下文状态", mergeId);
            // 同时清理聚合行情缓存，不清理锁存聚合行情
            mergeQuotesCacheService.removePolyPrices(mergeId);
        }
    }

    /**
     * 将断线通知转成聚合队列事件，避免并发修改聚合状态。
     */
    @EventListener
    public void onSourceDisconnected(SourceDisconnectedEvent event) {
        handleSourceDisconnect(event.getSourceName());
    }

    /**
     * 为受影响的聚合通道投递断线清理消息。
     */
    public void handleSourceDisconnect(String source) {
        log.warn("[Merge] 收到看门狗断线通知，开始为受影响的策略分发断线消息. provider={}", source);
        int affectedCount = 0;
        // 遍历所有当前存在的聚合上下文
        for (Map.Entry<String, PloyPrices> entry : stateContext.entrySet()) {
            String mergeId = entry.getKey();
            PloyPrices currentPrices = entry.getValue();
            // 检查该策略订阅的源列表中是否包含此断线 Provider
            if (currentPrices.getSources() != null && currentPrices.getSources().contains(source)) {
                // 构造断线清理事件
                MergeMessage disconnectMsg = new MergeMessage();
                disconnectMsg.setDisconnectEvent(true);
                disconnectMsg.setDisconnectedSource(source);
                
                queueGateway.pushToMerge(mergeId, disconnectMsg);
                
                log.info("[Merge] 已向聚合通道 [{}] 的队列推送断线清理事件. provider={}", mergeId, source);
                affectedCount++;
            }
        }
        log.info("[Merge] 断线通知分发完毕. provider={}, 共推送断线消息策略数={}", source, affectedCount);
    }


    private PloyPrices initPloyPrices(String mergeId, MergeMessage message) {
        // 根据mergeId反解析 sources providers symbol
        MergeBaseInfo mergeBaseInfo = MergeDataTopic.parseKeyToReq(mergeId);

        PloyPrices p = new PloyPrices();
        p.setSymbol(mergeBaseInfo.getSymbol());
        p.setSources(mergeBaseInfo.getSources());
        p.setProviders(mergeBaseInfo.getProviders());

        // 每个 mergeId 由单 worker 串行处理，内部价格簿不需要 ConcurrentHashMap 的并发写开销。
        p.setFdBid(new HashMap<>());
        p.setFdAsk(new HashMap<>());
        p.setExtrams(new HashMap<>());
        p.setUpdateTime(System.currentTimeMillis());
        return p;
    }

    // 聚合业务逻辑具体实现
    public PloyPrices doMerge(PloyPrices currentPloyPrices, MergeMessage message) {
        Depth cleanedDepth = message.getData();
        if (cleanedDepth == null) {
            log.warn("[Merge] 忽略空 Depth 聚合消息. provider={}, symbol={}",
                    message.getProvider(), message.getSymbol());
            return null;
        }

        if (log.isDebugEnabled()) {
            log.debug("[Merge] 开始聚合计算. 待聚合行情Depth={}, 原聚合行情对象currentPloyPrices={}", JsonUtils.toJson(cleanedDepth),
                    JsonUtils.toJson(currentPloyPrices));
        }

        try {
            // 过滤掉QDM行情数据
            if (!this.isOdmTradeMode(cleanedDepth)) {
                log.warn("放弃QDM行情聚合, Depth = {}", JsonUtils.toJson(cleanedDepth));
                return null;
            }


            // 获取真正的 source 和 provider
            String source = cleanedDepth.getSource();
            String provider = cleanedDepth.getProvider();
            
            // 生成专用的 SourceProviderKey 替代原有 EXTRA_KEY_VALUE_PROVIDER
            // SourceProviderKey 和 timestamp 在本次行情内双边共用，提前计算一次后传入 bid/ask 聚合。
            String sourceProviderKey = buildSourceProviderKey(source, provider);
            String timestamp = resolveTimestamp(cleanedDepth);

            // 聚合前全局清理：无条件先清理该 Provider 在双边聚合簿中的所有旧数据
            if (log.isDebugEnabled()) {
                log.debug("聚合前全局清理旧数据, sourceProviderKey: {}", sourceProviderKey);
            }
            this.removeProviderPricesExact(currentPloyPrices.getFdBid(), sourceProviderKey);
            this.removeProviderPricesExact(currentPloyPrices.getFdAsk(), sourceProviderKey);

            // 聚合行情
            if (!cleanedDepth.getBidPrices().isEmpty() && !cleanedDepth.getBidQuantities().isEmpty()) {
                // 聚合bid价格
                this.bidPricePloy(currentPloyPrices, cleanedDepth, sourceProviderKey, timestamp);
            }
            if (!cleanedDepth.getAskPrices().isEmpty() && !cleanedDepth.getAskQuantities().isEmpty()) {
                // 聚合ask价格
                this.askPricePloy(currentPloyPrices, cleanedDepth, sourceProviderKey, timestamp);
            }

            // 更新时间戳
            currentPloyPrices.setUpdateTime(System.currentTimeMillis());

            // 记录聚合结果
            if (log.isDebugEnabled()) {
                log.debug("[Merge] 聚合计算完成. 当前最新 currentPloyPrices: {}", JsonUtils.toJson(currentPloyPrices));
            }

            return currentPloyPrices;
        } catch (Exception e) {
            throw new IllegalStateException("Merge calculation failed", e);
        }
    }

    /**
     * 执行缓存推送
     * 【核心修改】采用了“深拷贝快照”模式，确保推送到缓存中的对象是不可变的、线程安全的。
     */
    private void replaceMergeState(PloyPrices target, PloyPrices source) {
        target.setSymbol(source.getSymbol());
        target.setSources(source.getSources());
        target.setProviders(source.getProviders());
        target.setBidOptimalPrice(source.getBidOptimalPrice());
        target.setBidWorstPrice(source.getBidWorstPrice());
        target.setAskOptimalPrice(source.getAskOptimalPrice());
        target.setAskWorstPrice(source.getAskWorstPrice());
        target.setFdBid(source.getFdBid());
        target.setFdAsk(source.getFdAsk());
        target.setExtrams(source.getExtrams());
        target.setUpdateTime(source.getUpdateTime());
    }

    public void doCache(PloyPrices snapshot) {
        if (snapshot == null) {
            return;
        }

        // 2. 【推送快照】: 将新对象推送到公共缓存供外部查询 (Key 对应的 Value 引用替换)
        String key = MergeDataTopic.buildKey(snapshot.getSources(), snapshot.getProviders(), snapshot.getSymbol());
        doCache(key, snapshot);
    }

    private void doCache(String key, PloyPrices snapshot) {
        if (key == null || snapshot == null) {
            return;
        }
        // 该重载用于热路径复用已知 key，避免 doCache(PloyPrices) 再从 sources/providers/symbol 拼接一次。
        mergeQuotesCacheService.cachePolyPrices(key, snapshot);

        // 3. 【新增逻辑】: 同步更新锁存缓存
        mergeQuotesLatchedCacheService.updateLatchedPrices(key, snapshot);

        if (log.isDebugEnabled()) {
            log.debug("[Cache] 聚合结果已更新缓存(Snapshot). Key={}, PloyPrices={}",
                    key,
                    JsonUtils.toJson(snapshot));
        }
    }

    /**
     * 创建 PloyPrices 的深拷贝快照
     */
    private PloyPrices deepCopy(PloyPrices source) {
        if (source == null) {
            return null;
        }
        PloyPrices target = new PloyPrices();

        // 1. 复制基础不可变字段 (String, BigDecimal, Integer)
        target.setSymbol(source.getSymbol());
        target.setSources(source.getSources());
        target.setProviders(source.getProviders());

        target.setBidOptimalPrice(source.getBidOptimalPrice());
        target.setBidWorstPrice(source.getBidWorstPrice());
        target.setAskOptimalPrice(source.getAskOptimalPrice());
        target.setAskWorstPrice(source.getAskWorstPrice());
        target.setUpdateTime(source.getUpdateTime());

        target.setFdBid(copyDepthMap(source.getFdBid()));
        target.setFdAsk(copyDepthMap(source.getFdAsk()));

        target.setExtrams(copyStringMap(source.getExtrams()));

        return target;
    }

    // 辅助：复制 Map<BigDecimal, Map<String, ProviderInfo>>
    private Map<BigDecimal, Map<String, ProviderInfo>> copyDepthMap(Map<BigDecimal, Map<String, ProviderInfo>> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        Map<BigDecimal, Map<String, ProviderInfo>> target = new HashMap<>(source.size());

        for (Map.Entry<BigDecimal, Map<String, ProviderInfo>> entry : source.entrySet()) {
            Map<String, ProviderInfo> innerSource = entry.getValue();
            Map<String, ProviderInfo> innerTarget = innerSource == null || innerSource.isEmpty()
                    ? new HashMap<>()
                    : new HashMap<>(innerSource.size());

            if (innerSource != null && !innerSource.isEmpty()) {
                for (Map.Entry<String, ProviderInfo> innerEntry : innerSource.entrySet()) {
                    // ProviderInfo 也必须深拷贝，因为它是可变对象
                    innerTarget.put(innerEntry.getKey(), copyProviderInfo(innerEntry.getValue()));
                }
            }
            target.put(entry.getKey(), innerTarget);
        }
        return target;
    }

    // 辅助：复制 ProviderInfo
    private ProviderInfo copyProviderInfo(ProviderInfo sourceInfo) {
        if (sourceInfo == null) {
            return null;
        }
        ProviderInfo target = new ProviderInfo();
        target.setQuoteId(sourceInfo.getQuoteId());
        target.setSource(sourceInfo.getSource());
        target.setProvider(sourceInfo.getProvider());
        target.setSourceProviderKey(sourceInfo.getSourceProviderKey());
        target.setTransactionTime(sourceInfo.getTransactionTime());

        if (sourceInfo.getQuantity() != null) {
            target.setQuantity(sourceInfo.getQuantity().isEmpty()
                    ? Collections.emptyList()
                    : new ArrayList<>(sourceInfo.getQuantity()));
        }
        if (sourceInfo.getPriceAttributes() != null) {
            target.setPriceAttributes(copyStringMap(sourceInfo.getPriceAttributes()));
        }
        return target;
    }

    private Map<String, String> copyStringMap(Map<String, String> source) {
        if (source == null) {
            return null;
        }
        if (source.isEmpty()) {
            return Collections.emptyMap();
        }
        return new HashMap<>(source);
    }

    private boolean isOdmTradeMode(Depth cleanedDepth) {
        boolean flag = true;
        String tradeMode = cleanedDepth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_TRADE_MODE);
        if (tradeMode == null) {
            log.warn("[Merge] extraParams 中缺少 tradeMode 字段，跳过本次聚合。Provider={}, Symbol={}",
                    cleanedDepth.getProvider(), cleanedDepth.getSymbol());
            return false;
        }
        if (!tradeMode.equals(BaseConstants.TRADE_MODE_ODM)) {
            log.warn("非ODM行情, Depth = {}", JsonUtils.toJson(cleanedDepth));
            flag = false;
        }
        return flag;
    }

    private String buildSourceProviderKey(String source, String provider) {
        if (provider != null && !provider.trim().isEmpty() && !provider.equals(source)) {
            return source + "." + provider;
        }
        return source;
    }

    private String resolveTimestamp(Depth cleanedDepth) {
        String timestamp = cleanedDepth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_TIMESTAMP);
        if (!DateAndTimeUtils.isValidFormat(timestamp, DateAndTimeUtils.TIME_FORMATTER_MILLISECOND)) {
            // 清洗阶段通常已经补齐 timestamp；这里保留兜底，避免异常报文影响聚合。
            return DateAndTimeUtils.getFormatTime(DateAndTimeUtils.TIME_FORMATTER_MILLISECOND);
        }
        return timestamp;
    }

    private void bidPricePloy(PloyPrices currentPloyPrices,
            Depth cleanedDepth,
            String sourceProviderKey,
            String timestamp) {
        // 1. 获取 source 和 provider
        String source = cleanedDepth.getSource();
        String provider = cleanedDepth.getProvider();
        // 2. 提取属性列表 (ExtraParams)
        Map<String, String> attributeList = new HashMap<>();
        Map<String, String> extraParams = cleanedDepth.getExtraParams();
        if (extraParams != null) {
            for (Map.Entry<String, String> entry : extraParams.entrySet()) {
                String key = entry.getKey();
                // 筛选包含 "BID" 的 key - 使用常量
                if (key.contains(BaseConstants.KEY_BID_PREFIX)) {
                    attributeList.put(key, entry.getValue());
                }
            }
            // 指定 key: "1300" - 使用常量
            if (extraParams.containsKey(BaseConstants.KEY_1300)) {
                attributeList.put(BaseConstants.KEY_1300, extraParams.get(BaseConstants.KEY_1300));
            }
            // 指定 key: "bidOriginator" - 使用常量
            if (extraParams.containsKey(BaseConstants.KEY_BID_ORIGINATOR)) {
                attributeList.put(BaseConstants.KEY_BID_ORIGINATOR, extraParams.get(BaseConstants.KEY_BID_ORIGINATOR));
            }
        }

        // 4. 日志打印，原清理逻辑改为上层doMerge统一执行清理
        if (log.isDebugEnabled()) {
            log.debug("聚合前bid聚合行情数据fdBid--> {}", JsonUtils.toJson(currentPloyPrices.getFdBid()));
        }
        // 5. 执行聚合，将新的 Depth 数据插入到 self.fdBid 中
        this.ployPrices(currentPloyPrices.getFdBid(), cleanedDepth.getBidPrices(), cleanedDepth.getBidQuantities(),
                cleanedDepth, source, provider, sourceProviderKey, timestamp, attributeList);
        if (log.isDebugEnabled()) {
            log.debug("聚合后bid聚合行情数据fdBid--> {}", JsonUtils.toJson(currentPloyPrices.getFdBid()));
        }
    }

    private void askPricePloy(PloyPrices currentPloyPrices,
            Depth cleanedDepth,
            String sourceProviderKey,
            String timestamp) {
        // 1. 获取 source 和 provider
        String source = cleanedDepth.getSource();
        String provider = cleanedDepth.getProvider();
        // 2. 提取属性列表 (ExtraParams)
        Map<String, String> attributeList = new HashMap<>();
        Map<String, String> extraParams = cleanedDepth.getExtraParams();
        if (extraParams != null) {
            for (Map.Entry<String, String> entry : extraParams.entrySet()) {
                String key = entry.getKey();
                // 筛选包含 "ASK" 的 key - 使用常量
                if (key.contains(BaseConstants.KEY_ASK_PREFIX)) {
                    attributeList.put(key, entry.getValue());
                }
            }
            // 指定 key: "1300" - 使用常量
            if (extraParams.containsKey(BaseConstants.KEY_1300)) {
                attributeList.put(BaseConstants.KEY_1300, extraParams.get(BaseConstants.KEY_1300));
            }
            // 指定 key: "askOriginator" - 使用常量
            if (extraParams.containsKey(BaseConstants.KEY_ASK_ORIGINATOR)) {
                attributeList.put(BaseConstants.KEY_ASK_ORIGINATOR, extraParams.get(BaseConstants.KEY_ASK_ORIGINATOR));
            }
        }

        // 4. 日志打印，原清理逻辑改为上层doMerge统一执行清理
        if (log.isDebugEnabled()) {
            log.debug("聚合前ask聚合行情数据fdAsk--> {}", JsonUtils.toJson(currentPloyPrices.getFdAsk()));
        }
        // 5. 执行聚合，将新的 Depth 数据插入到 self.fdAsk 中
        this.ployPrices(currentPloyPrices.getFdAsk(), cleanedDepth.getAskPrices(), cleanedDepth.getAskQuantities(),
                cleanedDepth, source, provider, sourceProviderKey, timestamp, attributeList);
        if (log.isDebugEnabled()) {
            log.debug("聚合后ask聚合行情数据fdAsk--> {}", JsonUtils.toJson(currentPloyPrices.getFdAsk()));
        }
    }

    /**
     * 专用于断线清理（按 Source 前缀匹配）
     */
    private void removeSourcePricesByPrefix(Map<BigDecimal, Map<String, ProviderInfo>> ployPrice, String source) {
        if (ployPrice == null || source == null) {
            return;
        }

        Iterator<Map.Entry<BigDecimal, Map<String, ProviderInfo>>> iterator = ployPrice.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BigDecimal, Map<String, ProviderInfo>> entry = iterator.next();
            Map<String, ProviderInfo> providerMap = entry.getValue();

            // 前缀匹配，移除该 Source 下的所有 Provider 报价
            Iterator<Map.Entry<String, ProviderInfo>> innerIt = providerMap.entrySet().iterator();
            while (innerIt.hasNext()) {
                ProviderInfo info = innerIt.next().getValue();
                if (source.equals(info.getSource())) {
                    innerIt.remove();
                }
            }

            if (providerMap.isEmpty()) {
                iterator.remove();
            }
        }
    }

    /**
     * 专用于正常行情覆盖前的精准清理（按 SourceProviderKey 精确匹配）
     */
    private void removeProviderPricesExact(Map<BigDecimal, Map<String, ProviderInfo>> ployPrice, String providerKey) {
        if (ployPrice == null || providerKey == null) {
            return;
        }

        Iterator<Map.Entry<BigDecimal, Map<String, ProviderInfo>>> iterator = ployPrice.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BigDecimal, Map<String, ProviderInfo>> entry = iterator.next();
            Map<String, ProviderInfo> providerMap = entry.getValue();

            // 精确移除特定的 Provider 报价
            providerMap.remove(providerKey);

            if (providerMap.isEmpty()) {
                iterator.remove();
            }
        }
    }

    /**
     * 对应 Apama 中的 action ployPrice
     * 核心聚合逻辑：将新的价格/数量/属性合并到现有的聚合表中
     *
     * @param ployPrice    当前的聚合表 (对应参数 d)
     * @param prices       新行情的某些价格列表
     * @param quantities   新行情的数量列表
     * @param source 报价源
     * @param provider 提供方
     * @param timestamp    时间戳
     * @param extraParams  原始扩展参数
     */
    private void ployPrices(Map<BigDecimal, Map<String, ProviderInfo>> ployPrice,
            List<BigDecimal> prices,
            List<BigDecimal> quantities,
            Depth cleanedDepth,
            String source,
            String provider,
            String sourceProviderKey,
            String timestamp,
            Map<String, String> extraParams) {
        if (prices == null || quantities == null) {
            return;
        }

        // 对应 Apama 中的 integer m := 0; 用于访问列表索引
        // sourceProviderKey 由 doMerge 预先计算，避免 bid/ask 以及每次 ployPrices 调用重复创建临时 ProviderInfo。
        int m = 0;
        // 对应 Apama 中的 integer j := 1; 用于属性冲突时的后缀计数
        int j = 1;
        // 对应 Apama 中的 k (层级计数，用于提取属性)
        int k = 0;

        for (BigDecimal temp : prices) {
            // 获取当前档位的数量
            BigDecimal qty = (m < quantities.size()) ? quantities.get(m) : BigDecimal.ZERO;

            // 1. [过滤] 对应 if(temp=0.0 or quantities[m]=0)
            if (temp == null || temp.compareTo(BigDecimal.ZERO) == 0 || qty.compareTo(BigDecimal.ZERO) == 0) {
                m++;
                continue;
            }
            k = m + 1; // 层级加1

            // 2. [判断价格是否存在] 对应 if(d.hasKey(temp))
            // computeIfAbsent 可以简化 "不存在则创建" 的逻辑，但为了还原 Apama 的 if-else 逻辑结构，这里用 get
            Map<String, ProviderInfo> sd = ployPrice.get(temp);

            if (sd != null) {
                // --- 场景 A: 价格已存在 ---

                // [判断报价商是否存在] 对应 if(sd.hasKey(providerKey))
                if (sd.containsKey(sourceProviderKey)) {
                    // --- 场景 A1: 同一报价商，相同价格 -> 合并 (Append) ---
                    ProviderInfo p = sd.get(sourceProviderKey);

                    // 1. 追加数量: p.quantity.append(quantities[m])
                    if (p.getQuantity() == null) {
                        p.setQuantity(new ArrayList<>());
                    }
                    p.getQuantity().add(qty);
                    // 更新报价id - quoteId
                    p.setQuoteId(cleanedDepth.getQuoteId());
                    // 2. 合并属性 (处理冲突)
                    // 对应 dtmap := self.setPriceAttributes(...)
                    Map<String, String> dtmp = setPriceAttributes(String.valueOf(k), extraParams);

                    // 对应 for ktmp in dtmp.keys() ... 冲突处理逻辑
                    mergeAttributesWithCollisionHandling(p, dtmp, j);

                    // 计数器增加
                    j++;

                } else {
                    // --- 场景 A2: 不同报价商，相同价格 -> 新增记录 ---
                    // 由于在聚合前进行过价格清理，实际应该只会走这里，不存在同价源聚合。
                    ProviderInfo p = createNewProviderInfo(cleanedDepth.getQuoteId(), source, provider, qty, timestamp);
                    // 设置属性
                    p.setPriceAttributes(setPriceAttributes(String.valueOf(k), extraParams));

                    sd.put(sourceProviderKey, p);
                    // Apama: d.add(temp, sd) -> Java map引用传递，无需再次put，但为了严谨可保留
                }
            } else {
                // --- 场景 B: 价格不存在 -> 新增价格档位 ---
                sd = new HashMap<>();

                ProviderInfo p = createNewProviderInfo(cleanedDepth.getQuoteId(), source, provider, qty, timestamp);
                // 设置属性
                p.setPriceAttributes(setPriceAttributes(String.valueOf(k), extraParams));

                sd.put(sourceProviderKey, p);
                ployPrice.put(temp, sd);
            }

            // 索引递增
            m++;
        }
    }

    /**
     * 辅助方法：创建新的 ProviderInfo 对象
     */
    private ProviderInfo createNewProviderInfo(String quoteId, String source, String provider, BigDecimal quantity, String timestamp) {
        ProviderInfo p = new ProviderInfo();
        p.setQuoteId(quoteId);
        p.setSource(source);
        p.setProvider(provider);
        String sourceProviderKey = p.buildSourceProviderKey();
        p.setSourceProviderKey(sourceProviderKey);

        List<BigDecimal> qtyList = new ArrayList<>(1);
        qtyList.add(quantity);
        p.setQuantity(qtyList);
        p.setTransactionTime(timestamp);
        return p;
    }

    /**
     * 对应 Apama 中的 action setPriceAttributes
     * 逻辑：遍历 extraParams，如果 key 包含 m 且包含下划线 "_", 则截取下划线之后的部分作为新 key；
     * 否则保留原 key。
     *
     * @param m           层级标识 (例如 "1", "2")
     * @param extraParams 原始扩展参数
     * @return 处理后的属性 Map
     */
    private Map<String, String> setPriceAttributes(String m, Map<String, String> extraParams) {
        Map<String, String> returnDic = new HashMap<>();
        if (extraParams == null || extraParams.isEmpty()) {
            return returnDic;
        }
        for (Map.Entry<String, String> entry : extraParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains(m) && key.contains("_")) {
                // 截取第一个下划线之后的所有字符作为新Key
                int underscoreIndex = key.indexOf("_");
                // 确保下划线不是最后一个字符，避免越界
                if (underscoreIndex < key.length() - 1) {
                    String newKey = key.substring(underscoreIndex + 1);
                    returnDic.put(newKey, value);
                } else {
                    // 如果下划线在最后，按照逻辑可能存为空串，或者保留原值，这里保留原值较安全
                    returnDic.put(key, value);
                }
            } else {
                returnDic.put(key, value);
            }
        }
        return returnDic;
    }

    /**
     * 辅助方法：处理属性合并时的 Key 冲突
     */
    private void mergeAttributesWithCollisionHandling(ProviderInfo p, Map<String, String> newAttributes, int j) {
        if (p.getPriceAttributes() == null) {
            p.setPriceAttributes(new HashMap<>());
        }
        Map<String, String> existingAttrs = p.getPriceAttributes();
        int i = j - 1; // Apama: integer i := j-1;

        for (Map.Entry<String, String> entry : newAttributes.entrySet()) {
            String ktmp = entry.getKey();
            String value = entry.getValue();

            // Apama: if(p.hasKey(ktmp) or p.hasKey(ktmp + i.toString()))
            // 注意：Apama 的 toString() 通常没有下划线，这里直接拼接
            if (existingAttrs.containsKey(ktmp) || existingAttrs.containsKey(ktmp + i)) {
                // 冲突：添加后缀
                // Apama: p.add(ktmp + j.toString(), value)
                existingAttrs.put(ktmp + j, value);
            } else {
                // 无冲突
                existingAttrs.put(ktmp, value);
            }
        }
    }
}
