package com.cmbc.mds.forex.distribution.channel.impl;

import com.cmbc.mds.forex.distribution.channel.DistributionChannel;
import com.cmbc.mds.forex.distribution.channel.PloyPricesHandler;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesCacheService;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic;
import com.cmbc.mds.forex.subscription.service.InitSubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略执行分发通道。
 * <p>
 * 接收聚合后的 PloyPrices，按完整 distTopicKey 精确路由到已经注册的策略回调。
 */
@Component
public class StrategyExecutionChannel implements DistributionChannel {

    private static final Logger log = LoggerFactory.getLogger(StrategyExecutionChannel.class);

    /**
     * 注册表：distTopicKey -> (handlerId -> handler)。
     * <p>
     * 外层和内层都使用 ConcurrentHashMap，保证策略模块并发注册/注销时不影响行情分发遍历。
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, PloyPricesHandler>> registry
            = new ConcurrentHashMap<>();

    @Autowired
    private MergeQuotesCacheService mergeQuotesCacheService;

    /**
     * 标准注册：按 sourceKey + strategyId 注册。
     *
     * @param sourceKey  聚合主题 Key，格式为 MD:MERGE:[...]:SYMBOL
     * @param strategyId 策略 ID
     * @param handlerId  同一 distTopicKey 下唯一的回调标识，用于 unregister 定位
     * @param handler    策略回调
     */
    public void register(String sourceKey, String strategyId, String handlerId, PloyPricesHandler handler) {
        String distTopicKey = DistributionDataTopic.buildTopicKey(
                DistributionDataTopic.Protocol.INTERNAL, strategyId, sourceKey);
        registry.computeIfAbsent(distTopicKey, k -> new ConcurrentHashMap<>())
                .put(handlerId, handler);
        log.info("[StrategyChannel] 注册回调: strategyId={}, sourceKey={}, handlerId={}",
                strategyId, sourceKey, handlerId);
    }

    /**
     * 简化注册：仅使用 symbol，内部解析 SYSTEM_INIT 对应的完整 distTopicKey。
     */
    public void registerBySymbol(String symbol, String handlerId, PloyPricesHandler handler) {
        String sourceKey = mergeQuotesCacheService.getSystemInitSourceKey(symbol);
        if (sourceKey == null) {
            throw new IllegalArgumentException(
                    "[StrategyChannel] symbol [" + symbol + "] 未在 SYSTEM_INIT 订阅中找到，"
                            + "请确认 InitSubscriptionService 已完成初始化且该 symbol 已配置");
        }
        String distTopicKey = DistributionDataTopic.buildTopicKey(
                DistributionDataTopic.Protocol.INTERNAL,
                InitSubscriptionService.STRATEGY_ID_SYSTEM_INIT, sourceKey);
        registry.computeIfAbsent(distTopicKey, k -> new ConcurrentHashMap<>())
                .put(handlerId, handler);
        log.info("[StrategyChannel] 注册 Symbol 回调: symbol={}, handlerId={}", symbol, handlerId);
    }

    /**
     * 注销：按 sourceKey + strategyId + handlerId 移除回调。
     */
    public void unregister(String sourceKey, String strategyId, String handlerId) {
        String distTopicKey = DistributionDataTopic.buildTopicKey(
                DistributionDataTopic.Protocol.INTERNAL, strategyId, sourceKey);
        ConcurrentHashMap<String, PloyPricesHandler> handlers = registry.get(distTopicKey);
        if (handlers != null) {
            handlers.remove(handlerId);
            log.info("[StrategyChannel] 注销回调: strategyId={}, sourceKey={}, handlerId={}",
                    strategyId, sourceKey, handlerId);
        }
    }

    /**
     * 简化注销：按 symbol + handlerId 移除，对应 registerBySymbol。
     */
    public void unregisterBySymbol(String symbol, String handlerId) {
        String sourceKey = mergeQuotesCacheService.getSystemInitSourceKey(symbol);
        if (sourceKey != null) {
            unregister(sourceKey, InitSubscriptionService.STRATEGY_ID_SYSTEM_INIT, handlerId);
        }
    }

    @Override
    public boolean supports(Class<?> dataType) {
        return PloyPrices.class.isAssignableFrom(dataType);
    }

    @Override
    public DistributionDataTopic.Protocol getProtocol() {
        return DistributionDataTopic.Protocol.INTERNAL;
    }

    @Override
    public void distribute(String topicKey, Object data, List<SubscriberContext> subscribers) {
        PloyPrices ployPrices = (PloyPrices) data;

        // topicKey 已经是完整 distTopicKey，直接查注册表，不再做 sourceKey/strategyId 反解析。
        dispatchToHandlers(topicKey, ployPrices);

        if (log.isDebugEnabled()) {
            // debug 日志路径也可能随行情高频触发，使用 for 循环避免 stream/lambda 分配。
            for (SubscriberContext ctx : subscribers) {
                if (ctx.getType() == SubscriberContext.SubscriberType.DIST_STRATEGY) {
                    log.debug("[StrategyChannel] 策略[{}] 收到聚合行情: {}",
                            ctx.getSubscriberId(), ployPrices.getSymbol());
                }
            }
        }
    }

    private void dispatchToHandlers(String distTopicKey, PloyPrices ployPrices) {
        ConcurrentHashMap<String, PloyPricesHandler> handlers = registry.get(distTopicKey);
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, PloyPricesHandler> entry : handlers.entrySet()) {
            try {
                entry.getValue().onPloyPrices(ployPrices);
            } catch (Exception e) {
                log.error("[StrategyChannel] 回调执行异常: distTopicKey={}, handlerId={}",
                        distTopicKey, entry.getKey(), e);
            }
        }
    }
}
