package com.cmbc.mds.forex.quotes.receiver.impl;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.quotes.dto.DimpleKsdQuoteEvent;
import com.cmbc.mds.forex.quotes.receiver.QuoteReceiver;
import com.cmbc.mds.ksd.cache.KsdStaticQuoteCacheService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
public class DimpleWsQuoteReceiver extends QuoteReceiver {

    private final KsdStaticQuoteCacheService ksdStaticQuoteCacheService;
    private final Object dimpleCaptureLock = new Object();

    @Value("${app.quote.dimple-message-capture.enabled:false}")
    private boolean dimpleMessageCaptureEnabled;

    @Value("${app.quote.dimple-message-capture.path:logs/dimple_messages_capture.log}")
    private String dimpleMessageCapturePath;

    public DimpleWsQuoteReceiver(KsdStaticQuoteCacheService ksdStaticQuoteCacheService) {
        this.ksdStaticQuoteCacheService = ksdStaticQuoteCacheService;
    }

    public void onGatewayQuote(DimpleKsdQuoteEvent event) {
        if (event == null) {
            return;
        }
        String source = hasText(event.getSource()) ? event.getSource() : BaseConstants.SERVICE_NAME_DIMPLE;
        String provider = hasText(event.getProvider()) ? event.getProvider() : BaseConstants.SERVICE_NAME_DIMPLE;
        String symbol = event.getSymbol();

        if (dimpleMessageCaptureEnabled) {
            captureMessageForTestReplay(event);
        }

        // 1. KSD 专属逻辑：将行情更新到本地静态缓存，供其他模块快速查阅
        ksdStaticQuoteCacheService.updateFromQuoteEvent(event);
        
        // 2. 调用父类公用方法 processQuote，进行前置过滤校验并向路由分发
        super.processQuote(event, source, provider, symbol);
    }

    public void onGatewayHeartbeat() {
        foreignBankConnectionService.refreshLastActive(BaseConstants.SERVICE_NAME_DIMPLE, true);
    }

    public void onGatewayDisconnected() {
        foreignBankConnectionService.refreshLastActive(BaseConstants.SERVICE_NAME_DIMPLE, false);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void captureMessageForTestReplay(DimpleKsdQuoteEvent event) {
        try {
            ObjectNode record = objectMapper.createObjectNode();
            record.put("captureTimeMillis", System.currentTimeMillis());
            record.set("event", objectMapper.valueToTree(event));

            Path logPath = Paths.get(dimpleMessageCapturePath);
            Path parent = logPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
            synchronized (dimpleCaptureLock) {
                Files.writeString(
                        logPath,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            log.error("保存 Dimple 行情消息到本地文件失败", e);
        }
    }
}
