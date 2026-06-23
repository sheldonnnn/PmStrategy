package com.cmbc.mds.forex.quotes.receiver.impl;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.constants.MetricConstants;
import com.cmbc.mds.forex.common.utils.SymbolUtils;
import com.cmbc.mds.forex.quotes.dto.CmdsQuotePayload;
import com.cmbc.mds.forex.quotes.receiver.JmsMessageBodyExtractor;
import com.cmbc.mds.forex.quotes.receiver.QuoteReceiver;
import com.cmbc.mds.monitor.QuotePerformanceService;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * CMDS 行情接收器。
 * <p>
 * CMDS 报文不携带可靠的 source/provider 信息，来源身份由专属队列绑定确定。
 */
@Component
public class CmdsMqQuoteReceiver extends QuoteReceiver {

    @Autowired
    private QuotePerformanceService quotePerformanceService;

    @Autowired
    private JmsMessageBodyExtractor messageBodyExtractor;

    @JmsListener(destination = "${ibm.mq.queue.cmds}")
    public void onMessage(Message message) {
        try {
            // 1. 从 JMS 消息中提取文本内容。
            String jsonBody = messageBodyExtractor.extract(message);
            if (jsonBody == null || jsonBody.isBlank()) {
                log.debug("[CMDS] Received empty message, skipped. ID={}", message.getJMSMessageID());
                return;
            }
            // 2. 交由 CMDS 专用处理逻辑完成 DTO 绑定、预检和路由。
            processMessage(jsonBody);
        } catch (JMSException e) {
            log.error("[CMDS] JMS message parsing failed", e);
        } catch (Exception e) {
            log.error("[CMDS] Message processing failed", e);
        }
    }

    void processMessage(String jsonMessage) {
        try {
            quotePerformanceService.start(MetricConstants.IBMMQ_QUOTE_RECEIVER);

            // 直接绑定为 CMDS DTO，后续接收预检和 Adapter 转换共用同一对象，避免 JsonNode 中间树。
            CmdsQuotePayload payload = objectMapper.readValue(jsonMessage, CmdsQuotePayload.class);
            if (!hasGradsPrices(payload)) {
                log.warn("[CMDS] Quote missing gradsPrices, skipped. payload={}", payload);
                return;
            }

            String symbol = normalizeSymbol(extractSymbol(payload));
            if (symbol == null || symbol.isEmpty()) {
                log.warn("[CMDS] Quote missing exnm, skipped. payload={}", payload);
                return;
            }

            processQuote(
                    payload,
                    BaseConstants.SERVICE_NAME_CMDS,
                    BaseConstants.SERVICE_NAME_CMDS,
                    symbol);
        } catch (Exception e) {
            log.error("[CMDS] Quote parsing failed, jsonMessage={}", jsonMessage, e);
        } finally {
            quotePerformanceService.end(MetricConstants.IBMMQ_QUOTE_RECEIVER);
        }
    }

    private boolean hasGradsPrices(CmdsQuotePayload payload) {
        return payload != null && payload.getGradsPrices() != null && !payload.getGradsPrices().isEmpty();
    }

    private String extractSymbol(CmdsQuotePayload payload) {
        return payload == null ? null : text(payload.getExnm());
    }

    private String normalizeSymbol(String rawSymbol) {
        if (!hasText(rawSymbol)) {
            return null;
        }
        return SymbolUtils.formatSymbol(rawSymbol);
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
