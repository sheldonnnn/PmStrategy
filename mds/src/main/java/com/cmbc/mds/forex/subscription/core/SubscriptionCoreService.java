package com.cmbc.mds.forex.subscription.core;

import com.cmbc.mds.forex.subscription.core.event.TopicActiveEvent;
import com.cmbc.mds.forex.subscription.core.event.TopicInactiveEvent;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;
import com.cmbc.mds.forex.subscription.validator.SubscriptionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.cmbc.mds.forex.subscription.service.InitSubscriptionService.STRATEGY_ID_SYSTEM_INIT;

@Service
public class SubscriptionCoreService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCoreService.class);

    // 正向缓存: TopicKey -> Set<Context>
    private final ConcurrentHashMap<String, Set<SubscriberContext>> topicSubscribers = new ConcurrentHashMap<>();

    // TopicKey -> TopicInstance 缓存 (用于在仅知道Key的情况下反查Topic对象以触发Event)
    private final ConcurrentHashMap<String, SubscriptionTopic> activeTopics = new ConcurrentHashMap<>();

    // 反向缓存: SubscriberId -> Set<TopicKey>
    private final ConcurrentHashMap<String, Set<String>> subscriberTopics = new ConcurrentHashMap<>();

    // 类型分桶二级索引: TopicKey -> (SubscriberType -> Set<SubscriberContext>)
    // 专供热路径按类型精确查询（如 CleanService 只需 MD_CLEAN_STRATEGY），避免 O(n) 遍历过滤。
    // 与 topicSubscribers 在同一 synchronized 锁内同步维护，保证一致性。
    private final ConcurrentHashMap<String, ConcurrentHashMap<SubscriberContext.SubscriberType, Set<SubscriberContext>>>
            topicSubscribersByType = new ConcurrentHashMap<>();

    @Autowired
    private List<SubscriptionValidator> validators;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 通用订阅入口
     */
    public synchronized void subscribe(SubscriptionTopic topic, SubscriberContext subscriber) {
        // 1. 策略校验
        if (!STRATEGY_ID_SYSTEM_INIT.equals(subscriber.getSubscriberId())) { // 初始化订阅无需进行连接有效性校验
            for (SubscriptionValidator v : validators) {
                if (v.supports(topic.getClass())) {
                    v.validate(topic);
                }
            }
        }

        String topicKey = topic.getTopicKey();
        String subId = subscriber.getSubscriberId();

        Set<SubscriberContext> contexts = topicSubscribers.computeIfAbsent(topicKey,
                k -> ConcurrentHashMap.newKeySet());
        boolean isFirst = contexts.isEmpty();

        // 2. 维护状态
        if (contexts.add(subscriber)) {
            // 维护 Topic 实例缓存
            activeTopics.putIfAbsent(topicKey, topic);

            // 维护反向缓存
            subscriberTopics.computeIfAbsent(subId, k -> ConcurrentHashMap.newKeySet()).add(topicKey);

            // 维护类型分桶二级索引
            topicSubscribersByType
                    .computeIfAbsent(topicKey, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(subscriber.getType(), k -> ConcurrentHashMap.newKeySet())
                    .add(subscriber);

            // 3. 触发激活事件
            if (isFirst) {
                eventPublisher.publishEvent(new TopicActiveEvent(this, topic));
                log.info("订阅成功: Topic=[{}], subId=[{}], Type=[{}]", topicKey, subId, subscriber.getType());
            } else {
                log.info("订阅Key重复，新增subscriber: Topic=[{}], subId=[{}], Type=[{}]", topicKey, subId,
                        subscriber.getType());
            }
        } else {
            // 幂等保护：同一 (subscriberId, type) 已在该 topicKey 下存在，拒绝重复写入。
            // 常见原因：InitSubscriptionService 的 foreignConfig 与 dimpleConfig 中
            // 配置了相同的 (source, provider, symbol) 组合，导致 subscribe() 被多次调用。
            // 当前行为：Set.add() 返回 false，状态不变，Topic 激活事件不会重复触发。
            // 建议：排查 application.yml 中是否有重复的 symbol 配置。
            log.warn("重复订阅被拒绝(幂等保护): Topic=[{}], subId=[{}], Type=[{}]，请检查订阅配置是否有重复",
                    topicKey, subId, subscriber.getType());
        }
    }

    /**
     * 通用退订入口 (通过 Topic 对象)
     */
    public synchronized void unsubscribe(SubscriptionTopic topic, SubscriberContext subscriber) {
        unsubscribe(topic.getTopicKey(), subscriber);
    }

    /**
     * [重构新增] 支持直接通过 TopicKey 退订
     * 允许业务层在不知道完整 Topic 构造参数的情况下，通过 ID 反查 Key 进行退订
     */
    public synchronized void unsubscribe(String topicKey, SubscriberContext subscriber) {
        String subId = subscriber.getSubscriberId();
        Set<SubscriberContext> contexts = topicSubscribers.get(topicKey);

        // 注意：Set.remove 依赖 SubscriberContext 的 equals 方法
        if (contexts != null && contexts.remove(subscriber)) {
            // 维护反向缓存
            Set<String> reverseKeys = subscriberTopics.get(subId);
            if (reverseKeys != null) {
                reverseKeys.remove(topicKey);
                if (reverseKeys.isEmpty()) {
                    subscriberTopics.remove(subId);
                }
            }

            // 同步清理类型分桶二级索引
            ConcurrentHashMap<SubscriberContext.SubscriberType, Set<SubscriberContext>> byType =
                    topicSubscribersByType.get(topicKey);
            if (byType != null) {
                Set<SubscriberContext> typeSet = byType.get(subscriber.getType());
                if (typeSet != null) {
                    typeSet.remove(subscriber);
                    if (typeSet.isEmpty()) byType.remove(subscriber.getType());
                }
                if (byType.isEmpty()) topicSubscribersByType.remove(topicKey);
            }

            log.info("退订成功: Topic=[{}], User=[{}]", topicKey, subId);

            // 触发失活事件
            if (contexts.isEmpty()) {
                topicSubscribers.remove(topicKey);

                // 从缓存中获取 Topic 对象以触发事件
                SubscriptionTopic topicInstance = activeTopics.remove(topicKey);
                if (topicInstance != null) {
                    eventPublisher.publishEvent(new TopicInactiveEvent(this, topicInstance));
                } else {
                    log.warn("退订时未找到Topic实例，无法触发InactiveEvent: Key=[{}]", topicKey);
                }
            }
        }
    }

    /**
     * 判断某主题是否有订阅者
     */
    public boolean hasSubscribers(String topicKey) {
        Set<SubscriberContext> contexts = topicSubscribers.get(topicKey);
        return contexts != null && !contexts.isEmpty();
    }

    /**
     * 获取某一个TopicKey下某一个订阅者的订阅者上下文
     * 订阅Ctx的subscriberId字段，对于策略订阅时为策略ID，交易员订阅时为交易员ID，
     */
    public List<SubscriberContext> getSubscriberContextsByTopickeyandSubscribeid(String topicKey, String subscriberId) {
        Set<SubscriberContext> contexts = topicSubscribers.get(topicKey);
        if (contexts == null || contexts.isEmpty()) {
            return Collections.emptyList();
        }

        return contexts.stream()
                .filter(ctx -> ctx.getSubscriberId().equals(subscriberId))
                .collect(Collectors.toList());
    }

    /**
     * [新增] 获取指定订阅者订阅的所有 TopicKey
     */
    public Set<String> getSubscribedTopics(String subscriberId) {
        Set<String> topics = subscriberTopics.get(subscriberId);
        if (topics == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(topics);
    }

    /**
     * 获取该 Topic 下的所有订阅者上下文
     */
    public List<SubscriberContext> getAllSubscribers(String topicKey) {
        Set<SubscriberContext> contexts = topicSubscribers.get(topicKey);
        if (contexts == null || contexts.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(contexts);
    }

    /**
     * 获取该 topicKey 获取原始的 SubscriptionTopic实体
     */
    public SubscriptionTopic getTopicInstance(String topicKey) {
        return activeTopics.get(topicKey);
    }

    /**
     * 按类型获取分发队列 ID 快照，避免行情分发时遍历实时订阅集合。
     */
    public Set<String> getDistributeIdsByType(String topicKey, SubscriberContext.SubscriberType type) {
        ConcurrentHashMap<SubscriberContext.SubscriberType, Set<SubscriberContext>> byType =
                topicSubscribersByType.get(topicKey);
        if (byType == null) return Collections.emptySet();
        Set<SubscriberContext> ctxs = byType.get(type);
        if (ctxs == null || ctxs.isEmpty()) return Collections.emptySet();

        Set<String> distributeIds = new HashSet<>();
        for (SubscriberContext ctx : ctxs) {
            String distributeId = ctx.getDistributeId();
            if (distributeId != null) {
                distributeIds.add(distributeId);
            }
        }
        return distributeIds;
    }

    /**
     * 按类型精确查询订阅者，返回快照列表，保留给非热路径和测试使用。
     */
    public List<SubscriberContext> getSubscribersByType(String topicKey, SubscriberContext.SubscriberType type) {
        ConcurrentHashMap<SubscriberContext.SubscriberType, Set<SubscriberContext>> byType =
                topicSubscribersByType.get(topicKey);
        if (byType == null) return Collections.emptyList();
        Set<SubscriberContext> ctxs = byType.get(type);
        if (ctxs == null || ctxs.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(ctxs); // 快照，防止迭代期间并发修改
    }
}
