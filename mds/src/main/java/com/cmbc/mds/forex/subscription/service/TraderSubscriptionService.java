package com.cmbc.mds.forex.subscription.service;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.cmbc.mds.forex.subscription.dto.TraderSubReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 交易员订阅业务服务
 * 职责：作为业务适配层，将交易员特定的订阅请求转换为通用订阅模型，并转发给核心订阅服务。
 *
 * <p>注意：symbol 格式以调用方传入为准，不做任何转换。
 * 行情侧的标准化统一由 BaseMQTranserAdapter.convertToDepth() 负责，
 * 调用方需保证订阅时传入的 symbol 与行情侧一致（外资行使用 USD/CNY 格式）。
 */
@Service
public class TraderSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TraderSubscriptionService.class);

    @Autowired
    private SubscriptionCoreService subscriptionCoreService;

    /**
     * 添加交易员订阅
     *
     * @param source   行情源 (例如: "GS", "FXALL")
     * @param provider 交易对手 (例如: "GS", "UBS")
     * @param symbol   货币对，以调用方传入为准 (外资行使用斜杠格式, 例如: "USD/CNY")
     * @param traderId 交易员ID
     */
    public void addTraderSubscription(String source, String provider, String symbol, String traderId) {
        try {
            // 1. 构建源头主题 (Clean Data)
            MarketDataTopic marketDataTopic = new MarketDataTopic(source, provider, symbol);
            SubscriberContext marketDataCtx = new SubscriberContext(traderId, SubscriberContext.SubscriberType.MD_CLEAN_TREADER,
                    null); // Clean通道开启，无需分发id

            // 2. 构建分发主题 (WebSocket to User)
            // 假设交易员ID直接作为 WebSocket 的 TargetId (或者 SessionId)
            // [修复确认] 使用 traderId 确保隔离，Key 格式: DIST:WEBSOCKET:{traderId}:MD:CLEAN:{source}.{provider}:{symbol}
            DistributionDataTopic distTopic = new DistributionDataTopic(marketDataTopic,
                    DistributionDataTopic.Protocol.WEBSOCKET, traderId);
            SubscriberContext userCtx = new SubscriberContext(traderId, SubscriberContext.SubscriberType.DIST_WEBSOCKET,
                    traderId);

            // 4. [编排] 确保源头开启
            // 使用系统身份订阅源Topic。只要有任何一个业务方需要这个源，它就会保持 Active。
            subscriptionCoreService.subscribe(marketDataTopic, marketDataCtx);

            // 5. [编排] 建立分发通道
            subscriptionCoreService.subscribe(distTopic, userCtx); // CleanedDepth -> WebSocket通道的分发
        } catch (Exception e) {
            log.error("[TraderSubscription] 添加订阅失败: source={}, provider={}, symbol={}, traderId={}",
                    source, provider, symbol, traderId, e);
            throw e;
        }
    }

    public void addBatchTraderSubscription(List<TraderSubReq> batchTraderSubReq) {
        if (batchTraderSubReq != null && !batchTraderSubReq.isEmpty()) {
            for (TraderSubReq traderSubReq : batchTraderSubReq) {
                addTraderSubscription(
                        traderSubReq.getSource(),
                        traderSubReq.getProvider(),
                        traderSubReq.getSymbol(),
                        traderSubReq.getTraderId());
            }
        }
    }

    /**
     * [重构] 移除交易员订阅
     * 同步移除分发订阅和源头订阅
     *
     * @param symbol 货币对，以调用方传入为准，需与订阅时保持一致
     */
    public void removeTraderSubscription(String source, String provider, String symbol, String traderId) {
        try {
            // 1. 构建对象 (需与订阅时一致)
            MarketDataTopic sourceTopic = new MarketDataTopic(source, provider, symbol);
            SubscriberContext sourceCtx = new SubscriberContext(traderId, SubscriberContext.SubscriberType.MD_CLEAN_TREADER,
                    null);

            DistributionDataTopic distTopic = new DistributionDataTopic(sourceTopic,
                    DistributionDataTopic.Protocol.WEBSOCKET, traderId);
            SubscriberContext userCtx = new SubscriberContext(traderId, SubscriberContext.SubscriberType.DIST_WEBSOCKET,
                    traderId);

            // 2. 退订分发 (断开用户)
            subscriptionCoreService.unsubscribe(distTopic, userCtx);

            // 3. 减少源头引用
            subscriptionCoreService.unsubscribe(sourceTopic, sourceCtx);
        } catch (Exception e) {
            log.error("[TraderSubscription] 取消订阅失败: {}.{}/{}", source, provider, symbol, e);
        }
    }

    /**
     * [新增] 按 TraderID 移除该交易员的所有订阅
     */
    public void removeAllSubscriptionsByTraderId(String traderId) {
        // 1. 从 CoreService 获取该 ID 所有的订阅 Key
        Set<String> subscribedTopics = subscriptionCoreService.getSubscribedTopics(traderId);

        if (subscribedTopics == null || subscribedTopics.isEmpty()) {
            return;
        }

        // 2. 遍历并清理
        for (String topicKey : subscribedTopics) {
            // 判断 Context 类型：如果是分发层
            if (topicKey.startsWith(BaseConstants.MARKET_DATA_DIST_KEY_PREFIX)) {
                List<SubscriberContext> distributionCtxs = subscriptionCoreService
                        .getSubscriberContextsByTopickeyandSubscribeid(topicKey, traderId);
                for (SubscriberContext currentCtx : distributionCtxs) {
                    subscriptionCoreService.unsubscribe(topicKey, currentCtx);
                }
            }
            // 判断 Context 类型：如果是源头层 (MD:CLEAN)
            else if (topicKey.startsWith(BaseConstants.MARKET_DATA_CLEAN_KEY_PREFIX)) {
                SubscriberContext marketDataCtx = new SubscriberContext(traderId,
                        SubscriberContext.SubscriberType.MD_CLEAN_TREADER, null);
                subscriptionCoreService.unsubscribe(topicKey, marketDataCtx);
            }
        }
    }

    public void removeBatchTraderSubscription(List<TraderSubReq> batchTraderUnSubReq) {
        if (batchTraderUnSubReq != null && !batchTraderUnSubReq.isEmpty()) {
            for (TraderSubReq traderUnSubReq : batchTraderUnSubReq) {
                removeTraderSubscription(
                        traderUnSubReq.getSource(),
                        traderUnSubReq.getProvider(),
                        traderUnSubReq.getSymbol(),
                        traderUnSubReq.getTraderId());
            }
        }
    }

    /**
     * 快速校验是否存在订阅
     *
     * @param symbol 货币对，以调用方传入为准
     */
    public boolean hasTraderSubscription(String source, String provider, String symbol) {
        // 检查源头是否活跃
        String mergeTopicKey = MarketDataTopic.buildTopicKey(source, provider, symbol);
        return subscriptionCoreService.hasSubscribers(mergeTopicKey);
    }

    /**
     * 查询交易员已订阅的SubscriptionTopic
     */
    public List<SubscriptionTopic> getTraderSubscriptions(String traderId) {
        Set<String> topicKeys = subscriptionCoreService.getSubscribedTopics(traderId);
        List<SubscriptionTopic> result = new ArrayList<>();
        for (String topicKey : topicKeys) {
            result.add(subscriptionCoreService.getTopicInstance(topicKey));
        }
        return result;
    }
}