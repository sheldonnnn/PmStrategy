package com.cmbc.mds.forex.subscription.core.event;

import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;
import org.springframework.context.ApplicationEvent;

/**
 * 主题失活事件 (订阅人数 1 -> 0)
 * 用于通知 Engine 释放资源
 */
public class TopicInactiveEvent extends ApplicationEvent {
    private final SubscriptionTopic topic;

    public TopicInactiveEvent(Object source, SubscriptionTopic topic) {
        super(source);
        this.topic = topic;
    }

    public SubscriptionTopic getTopic() { return topic; }
}