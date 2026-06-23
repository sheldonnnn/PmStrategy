package com.cmbc.mds.forex.subscription.core.event;

import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;
import org.springframework.context.ApplicationEvent;

/**
 * 主题激活事件 (订阅人数 0 -> 1)
 * 用于通知 Engine 开启底层资源
 */
public class TopicActiveEvent extends ApplicationEvent {
    private final SubscriptionTopic topic;

    public TopicActiveEvent(Object source, SubscriptionTopic topic) {
        super(source);
        this.topic = topic;
    }

    public SubscriptionTopic getTopic() { return topic; }
}