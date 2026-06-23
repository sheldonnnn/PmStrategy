package com.cmbc.mds.forex.subscription.core.model.topic;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * [新增] 分发数据主题
 * 定义"数据发给谁/怎么发" (分发路由)
 * 包装了源头主题 MarketDataTopic，源头主题是为了在直接开启
 */
@Getter
@Setter
public class DistributionDataTopic implements SubscriptionTopic {
    public static final String TYPE = "DISTRIBUTION_DATA";

    public enum Protocol {
        WEBSOCKET, // 推送前端
        INTERNAL, // 进程内调用 (Strategy)
        MIDDLEWARE // MQ/Kafka (Service)
    }

    private final SubscriptionTopic sourceTopic; // 关联的数据源（provider+symbol+level）
    private final Protocol protocol; // 分发协议
    private final String targetId; // 目标ID (SessionId, StrategyId, QueueName)

    public DistributionDataTopic(SubscriptionTopic sourceTopic, Protocol protocol, String targetId) {
        this.sourceTopic = sourceTopic;
        this.protocol = protocol;
        this.targetId = targetId;
    }

    /**
     * 静态工厂方法：构建 distTopicKey
     * <p>
     * Key 格式：{@code DIST:{PROTOCOL}:{TARGET_ID}:{SOURCE_KEY}}
     * <p>
     * 供需要预先计算 distTopicKey 的场景使用（如 StrategyExecutionChannel 的回调注册），
     * 避免格式字符串分散在各处。
     *
     * @param protocol   分发协议
     * @param targetId   目标 ID（SessionId / StrategyId / QueueName）
     * @param sourceKey  来源 TopicKey（如 MergeDataTopic.getTopicKey()）
     * @return 完整的 distTopicKey 字符串
     */
    public static String buildTopicKey(Protocol protocol, String targetId, String sourceKey) {
        return BaseConstants.MARKET_DATA_DIST_KEY_PREFIX + protocol + ":" + targetId + ":" + sourceKey;
    }

    @Override
    public String getTopicKey() {
        // 委托静态方法，保证格式唯一
        return buildTopicKey(protocol, targetId, sourceTopic.getTopicKey());
    }

    @Override
    public String getTopicType() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DistributionDataTopic that = (DistributionDataTopic) o;
        return Objects.equals(sourceTopic, that.sourceTopic) &&
                protocol == that.protocol &&
                Objects.equals(targetId, that.targetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceTopic, protocol, targetId);
    }
}
