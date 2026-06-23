package com.cmbc.mds.forex.subscription.listener;

import com.cmbc.mds.forex.engine.MarketDataEngine;
import com.cmbc.mds.forex.subscription.core.event.TopicActiveEvent;
import com.cmbc.mds.forex.subscription.core.event.TopicInactiveEvent;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.MergeDataTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MarketDataResourceListener {

    private static final Logger log = LoggerFactory.getLogger(MarketDataResourceListener.class);
    @Autowired private MarketDataEngine marketDataEngine;

    @EventListener
    public void onTopicActive(TopicActiveEvent event) {
        if (event.getTopic() instanceof MarketDataTopic) {
            MarketDataTopic topic = (MarketDataTopic) event.getTopic();
            // 使用工具类生成 Engine 内部识别的 Key
            String cleanId = MarketDataTopic.buildTopicKey(topic.getSource(), topic.getProvider(), topic.getSymbol());

            log.info(">>> 激活信号: 开启数据清洗通道 [{}]", cleanId);
            marketDataEngine.registerCleanChannel(cleanId);
        }else if (event.getTopic() instanceof MergeDataTopic) {
            MergeDataTopic topic = (MergeDataTopic) event.getTopic();
            String mergeId = topic.getTopicKey();

            log.info(">>> 激活信号: 开启数据聚合通道 [{}]", mergeId);
            marketDataEngine.registerMergeStrategy(mergeId);
        }
    }

    @EventListener
    public void onTopicInactive(TopicInactiveEvent event) {
        if (event.getTopic() instanceof MarketDataTopic) {
            MarketDataTopic topic = (MarketDataTopic) event.getTopic();
            String cleanId = topic.getTopicKey();

            log.info("<<< 失活信号: 关闭数据清洗通道 [{}]", cleanId);
            marketDataEngine.removeCleanChannel(cleanId);
        }else if (event.getTopic() instanceof MergeDataTopic) {
            MergeDataTopic topic = (MergeDataTopic) event.getTopic();
            String mergeId = topic.getTopicKey();

            log.info(">>> 失活信号: 关闭数据聚合通道 [{}]", mergeId);
            marketDataEngine.removeMergeStrategy(mergeId);
        }
    }
}