package com.cmbc.mds.forex.distribution.channel;

import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic; // 新增导入
import java.util.List;

/**
 * 分发通道接口，仅用于MDS对外分发
 */
public interface DistributionChannel {
    /**
     * 判断通道是否支持处理该类型的数据
     * @param dataType 数据类型 (例如 Depth.class 或 PloyPrices.class)
     * @return boolean
     */
    boolean supports(Class<?> dataType);

    /**
     * [新增] 获取该通道所属的分发协议
     * @return 协议枚举
     */
    DistributionDataTopic.Protocol getProtocol();

    /**
     * 执行分发
     * @param topicKey  行情主题Key
     * @param data      行情数据
     * @param subscribers 该主题下的所有订阅者上下文
     */
    void distribute(String topicKey, Object data, List<SubscriberContext> subscribers);
}