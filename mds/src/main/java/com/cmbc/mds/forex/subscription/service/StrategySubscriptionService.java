package com.cmbc.mds.forex.subscription.service;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.engine.port.MarketDataQueueGateway;
import com.cmbc.mds.forex.subscription.core.model.topic.MergeDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;
import com.cmbc.mds.forex.subscription.dto.StrategySubReq;
import com.cmbc.mds.forex.quotes.cacheservice.CleanQuotesCacheService;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.MergeMessage;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 策略订阅业务服务
 *
 * <p>注意：symbol 格式以调用方传入为准，不做任何转换。
 * 行情侧的标准化统一由 BaseMQTranserAdapter.convertToDepth() 负责，
 * 调用方需保证订阅时传入的 symbol 与行情侧一致（外资行使用 USD/CNY 格式）。
 */
@Service
public class StrategySubscriptionService {

    @Autowired
    private SubscriptionCoreService subscriptionCoreService;
    @Autowired
    private MarketDataQueueGateway queueGateway;
    @Autowired
    private CleanQuotesCacheService cleanQuotesCacheService;

    /**
     * 添加策略订阅
     *
     * @param sources    行情源列表 (例如: ["GS", "FXALL"])
     * @param providers  交易对手列表 (例如: ["GS", "UBS"])
     * @param symbol     货币对，以调用方传入为准 (外资行使用斜杠格式, 例如: "USD/CNY")
     * @param traderId   交易员ID
     * @param strategyId 策略ID
     */
    public void addStrategySubscription(List<String> sources, List<String> providers, String symbol, String traderId,
            String strategyId) {
        // 1. 构建源头主题 (MERGE Data - 聚合行情)
        MergeDataTopic mergeDataTopic = new MergeDataTopic(sources, providers, symbol);
        SubscriberContext mergeDataCtx = new SubscriberContext(strategyId, SubscriberContext.SubscriberType.MD_MERGE,
                null);

        // 2. 构建分发主题
        // 这里的订阅表示通过内部调用的形式，将 从聚合后行情 分发到 策略
        DistributionDataTopic distTopic = new DistributionDataTopic(mergeDataTopic,
                DistributionDataTopic.Protocol.INTERNAL, strategyId);
        SubscriberContext strategyCtx = new SubscriberContext(strategyId,
                SubscriberContext.SubscriberType.DIST_STRATEGY, strategyId);

        // 3. [编排] 确保MarketData订阅, 确保MergeData订阅
        for (int i = 0; i < sources.size(); i++) {
            String currentSource = sources.get(i); // e.g., "FXALL"
            String currentProvider = providers.get(i); // e.g., "UBS"
            // 构建底层清洗主题 (MarketDataTopic)
            MarketDataTopic currentMarketDataTopic = new MarketDataTopic(currentSource, currentProvider, symbol);
            SubscriberContext currentMarketDataCtx = new SubscriberContext(strategyId,
                    SubscriberContext.SubscriberType.MD_CLEAN_STRATEGY, mergeDataTopic.getTopicKey());
            // 功能1：订阅触发 TopicActiveEvent(MarketDataTopic) -> MarketDataResourceListener 开启对应通道，初始化待清洗行情queue和thread
            // 功能2：维护MarketDataTopic 与 策略id 的订阅关系，用于清洗后行情提交到对应的待聚合行情队列
            subscriptionCoreService.subscribe(currentMarketDataTopic, currentMarketDataCtx);
        }

        // MergeData 订阅
        // 订阅触发 TopicActiveEvent(MergeDataTopic) -> MarketDataResourceListener 开启对应通道，初始化待聚合行情queue和thread
        subscriptionCoreService.subscribe(mergeDataTopic, mergeDataCtx);
        // 4. [编排] 建立分发通道
        subscriptionCoreService.subscribe(distTopic, strategyCtx); // 这里的分发主题用于从聚合后行情 到 策略的分发

        // 策略启动时，用当前清洗缓存预热对应聚合队列。
        if (sources != null && providers != null && sources.size() == providers.size()) {
            for (int i = 0; i < sources.size(); i++) {
                String currentSource = sources.get(i);
                String currentProvider = providers.get(i);

                Depth cachedDepth = cleanQuotesCacheService.getDepth(currentSource, currentProvider, symbol);
                if (cachedDepth != null) {
                    MergeMessage mergeMsg = new MergeMessage(
                            currentProvider,
                            symbol,
                            cachedDepth
                    );
                    queueGateway.pushToMerge(mergeDataTopic.getTopicKey(), mergeMsg);
                }
            }
        }
    }

