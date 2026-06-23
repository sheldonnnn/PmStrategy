package com.cmbc.mds.forex.quotes.service;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.constants.InterConstants;
import com.cmbc.mds.forex.common.utils.DateAndTimeUtils;
import com.cmbc.mds.forex.common.utils.JsonUtils;
import com.cmbc.mds.forex.distribution.service.QuoteDistributionService;
import com.cmbc.mds.forex.engine.port.MarketDataQueueGateway;
import com.cmbc.mds.forex.provider.dto.OmsSourceReq;
import com.cmbc.mds.forex.provider.service.ForeignBankConnectionService;
import com.cmbc.mds.forex.provider.service.SourceConfigService;
import com.cmbc.mds.forex.quotes.cacheservice.CleanQuotesCacheService;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.MergeMessage;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.cmbc.mds.forex.common.utils.ServiceNameUtils.getPrefixServiceName;

@Service
public class CleanService {

    private static final Logger log = LoggerFactory.getLogger(CleanService.class);

    @Autowired
    private SubscriptionCoreService subscriptionCoreService;

    @Autowired
    private MarketDataQueueGateway queueGateway;

    @Autowired
    private QuoteDistributionService distributionService;

    @Autowired
    CleanQuotesCacheService cleanQuotesCacheService;
    @Autowired
    ForeignBankConnectionService foreignBankConnectionService;
    @Autowired
    SourceConfigService sourceConfigService;

    @Value("${CleanConfig.ProviderExporedTime:10000}")
    private long providerExporedTime;

    /**
     * 清洗行情，并路由到聚合队列和外部分发通道。
     */
    public void doCleanandRoute(String cleanId, Depth depth) {
        if (depth == null) {
            log.warn("[CleanService] 接收到的 Depth 对象为 null，无法处理。CleanId: {}", cleanId);
            return;
        }

        long startTime = System.currentTimeMillis();
        // 清洗
        Depth cleanedDepth = this.depthClean(depth);
        if (cleanedDepth == null) {
            // 清洗失败或被过滤，已经在 depthClean 中记录了日志
            return;
        }
        // 缓存
        String topicKey = MarketDataTopic.buildTopicKey(cleanedDepth.getSource(), cleanedDepth.getProvider(), cleanedDepth.getSymbol());
        this.doCache(topicKey, cleanedDepth);

        // 内部流转：将清洗结果写入对应策略的聚合队列。
        for (String distributeId : subscriptionCoreService.getDistributeIdsByType(
                topicKey, SubscriberContext.SubscriberType.MD_CLEAN_STRATEGY)) {
            MergeMessage msg = new MergeMessage(cleanedDepth.getProvider(), cleanedDepth.getSymbol(), cleanedDepth);
            queueGateway.pushToMerge(distributeId, msg);
        }

        // 外部旁路：仅面向通信通道做分发。
        distributionService.distribute(topicKey, cleanedDepth);

        if (log.isDebugEnabled()) {
            log.debug("[CleanService] 完整处理耗时: {}ms, Symbol: {}", (System.currentTimeMillis() - startTime), cleanedDepth.getSymbol());
        }
    }

    /**
     * 缓存清洗后的最新行情。
     */
    public void doCache(Depth cleanedDepth) {
        String cleanedDataCachekey = MarketDataTopic.buildTopicKey(cleanedDepth.getSource(), cleanedDepth.getProvider(), cleanedDepth.getSymbol());
        doCache(cleanedDataCachekey, cleanedDepth);
    }

    private void doCache(String cleanedDataCachekey, Depth cleanedDepth) {
        cleanQuotesCacheService.cacheDepth(cleanedDataCachekey, cleanedDepth);
    }

