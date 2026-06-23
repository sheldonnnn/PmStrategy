package com.cmbc.mds.ksd.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class KsdGatewayConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(KsdGatewayConnectionRegistry.class);
    private static final String DEFAULT_GATEWAY_ID = "ksd-gateway";

    private final Map<String, ConnectionInfo> sessionConnections = new HashMap<>();
    private final Map<String, GatewayConnectionState> gatewayStates = new HashMap<>();

    public synchronized ConnectionInfo register(WebSocketSession session) {
        ConnectionInfo info = ConnectionInfo.from(session);
        sessionConnections.put(session.getId(), info);

        GatewayConnectionState state = gatewayStates.computeIfAbsent(
                info.gatewayId,
                key -> new GatewayConnectionState(info.gatewayId, info.shardCount));
        state.shardCount = Math.max(state.shardCount, info.shardCount);
        state.addSession(info.shardIndex, session.getId());
        state.lastActiveTimeMillis = System.currentTimeMillis();

        log.info("KsdGateway分片WebSocket连接建立。sessionId={}, gatewayId={}, 分片={}/{}, 活跃分片数={}",
                new Object[]{session.getId(), info.gatewayId, info.shardIndex, state.shardCount, state.activeShardCount()});
        return info;
    }

    public synchronized void markActive(WebSocketSession session) {
        ConnectionInfo info = sessionConnections.get(session.getId());
        if (info == null) {
            info = register(session);
        }
        GatewayConnectionState state = gatewayStates.get(info.gatewayId);
        if (state != null) {
            state.lastActiveTimeMillis = System.currentTimeMillis();
        }
    }

    public synchronized ConnectionCloseResult unregister(WebSocketSession session) {
        ConnectionInfo info = sessionConnections.remove(session.getId());
        if (info == null) {
            return new ConnectionCloseResult(null, hasAnyActiveConnection());
        }

        GatewayConnectionState state = gatewayStates.get(info.gatewayId);
        if (state != null) {
            state.removeSession(info.shardIndex, session.getId());
            if (state.isEmpty()) {
                gatewayStates.remove(info.gatewayId);
            }
        }

        boolean hasAnyActiveConnection = hasAnyActiveConnection();
        log.warn("KsdGateway分片WebSocket连接断开。sessionId={}, gatewayId={}, 分片={}/{}, 剩余活跃连接数={}",
                new Object[]{session.getId(), info.gatewayId, info.shardIndex, info.shardCount, activeConnectionCount()});
        return new ConnectionCloseResult(info, hasAnyActiveConnection);
    }

    public synchronized boolean hasAnyActiveConnection() {
        return !sessionConnections.isEmpty();
    }

    private int activeConnectionCount() {
        return sessionConnections.size();
    }

    public static final class ConnectionCloseResult {
        private final ConnectionInfo connectionInfo;
        private final boolean hasAnyActiveConnection;

        private ConnectionCloseResult(ConnectionInfo connectionInfo, boolean hasAnyActiveConnection) {
            this.connectionInfo = connectionInfo;
            this.hasAnyActiveConnection = hasAnyActiveConnection;
        }

        public ConnectionInfo getConnectionInfo() {
            return connectionInfo;
        }

        public boolean hasAnyActiveConnection() {
            return hasAnyActiveConnection;
        }
    }

    public static final class ConnectionInfo {
        private final String gatewayId;
        private final int shardIndex;
        private final int shardCount;

        private ConnectionInfo(String gatewayId, int shardIndex, int shardCount) {
            this.gatewayId = gatewayId;
            this.shardIndex = shardIndex;
            this.shardCount = shardCount;
        }

        public String getGatewayId() {
            return gatewayId;
        }

        public int getShardIndex() {
            return shardIndex;
        }

        public int getShardCount() {
            return shardCount;
        }

        private static ConnectionInfo from(WebSocketSession session) {
            Map<String, String> query = parseQuery(session.getUri());
            String gatewayId = valueOrDefault(query.get("gatewayId"), DEFAULT_GATEWAY_ID);
            int shardCount = parsePositiveInt(query.get("shardCount"), 1);
            int shardIndex = parseNonNegativeInt(query.get("shardIndex"), 0);
            if (shardIndex >= shardCount) {
                shardIndex = 0;
            }
            return new ConnectionInfo(gatewayId, shardIndex, shardCount);
        }

        private static Map<String, String> parseQuery(URI uri) {
            Map<String, String> queryValues = new HashMap<>();
            if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isEmpty()) {
                return queryValues;
            }

            String[] pairs = uri.getRawQuery().split("&");
            for (String pair : pairs) {
                int separator = pair.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = decode(pair.substring(0, separator));
                String value = decode(pair.substring(separator + 1));
                queryValues.put(key, value);
            }
            return queryValues;
        }

        private static String decode(String value) {
            try {
                return URLDecoder.decode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                log.warn("[KsdGatewayRegistry] Decode KsdGateway WebSocket query value failed. rawValue={}", value, e);
                return value;
            }
        }

        private static String valueOrDefault(String value, String defaultValue) {
            return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
        }

        private static int parsePositiveInt(String value, int defaultValue) {
            int parsed = parseNonNegativeInt(value, defaultValue);
            return parsed > 0 ? parsed : defaultValue;
        }

        private static int parseNonNegativeInt(String value, int defaultValue) {
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            try {
                int parsed = Integer.parseInt(value.trim());
                return parsed >= 0 ? parsed : defaultValue;
            } catch (NumberFormatException e) {
                log.warn("[KsdGatewayRegistry] Parse KsdGateway WebSocket numeric query value failed. rawValue={}, defaultValue={}",
                        value, defaultValue, e);
                return defaultValue;
            }
        }
    }

    private static final class GatewayConnectionState {
        private final String gatewayId;
        private final Map<Integer, Set<String>> shardSessions = new HashMap<>();
        private int shardCount;
        private long lastActiveTimeMillis;

        private GatewayConnectionState(String gatewayId, int shardCount) {
            this.gatewayId = gatewayId;
            this.shardCount = shardCount;
        }

        private void addSession(int shardIndex, String sessionId) {
            Set<String> sessions = shardSessions.computeIfAbsent(shardIndex, key -> new HashSet<>());
            sessions.add(sessionId);
        }

        private void removeSession(int shardIndex, String sessionId) {
            Set<String> sessions = shardSessions.get(shardIndex);
            if (sessions == null) {
                return;
            }
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                shardSessions.remove(shardIndex);
            }
        }

        private int activeShardCount() {
            return shardSessions.size();
        }

        private boolean isEmpty() {
            return shardSessions.isEmpty();
        }
    }
}