    public void addBatchStrategySubscription(List<StrategySubReq> batchStrategySubReqs) {
        if (batchStrategySubReqs != null && !batchStrategySubReqs.isEmpty()) {
            for (StrategySubReq strategySubReq : batchStrategySubReqs) {
                addStrategySubscription(
                        strategySubReq.getSources(),
                        strategySubReq.getProviders(),
                        strategySubReq.getSymbol(),
                        strategySubReq.getTraderId(),
                        strategySubReq.getStrategyId());
            }
        }
    }

    /**
     * [重构] 移除策略订阅 (指定参数模式)
     * 需要传入与订阅时完全一致的 sources 列表，才能清理底层的引用
     *
     * @param symbol 货币对，以调用方传入为准，需与订阅时保持一致
     */
    public void removeStrategySubscription(List<String> sources, List<String> providers, String symbol,
            String strategyId) {
        // 1. 构建 MergeDataTopic 及上下文
        MergeDataTopic mergeTopic = new MergeDataTopic(sources, providers, symbol);
        SubscriberContext strategyMergeCtx = new SubscriberContext(strategyId,
                SubscriberContext.SubscriberType.MD_MERGE, null);

        // 2. 构建 DistributionDataTopic 及上下文
        DistributionDataTopic distTopic = new DistributionDataTopic(mergeTopic, DistributionDataTopic.Protocol.INTERNAL,
                strategyId);
        SubscriberContext strategyDistCtx = new SubscriberContext(strategyId,
                SubscriberContext.SubscriberType.DIST_STRATEGY, strategyId);

        // 3. 执行退订
        // 3.1 停止分发
        subscriptionCoreService.unsubscribe(distTopic, strategyDistCtx);

        // 3.2 停止聚合计算订阅
        subscriptionCoreService.unsubscribe(mergeTopic, strategyMergeCtx);

        // 3.3 [关键] 循环停止所有底层源的订阅
        if (sources != null && providers != null && sources.size() == providers.size()) {
            for (int i = 0; i < sources.size(); i++) {
                // 构建MD_CLEAN_STRATEGY 订阅主题，带上 distributeId 进行退订匹配
                MarketDataTopic currentMarketDataTopic = new MarketDataTopic(sources.get(i), providers.get(i), symbol);
                SubscriberContext currentMdCtx = new SubscriberContext(strategyId,
                        SubscriberContext.SubscriberType.MD_CLEAN_STRATEGY, mergeTopic.getTopicKey());

                // 移除MD_CLEAN订阅
                subscriptionCoreService.unsubscribe(currentMarketDataTopic, currentMdCtx);
            }
        }
    }

    /**
     * [新增] 按 StrategyID 智能移除所有相关订阅
     * 自动清理该策略 ID 关联的 Market、Merge 和 Distribution 层
     */
    public void removeAllSubscriptionsByStrategyId(String strategyId) {
        Set<String> allTopics = subscriptionCoreService.getSubscribedTopics(strategyId);

        if (allTopics == null || allTopics.isEmpty()) {
            return;
        }

        for (String topicKey : allTopics) {
            List<SubscriberContext> contexts = subscriptionCoreService
                    .getSubscriberContextsByTopickeyandSubscribeid(topicKey, strategyId);
            for (SubscriberContext currentCtx : contexts) {
                subscriptionCoreService.unsubscribe(topicKey, currentCtx);
            }
        }
    }

    /**
     * [新增] 查询策略订阅列表
     */
    public List<SubscriptionTopic> getStrategySubscriptions(String strategyId) {
        Set<String> topicKeys = subscriptionCoreService.getSubscribedTopics(strategyId);
        List<SubscriptionTopic> result = new ArrayList<>();
        // 使用 TreeSet 自动排序 topicKeys
        Set<String> sortedTopicKeys = new TreeSet<>(topicKeys);

        for (String topicKey : sortedTopicKeys) {
            result.add(subscriptionCoreService.getTopicInstance(topicKey));
        }
        return result;
    }
}
