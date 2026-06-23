package com.cmbc.mds.ksd.ws;

import com.cmbc.mds.forex.quotes.protocol.KsdGatewayFrameDecoder;
import com.cmbc.mds.forex.quotes.protocol.KsdGatewayFrameType;
import com.cmbc.mds.forex.quotes.receiver.impl.DimpleWsQuoteReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

@Component
public class DimpleKsdWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DimpleKsdWebSocketHandler.class);

    private final DimpleWsQuoteReceiver receiver;
    private final KsdGatewayConnectionRegistry connectionRegistry;

    public DimpleKsdWebSocketHandler(DimpleWsQuoteReceiver receiver,
                                     KsdGatewayConnectionRegistry connectionRegistry) {
        this.receiver = receiver;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        KsdGatewayConnectionRegistry.ConnectionInfo connectionInfo = connectionRegistry.register(session);
        log.info("KsdGateway行情WebSocket连接建立。sessionId={}, remote={}, gatewayId={}, 分片={}/{}",
                new Object[]{
                        session.getId(),
                        session.getRemoteAddress(),
                        connectionInfo.getGatewayId(),
                        connectionInfo.getShardIndex(),
                        connectionInfo.getShardCount()
                });
        receiver.onGatewayHeartbeat();
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            byte[] payload = new byte[message.getPayloadLength()];
            message.getPayload().get(payload);

            KsdGatewayFrameDecoder.DecodedFrame frame = KsdGatewayFrameDecoder.decode(payload);
            connectionRegistry.markActive(session);

            if (frame.getType() == KsdGatewayFrameType.QUOTE) {
                receiver.onGatewayQuote(frame.getQuote());
            } else if (frame.getType() == KsdGatewayFrameType.HEARTBEAT) {
                receiver.onGatewayHeartbeat();
            } else {
                log.debug("忽略未知KsdGateway帧类型。rawType={}, gatewayId={}",
                        frame.getRawType(), frame.getGatewayId());
            }
        } catch (Exception e) {
            log.error("处理KsdGateway二进制行情帧失败。sessionId={}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        KsdGatewayConnectionRegistry.ConnectionCloseResult closeResult = connectionRegistry.unregister(session);
        log.warn("KsdGateway行情WebSocket连接关闭。sessionId={}, status={}", session.getId(), status);
        if (closeResult.getConnectionInfo() == null) {
            log.warn("KsdGateway行情WebSocket连接关闭事件未匹配到已登记连接，跳过全局断连处理。sessionId={}", session.getId());
            return;
        }
        if (!closeResult.hasAnyActiveConnection()) {
            receiver.onGatewayDisconnected();
        } else {
            log.warn("KsdGateway仍存在活跃分片WebSocket连接，跳过全局断连处理。sessionId={}",
                    session.getId());
        }
    }
}
