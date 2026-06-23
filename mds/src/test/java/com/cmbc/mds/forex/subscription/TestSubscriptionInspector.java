package com.cmbc.mds.forex.subscription;
import com.cmbc.mds.forex.distribution.service.QuoteDistributionService;
import com.cmbc.mds.forex.engine.MarketDataChannelRegistry;
import com.cmbc.mds.forex.engine.queue.ConflatingQueue;
import com.cmbc.mds.forex.engine.queue.MarketDataQueueEvent;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.MergeMessage;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试专用的检查工具类，用于通过反射读取服务的私有缓存状态。
 */
@Component
public class TestSubscriptionInspector {

    @Autowired
    private SubscriptionCoreService coreService;

    @Autowired
    private MarketDataChannelRegistry channelRegistry;

    @Autowired
    private QuoteDistributionService distributionService;

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access field: " + fieldName, e);
        }
    }

    /**
     * 获取【主题订阅者映射缓存】
     * 作用：记录某个 Topic（Key）当前被哪些上下文（Context）订阅。
     * 只要这个集合不为空，代表该 Topic 处于活跃状态。
     * Map结构：TopicKey -> Set<SubscriberContext>
     */
    public Map<String, Set<SubscriberContext>> getTopicSubscribers() {
        return getFieldValue(coreService, "topicSubscribers");
    }

    /**
     * 获取【活跃主题实例缓存】
     * 作用：保存当前系统中存活的 Topic 实例对象（包含数据源、币种等具体信息）。
     * Map结构：TopicKey -> SubscriptionTopic
     */
    public Map<String, SubscriptionTopic> getActiveTopics() {
        return getFieldValue(coreService, "activeTopics");
    }

    /**
     * 获取【订阅者拥有的主题缓存】（反向索引）
     * 作用：记录某个业务方（如策略ID、交易员ID）目前订阅了哪些 TopicKey。
     * 用于在用户退订时，快速找到并清理他名下的所有 Topic。
     * Map结构：SubscriberId -> Set<TopicKey>
     */
    public Map<String, Set<String>> getSubscriberTopics() {
        return getFieldValue(coreService, "subscriberTopics");
    }

    /**
     * 获取【按类型分类的主题订阅者缓存】
     * 作用：精细化管理，按 Context 的 SubscriberType (如 STRATEGY, WEBSOCKET) 进行分组。
     * 方便按类型广播或清理。
     */
    public Map<String, ConcurrentHashMap<SubscriberContext.SubscriberType, Set<SubscriberContext>>> getTopicSubscribersByType() {
        return getFieldValue(coreService, "topicSubscribersByType");
    }

    /**
     * 获取【清洗层数据队列】
     * 作用：保存活跃清洗通道对应的覆盖型队列。
     * Map结构：CleanTopicKey -> ConflatingQueue<MarketDataQueueEvent<Depth>>
     */
    public Map<String, ConflatingQueue<MarketDataQueueEvent<Depth>>> getCleanQueues() {
        return getFieldValue(channelRegistry, "cleanQueues");
    }

    /**
     * 快速获取所有存活的【清洗层队列】的 Key 集合
     */
    public Set<String> getCleanQueueKeys() {
        return getCleanQueues().keySet();
    }

    /**
     * 获取【聚合层数据队列】
     * 作用：保存活跃聚合通道对应的覆盖型队列。
     * Map结构：MergeTopicKey -> ConflatingQueue<MarketDataQueueEvent<MergeMessage>>
     */
    public Map<String, ConflatingQueue<MarketDataQueueEvent<MergeMessage>>> getMergeQueues() {
        return getFieldValue(channelRegistry, "mergeQueues");
    }

    /**
     * 快速获取所有存活的【聚合层队列】的 Key 集合
     */
    public Set<String> getMergeQueueKeys() {
        return getMergeQueues().keySet();
    }

    /**
     * 获取【分发路由表】
     * 作用：决定一个底层数据源（Merge/Clean）要通过什么协议（INTERNAL/WEBSOCKET），
     * 推送给哪个具体的目标（TargetId）。
     * Map结构：SourceTopicKey -> Set<DistributionDataTopic>
     */
    public Map<String, Set<DistributionDataTopic>> getSourceToDistMap() {
        return getFieldValue(distributionService, "sourceToDistMap");
    }

    /**
     * 清理所有缓存和队列状态，用于确保测试用例之间的绝对隔离
     */
    public void clearAllState() {
        getTopicSubscribers().clear();
        getActiveTopics().clear();
        getSubscriberTopics().clear();
        getTopicSubscribersByType().clear();
        getCleanQueues().clear();
        getMergeQueues().clear();
        getSourceToDistMap().clear();
    }
}
