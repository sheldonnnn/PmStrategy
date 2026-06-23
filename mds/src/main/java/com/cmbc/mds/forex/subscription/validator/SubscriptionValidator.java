package com.cmbc.mds.forex.subscription.validator;

import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;

public interface SubscriptionValidator {
    boolean supports(Class<? extends SubscriptionTopic> topicClass);
    void validate(SubscriptionTopic topic);
}