    /**
     * 执行行情清洗和业务过滤。
     */
    public Depth depthClean(Depth depth) {
        if (log.isDebugEnabled()) {
            log.debug("开始清理行情数据, depth: {}", JsonUtils.toJson(depth));
        }

        // 1. 判断是否需要清洗（是否有订阅）
        // [修改] 使用通用订阅服务判断
        // 修复：由于策略订阅通常是 MERGE 级别，而交易员是 CLEAN 级别，
        // 这里必须检查是否存在任意一种层级的订阅，否则会导致策略启动后因 CleanService 拦截数据而接收不到行情。
        String cleanTopicKey = MarketDataTopic.buildTopicKey(depth.getSource(), depth.getProvider(), depth.getSymbol());
        boolean hasCleanSubscriber = subscriptionCoreService.hasSubscribers(cleanTopicKey);

        if (!hasCleanSubscriber) {
            // 降低日志级别，避免未订阅的行情刷屏
            if (log.isDebugEnabled()) {
                log.debug("行情数据被忽略(无 CLEAN 或 MERGE 订阅者). TopicKey: {}", cleanTopicKey);
            }
            return null;
        }
        // 2. 开始进行清洗
        if (!depthValid(depth)) {
            // 具体的失败原因已在 depthValid 内部打印
            return null;
        }
        // 3. 返回清洗后的数据
        Depth cleanedDepth = buildNewDepth(depth);
        if (cleanedDepth == null) {
            log.warn("构建新Depth对象失败(可能因拒单配置或账户过滤), Provider: {}, Symbol: {}", depth.getProvider(), depth.getSymbol());
            return null;
        }
        // 4. 返回清洗后的数据
        if (log.isDebugEnabled()) {
            log.debug("行情数据清理完成, cleanedDepth = {}", JsonUtils.toJson(cleanedDepth));
        }
        return cleanedDepth;
    }

    // 行情校验
    private boolean depthValid(Depth depth) {
        // LC综合管理订阅行情过滤（apama的）
        if (depth.getExtraParams().containsKey(BaseConstants.LC_SUBSCRIBEID)) {
            String subscribId = depth.getExtraParams().get(BaseConstants.LC_SUBSCRIBEID);
            if (subscribId != null && subscribId.contains(BaseConstants.FX)) {
                log.warn("[行情校验失败] 命中LC订阅ID过滤: {}", subscribId);
                return false;
            }
        }
        // 过滤询价行情（apama的）
        if (depth.getExtraParams().containsKey(BaseConstants.QUOTEID)) {
            String quoteId = depth.getExtraParams().get(BaseConstants.QUOTEID);
            if (quoteId != null && quoteId.contains(BaseConstants.FXRFQ)) {
                log.warn("[行情校验失败] 命中询价行情过滤(FXRFQ): {}", quoteId);
                return false;
            }
        }

        String source = depth.getSource();
        // todo 判断是否是内部报价，目前缺失两个常量:constants.service_name_internal, constants.service_name_external

        // 处理非DIMPLE的行情
        if (!BaseConstants.SERVICE_NAME_DIMPLE.equals(source)) {
            // 判断行情是否过期
            boolean isExpired = isDepthExpired(providerExporedTime,depth.getCreateTime());
            if (isExpired) {
                log.warn("[行情校验失败] 行情数据过期. CreateTime: {}, Limit: {}ms", depth.getCreateTime(), providerExporedTime);
                return false;
            }
            boolean isBankConnected = foreignBankConnectionService.isBankConnected(source);

            if(!isBankConnected) {
                log.warn("[行情校验失败] 外资行连接中断. source: {}", source);
                return false;
            }
        }
        // 检查是否是错误行情，错误行情直接丢弃
        if(depth.getExtraParams().containsKey(BaseConstants.DEPTH_EXTRAKEY_ERROR)) {
            log.warn("[行情校验失败] 包含错误标记: {}", BaseConstants.DEPTH_EXTRAKEY_ERROR);
            return false;
        }
        // 行情数据校验
        boolean isDepthDateValid = depthDataValid(depth);
        if (!isDepthDateValid) {
            // 具体原因在 depthDataValid 中记录
            log.warn("[行情校验失败] 价格/数量 数据格式校验不通过. Provider: {}, Symbol: {}", depth.getProvider(), depth.getSymbol());
            return false;
        }

        return true;
    }

    // 从Depth的扩展字段中获取报价商名称
    private String getServiceNameFromExtraParam(Map<String,String> extraParam) {
        String serviceName;
        if (extraParam.containsKey(BaseConstants.SERVICE_NAME_KEY1)) {
            serviceName = extraParam.get(BaseConstants.SERVICE_NAME_KEY1);
        }
        else if (extraParam.containsKey(BaseConstants.SERVICE_NAME_KEY2)) {
            serviceName = extraParam.get(BaseConstants.SERVICE_NAME_KEY2);
        }
        else if (extraParam.containsKey(BaseConstants.SERVICE_NAME_KEY3)) {
            serviceName = extraParam.get(BaseConstants.SERVICE_NAME_KEY3);
        }
        else {
            serviceName = BaseConstants.SERVICE_NAME_DIMPLE;
        }

        return serviceName;
    }

