package com.cmbc.mds.forex.distribution.channel.impl;

import com.cmbc.mds.forex.distribution.channel.DistributionChannel;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic; // 新增导入
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSocketDistributionChannel implements DistributionChannel {

    private static final Logger log = LoggerFactory.getLogger(WebSocketDistributionChannel.class);

    // 内部协议前缀，需要从路径中剥离
    private static final String INTERNAL_PREFIX = "DIST:WEBSOCKET:";

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public boolean supports(Class<?> dataType) {
        // 同时支持 Depth (原始清洗) 和 PloyPrices (聚合结果)
        return Depth.class.isAssignableFrom(dataType) || PloyPrices.class.isAssignableFrom(dataType);
    }

    @Override
    public DistributionDataTopic.Protocol getProtocol() {
        return DistributionDataTopic.Protocol.WEBSOCKET;
    }

    @Override
    public void distribute(String topicKey, Object data, List<SubscriberContext> subscribers) {
        try {
            // 1. [核心修复] 剥离内部路由前缀，保留用户ID以实现隔离
            // 输入: "DIST:WEBSOCKET:T-1001:MD:CLEAN:GS.GS:USD/JPY"
            // 输出: "T-1001:MD:CLEAN:GS.GS:USD/JPY"
            String userPathKey = stripInternalPrefix(topicKey);

            if (userPathKey == null) {
                // 如果解析失败，不进行推送
                return;
            }

            // 2. 生成 WebSocket 目标地址
            // 先移除货币对斜杠，再将冒号和点号均转换为斜杠
            // "T-1001:MD:CLEAN:GS.GS:USDJPY" -> "/topic/T-1001/MD/CLEAN/GS/GS/USDJPY"
            String destination = "/topic/" + userPathKey.replace("/","").replace(":", "/").replace(".", "/");

            // 3. 执行推送
            // 由于是针对特定 Topic 推送，session 为 null 是正常的 (SimpMessagingTemplate 内部处理)
            messagingTemplate.convertAndSend(destination, data);

            if (log.isDebugEnabled()) {
                log.debug("[WebSocket] Sent to [{}]", destination);
            }

        } catch (Exception e) {
            log.error("[WebSocket] Distribution failed: " + topicKey, e);
        }
    }

    /**
     * 剥离内部路由前缀，保留 "TargetId:SourceKey"
     */
    private String stripInternalPrefix(String fullKey) {
        if (fullKey == null) {
            log.warn("[WebSocket] stripInternalPrefix 收到 null topicKey，跳过分发");
            return null;
        }
        if (fullKey.startsWith(INTERNAL_PREFIX)) {
            return fullKey.substring(INTERNAL_PREFIX.length());
        }

        // 兜底逻辑：防止前缀格式有细微变化
        // 假设格式固定为 DIST:PROTOCOL:TARGET:...
        String[] parts = fullKey.split(":");
        // 确保至少有 DIST, PROTOCOL, TARGET
        if (parts.length > 2 && "DIST".equals(parts[0])) {
            // 找到第二个冒号的位置 (PROTOCOL 结束的位置)
            int protocolEndIndex = fullKey.indexOf(":", fullKey.indexOf(":") + 1);
            if (protocolEndIndex > 0 && protocolEndIndex < fullKey.length() - 1) {
                // 返回 TargetId 及其后的所有内容
                return fullKey.substring(protocolEndIndex + 1);
            }
        }
        return null;
    }
}