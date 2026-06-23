package com.cmbc.mds.forex.subscription.core.model.topic;

/**
 * [接口] 订阅主题
 * 任何资源（行情、系统状态、期权）要想被订阅，必须实现此接口
 */
public interface SubscriptionTopic {
    /**
     * 获取用于 Map 存储的唯一 Key (例如: "MD:UBS:USDCNY")
     */
    String getTopicKey();

    /**
     * 获取业务类型 (例如: "MARKET_DATA")
     */
    String getTopicType();
}