    private boolean isDepthExpired(long expireTime, long depthTime) {
        // 默认不过期
        boolean isValid = false;
        if (expireTime > 0) { // 过期时间参数为正才进行过期时间判断，否则不进行过期时间判断，直接返回true
            // 获取当前时间
            long currentTime = System.currentTimeMillis();
            // 计算时间差
            long timeSpread = currentTime - depthTime;
            // 判断是否过期 (对应: if(timeSpread>expireTime))
            if (timeSpread > expireTime) {
                isValid = true;
            }
        }
        return isValid;
    }

    private boolean depthDataValid(Depth depth) {
        // ask 数据校验
        if (depth.getAskPrices() == null || depth.getAskQuantities() == null ||
                depth.getAskPrices().size() != depth.getAskQuantities().size()) {
            log.warn("数据校验失败: Ask价格与数量列表长度不一致或为空");
            return false;
        }
        // bid 数据校验
        if (depth.getBidPrices() == null || depth.getBidQuantities() == null ||
                depth.getBidPrices().size() != depth.getBidQuantities().size()) {
            log.warn("数据校验失败: Bid价格与数量列表长度不一致或为空");
            return false;
        }
        // bid价格非正校验
        if (!depth.getBidPrices().isEmpty()) {
            boolean hasNonPositive = hasNonPositive(depth.getBidPrices());
            if (hasNonPositive) {
                log.warn("数据校验失败: 存在 Bid 价格 <= 0");
                return false;
            }
        }
        // ask价格非正校验
        if (!depth.getAskPrices().isEmpty()) {
            boolean hasNonPositive = hasNonPositive(depth.getAskPrices());
            if (hasNonPositive) {
                log.warn("数据校验失败: 存在 Ask 价格 <= 0");
                return false;
            }
        }
        // ask数量非正校验
        if (!depth.getAskQuantities().isEmpty()) {
            boolean hasNonPositive = hasNonPositive(depth.getAskQuantities());
            if (hasNonPositive) {
                log.warn("数据校验失败: 存在 Ask 数量 <= 0");
                return false;
            }
        }
        // bid数量非正校验
        if (!depth.getBidQuantities().isEmpty()) {
            boolean hasNonPositive = hasNonPositive(depth.getBidQuantities());
            if (hasNonPositive) {
                log.warn("数据校验失败: 存在 Bid 数量 <= 0");
                return false;
            }
        }
        return true;
    }

