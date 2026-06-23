package com.cmbc.mds.ksd.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class DimpleKsdWebSocketConfig implements WebSocketConfigurer {

    private final DimpleKsdWebSocketHandler handler;

    public DimpleKsdWebSocketConfig(DimpleKsdWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/ksd/quotes")
                .setAllowedOriginPatterns("*");
    }
}
