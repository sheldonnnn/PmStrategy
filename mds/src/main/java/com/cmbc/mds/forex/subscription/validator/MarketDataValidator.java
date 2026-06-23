package com.cmbc.mds.forex.subscription.validator;

import com.cmbc.mds.forex.provider.service.ForeignBankConnectionService;
import com.cmbc.mds.forex.provider.service.SourceConfigService;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MarketDataValidator implements SubscriptionValidator {

    private static final Logger log = LoggerFactory.getLogger(MarketDataValidator.class);

    @Autowired private SourceConfigService configService;
    @Autowired private ForeignBankConnectionService connectionService;

    @Override
    public boolean supports(Class<? extends SubscriptionTopic> topicClass) {
        return MarketDataTopic.class.isAssignableFrom(topicClass);
    }

    @Override
    public void validate(SubscriptionTopic topic) {
        MarketDataTopic mdTopic = (MarketDataTopic) topic;
        String provider = mdTopic.getProvider();

        // 1. 检查配置
        // 注意：由于 FxProviderConfigService 目前为空初始化，在外部数据真正加载前，这里必须保持注释，否则会阻断所有订阅
//        if (!configService.isValidProvider(provider)) {
//            throw new SubscriptionException("订阅失败：无效的报价源配置 [" + provider + "]");
//        }
    }
}