    private boolean hasNonPositive(List<BigDecimal> values) {
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) <= 0) {
                return true;
            }
        }
        return false;
    }

    private Depth buildNewDepth(Depth depth) {
        // 正常报价行情，走清洗逻辑
        Depth newDepth = depth;

        // 1. 复用原始 Depth，仅确保扩展参数可写
        if (newDepth.getExtraParams() == null) {
            newDepth.setExtraParams(new java.util.HashMap<>());
        }

        // 2. 数据源参数处理
        String prefixName = getPrefixServiceName(depth.getSource()); // 获取行情提供者前缀名称 FXALL UBS
        String depthAccount = prefixName; // todo 这个account 信息从何而来？目前并没有这个属性？
        String isOneMaker = "1"; // 数据源是但数据源还是多数据源 0-多数据源 1-单数据源
        String alisName = depth.getProvider(); // 行情提供者名称 - 目前看报文中无此字段
        if (sourceConfigService.isValidSource(prefixName)) {
            OmsSourceReq providerParam = sourceConfigService.getSourceParam(prefixName);
            isOneMaker = providerParam.getExtraParams().getOrDefault(InterConstants.EXTRA_KEY_VALUE_ISONEMAKER,"");
            alisName = providerParam.getExtraParams().getOrDefault(InterConstants.EXTRA_KEY_VALUE_ALIAS,"");
        }
        if (isOneMaker.equals("0")) {
            // 多数据源,目前看来只有fxall会有多数据源情况
            List<String> originator;
            if (depth.getExtraParams().containsKey(InterConstants.EXTRA_KEY_VALUE_ASK_ORIGINATOR)) {
                originator = JsonUtils.toList(depth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_ASK_ORIGINATOR));
            }
            else if (depth.getExtraParams().containsKey(InterConstants.EXTRA_KEY_VALUE_BID_ORIGINATOR)) {
                originator = JsonUtils.toList(depth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_BID_ORIGINATOR));
            }
            else {
                originator = null;
            }

            String providerName = depth.getProvider();
            if (originator != null && !originator.isEmpty()) {
                depthAccount = originator.get(0);
                providerName = prefixName + "-" + originator.get(0);
            }
            newDepth.getExtraParams().put(InterConstants.EXTRA_KEY_VALUE_PROVIDER, providerName);
        } else {
            // 单数据源
            newDepth.getExtraParams().put(InterConstants.EXTRA_KEY_VALUE_PROVIDER, alisName);
        }

        if (newDepth.getExtraParams().containsKey(InterConstants.EXTRA_KEY_VALUE_PROVIDER)) {
            String providerNewName = newDepth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_PROVIDER);
            if (sourceConfigService.isSourceRejected(providerNewName)) {
                log.warn("行情构建中止: 数据源 [{}] 在拒单列表中", providerNewName);
                return null;
            }
        }

        // 3. 是否参与聚合处理
        boolean isDeployDepth = false;
        List<String> dataSourceAccounts = sourceConfigService.getAllSourceKeys();
        if (!depthAccount.isEmpty() && !dataSourceAccounts.isEmpty()) {
            for (String dataSourceKey:dataSourceAccounts) {
                String dataSourceKeyPrefixName = getPrefixServiceName(dataSourceKey);
                if (!dataSourceKeyPrefixName.equals(prefixName)) {
                    continue;
                }
                // 交易账户判断
                List<String> accounts = sourceConfigService.getSourceAccounts(dataSourceKey);
                if (accounts != null && !accounts.isEmpty()) {
                    if (accounts.contains(depthAccount)) {
                        isDeployDepth = true;
                        break;
                    }
                } else {
                    if (dataSourceKey.equals(depthAccount)) {
                        isDeployDepth = true;
                        break;
                    }
                }
            }
            // 如果非空且未获取到账户，则忽略本次行情
            if (!isDeployDepth) {
                log.warn("行情构建中止: 账户过滤未通过. Account: {}", depthAccount);
                return null;
            }
        }
        // 4. 交易模式设置：tradeMode
        String tradeInstrument;
        if (depth.getExtraParams().containsKey(InterConstants.EXTRA_KEY_VALUE_TRADE_MODE)) {
            tradeInstrument = depth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_TRADE_MODE); // 目前看报文都没有这个字段
        } else {
            tradeInstrument = BaseConstants.TRADE_MODE_ODM; // 1-ODM 0-QDM, 特别地：DIMP行情全部为ODM
        }
        newDepth.getExtraParams().put(InterConstants.EXTRA_KEY_VALUE_TRADE_MODE, tradeInstrument);

        // 5. 行情时间
        String timeStamp = "";
        if (depth.getExtraParams().containsKey(InterConstants.EXTRA_KEY_VALUE_TIME)) {
            timeStamp = depth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_TIME);
        }
        else if (depth.getExtraParams().containsKey(InterConstants.EXTRA_KEY_VALUE_TIMESTAMP)) {
            timeStamp = depth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_TIMESTAMP);
        } else {
            // DIMP时间处理，dimp日期和时间是分开的，需要拼接，并转换
            String tradeTime = "";
            if (depth.getExtraParams().containsKey(InterConstants.EXTRA_KEY_VALUE_TRADE_DATE)) {
                tradeTime = depth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_TRADE_DATE);
            }
            if (depth.getExtraParams().containsKey(InterConstants.EXTRA_KEY_VALUE_GEN_TIME)) {
                tradeTime = tradeTime+" "+depth.getExtraParams().get(InterConstants.EXTRA_KEY_VALUE_GEN_TIME);
            }
            if (!tradeTime.isEmpty()) {
                if (DateAndTimeUtils.isValidFormat(tradeTime,DateAndTimeUtils.TIME_FORMATTER_MILLISECOND)) {
                    timeStamp = tradeTime;
                } else if (DateAndTimeUtils.isValidFormat(tradeTime,DateAndTimeUtils.TIME_FORMATTER_SECOND)) {
                    timeStamp = DateAndTimeUtils.convert(tradeTime, DateAndTimeUtils.TIME_FORMATTER_SECOND, DateAndTimeUtils.TIME_FORMATTER_MILLISECOND);
                    if (timeStamp == null) {
                        timeStamp = DateAndTimeUtils.getFormatTime(DateAndTimeUtils.TIME_FORMATTER_MILLISECOND);
                    }
                } else {
                    timeStamp = DateAndTimeUtils.getFormatTime(DateAndTimeUtils.TIME_FORMATTER_MILLISECOND);
                }
            }
        }
        if (timeStamp.isEmpty()) {
            timeStamp = DateAndTimeUtils.getFormatTime(DateAndTimeUtils.TIME_FORMATTER_MILLISECOND);
        }
        newDepth.getExtraParams().put(InterConstants.EXTRA_KEY_VALUE_TIMESTAMP, timeStamp);

        return newDepth;
    }
}
