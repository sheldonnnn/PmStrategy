package com.cmbc.mds.ksd.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class KsdService {

    private static final Logger log = LoggerFactory.getLogger(KsdService.class);

    private final HttpClient httpClient;
    private final String gatewayBaseUrl;
    private final Duration requestTimeout;

    public KsdService(
            @Value("${ksd.gateway.control-url:http://127.0.0.1:19090}") String gatewayBaseUrl,
            @Value("${ksd.gateway.request-timeout-millis:3000}") long requestTimeoutMillis) {
        this.gatewayBaseUrl = trimTrailingSlash(gatewayBaseUrl);
        this.requestTimeout = Duration.ofMillis(requestTimeoutMillis);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.requestTimeout)
                .build();
    }

    public GatewayResponse status() {
        return get("/api/ksd/status");
    }

    public GatewayResponse start() {
        return post("/api/ksd/start", null);
    }

    public GatewayResponse reconnect() {
        return post("/api/ksd/reconnect", null);
    }

    public GatewayResponse logout(String userId) {
        if (userId == null || userId.isBlank()) {
            return post("/api/ksd/logout", null);
        }
        return post("/api/ksd/logout", Map.of("userID", userId));
    }

    public GatewayResponse stop() {
        return post("/api/ksd/stop", null);
    }

    private GatewayResponse get(String path) {
        HttpRequest request = HttpRequest.newBuilder(uri(path, null))
                .timeout(requestTimeout)
                .GET()
                .build();
        return send(request);
    }

    private GatewayResponse post(String path, Map<String, String> queryParams) {
        HttpRequest request = HttpRequest.newBuilder(uri(path, queryParams))
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return send(request);
    }

    private GatewayResponse send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new GatewayResponse(response.statusCode(), response.body());
        } catch (IOException e) {
            log.warn("[KsdGatewayRequest] Failed to call KsdGateway: {}", request.uri(), e);
            return new GatewayResponse(502, "{\"error\":\"KsdGateway unavailable\"}");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[KsdGatewayRequest] KsdGateway request interrupted. uri={}, timeout={}, thread={}",
                    request.uri(), requestTimeout, Thread.currentThread().getName(), e);
            return new GatewayResponse(503, "{\"error\":\"KsdGateway request interrupted\"}");
        }
    }

    private URI uri(String path, Map<String, String> queryParams) {
        StringBuilder builder = new StringBuilder(gatewayBaseUrl).append(path);
        if (queryParams != null && !queryParams.isEmpty()) {
            StringJoiner joiner = new StringJoiner("&");
            queryParams.forEach((key, value) ->
                    joiner.add(encode(key) + "=" + encode(value == null ? "" : value)));
            builder.append('?').append(joiner);
        }
        return URI.create(builder.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:19090";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public record GatewayResponse(int statusCode, String body) {
    }
}
