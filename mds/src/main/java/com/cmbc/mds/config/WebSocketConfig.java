package com.cmbc.mds.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的内存消息代理，客户端订阅以 /topic 开头的地址
        config.enableSimpleBroker("/topic");
        // 客户端发送消息的前缀（如果有交互需求）
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 WebSocket 端点，前端连接 ws://localhost:8080/ws-fx
        registry.addEndpoint("/ws-fx")
                .setAllowedOriginPatterns("*") // 允许跨域
                .withSockJS(); // 启用 SockJS 回退支持
    }
}