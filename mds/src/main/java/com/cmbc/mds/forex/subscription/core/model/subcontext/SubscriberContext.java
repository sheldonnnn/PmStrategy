package com.cmbc.mds.forex.subscription.core.model.subcontext;

import java.util.Objects;

/**
 * 订阅者上下文
 * [修改] 将扩展属性改为明确的 distributeId，用于标识归属关系（如策略所属的TraderID）
 */
public class SubscriberContext {

    public enum SubscriberType {
        MD_CLEAN_TREADER,// 表示这个订阅是 MatketData的订阅,在TREADER订阅中使用的
        MD_CLEAN_STRATEGY,// 表示这个订阅是 MatketData的订阅,在STRATEGY订阅中使用的

        MD_MERGE, // 表示这个订阅是 MergeData 的订阅,并且是 聚合任务 需要的
        DIST_MERGE, // 表示这个订阅是 分发场景 的订阅，并且是 MergeEngin通道 需要的分发
        DIST_WEBSOCKET, // 表示这个订阅是 分发场景 的订阅，并且是 Websocket通道 需要的分发
        DIST_STRATEGY, // 表示这个订阅是 分发场景 的订阅，并且是 Strategy执行通道 需要的分发
        DIST_VALUATION // 表示这个订阅是 分发场景 的订阅，并且是 Valuation执行通道 需要的分发
    }

    private final String subscriberId; // 订阅主键id
    private final SubscriberType type; // 订阅类型

    // todo 后续是否能支持完成点对点分发？ 是都需要对SubscriberContext 类进行扩展？
    private final String distributeId; // 分发归属ID (例如: 对于策略订阅，此处存储其所属的 traderId)

    public SubscriberContext(String subscriberId, SubscriberType type, String distributeId) {
        this.subscriberId = subscriberId;
        this.type = type;
        this.distributeId = distributeId;
    }

    public String getSubscriberId() {
        return subscriberId;
    }

    public SubscriberType getType() {
        return type;
    }

    /**
     * 获取分发归属ID
     */
    public String getDistributeId() {
        return distributeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SubscriberContext that = (SubscriberContext) o;
        return Objects.equals(subscriberId, that.subscriberId) &&
                type == that.type &&
                Objects.equals(distributeId, that.distributeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriberId, type, distributeId);
    }
}