package com.cmbc.mds.forex.distribution.channel.impl;

import com.cmbc.mds.forex.distribution.channel.DistributionChannel;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RemoteMonitorChannel implements DistributionChannel {

    private static final Logger log = LoggerFactory.getLogger(RemoteMonitorChannel.class);

    // 当前仅保留远程监控推送占位，真实 MQ/中间件发送组件后续接入。
    // @Autowired private RabbitTemplate rabbitTemplate;

    @Override
    public boolean supports(Class<?> dataType) {
        return Depth.class.isAssignableFrom(dataType) || PloyPrices.class.isAssignableFrom(dataType);
    }

    @Override
    public DistributionDataTopic.Protocol getProtocol() {
        return DistributionDataTopic.Protocol.MIDDLEWARE;
    }

    @Override
    public void distribute(String topicKey, Object data, List<SubscriberContext> subscribers) {
        // 高频分发路径避免 stream/lambda 额外对象分配，直接收集监控端订阅用户。
        List<String> targetUserIds = new ArrayList<>();
        for (SubscriberContext ctx : subscribers) {
            if (ctx.getType() == SubscriberContext.SubscriberType.MD_CLEAN_TREADER
                    && ctx.getSubscriberId() != null) {
                targetUserIds.add(ctx.getSubscriberId());
            }
        }

        if (targetUserIds.isEmpty()) {
            return;
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug("[RemoteMonitorChannel] 准备推送数据至管理端. DataClass={}, Targets={}",
                        data.getClass().getSimpleName(), targetUserIds);
                // String json = JsonUtils.toJson(data);
                // rabbitTemplate.convertAndSend("exchange", "routingKey", json);
            }
        } catch (Exception e) {
            log.error("[RemoteMonitor] 跨进程推送失败. topicKey={}", topicKey, e);
        }
    }
}
