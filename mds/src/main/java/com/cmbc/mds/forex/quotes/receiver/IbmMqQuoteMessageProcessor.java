package com.cmbc.mds.forex.quotes.receiver;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.constants.MetricConstants;
import com.cmbc.mds.forex.common.utils.ServiceNameUtils;
import com.cmbc.mds.forex.common.utils.SymbolUtils;
import com.cmbc.mds.forex.quotes.dto.GradsPrice;
import com.cmbc.mds.forex.quotes.dto.HeartbeatResponse;
import com.cmbc.mds.forex.quotes.dto.MQTranserBean;
import com.cmbc.mds.monitor.QuotePerformanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * IBM MQ 文本行情处理器。
 *
 * <p>真实 IBM MQ 接收器和 HTTP 手工测试入口共用该处理器，确保测试接口尽可能模拟 MQ 通路。
 */
@Component
public class IbmMqQuoteMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(IbmMqQuoteMessageProcessor.class);
    private final ObjectMapper objectMapper;
    private final QuotePerformanceService quotePerformanceService;

    public IbmMqQuoteMessageProcessor(ObjectMapper objectMapper, QuotePerformanceService quotePerformanceService) {
        this.objectMapper = objectMapper;
        this.quotePerformanceService = quotePerformanceService;
    }

    /**
     * 处理 IBM MQ 文本报文。
     *
     * @param expectedProvider 日志标识，真实 MQ 使用 IBMMQ，HTTP 回放可传入 HTTP_IBMMQ_REPLAY
     * @param jsonMessage      MQ 原始文本报文
     * @param receiver         调用方 Receiver，用于复用其心跳和行情分发入口
     * @param serviceIdOverride 可选的测试覆盖值；为空时完全使用报文字段
     */
    public void process(String expectedProvider,
            String jsonMessage,
            QuoteReceiver receiver,
            String serviceIdOverride) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Processing MQ Msg: {}", jsonMessage);
            }
            quotePerformanceService.start(MetricConstants.IBMMQ_QUOTE_RECEIVER);

            // 1. 将文本直接反序列化为通用的 MQTranserBean
            MQTranserBean quote = objectMapper.readValue(jsonMessage, MQTranserBean.class);
            applyServiceIdOverride(quote, serviceIdOverride);

            if (BaseConstants.MESSAGE_TYPE_HEARTBEATRESPONSE.equalsIgnoreCase(quote.getMessageType())) {
                HeartbeatResponse heartbeat = new HeartbeatResponse();
                heartbeat.setConnected(quote.getConnected());
                heartbeat.setTransport(quote.getTransport());
                
                receiver.handleHeartbeat(heartbeat, resolveSource(quote));
                return;
            }

            if (BaseConstants.MESSAGE_TYPE_MQTRANSERBEAN.equalsIgnoreCase(quote.getMessageType())) {
                String source = resolveSource(quote);
                String provider = resolveProvider(source, quote);
                String symbol = SymbolUtils.formatSymbol(firstNonBlank(quote.getSymbol(), quote.getExnm()));

                receiver.receiveAndDispatch(quote, source, provider, symbol);
                return;
            }

            if (BaseConstants.MESSAGE_TYPE_SUBSCRIBEREJECTDEPTH.equalsIgnoreCase(quote.getMessageType())) {
                log.info("[{}] Received SubscribeRejectDepth message, ignored for now. serviceId={}, symbol={}, exnm={}, nameid={}, sequ={}",
                        expectedProvider,
                        quote.getServiceId(),
                        quote.getSymbol(),
                        quote.getExnm(),
                        quote.getNameid(),
                        quote.getSequ());
                return;
            } else {
                log.warn("[{}] Unsupported IBM MQ messageType={}, serviceId={}, symbol={}, exnm={}, nameid={}, sequ={}",
                        expectedProvider,
                        quote.getMessageType(),
                        quote.getServiceId(),
                        quote.getSymbol(),
                        quote.getExnm(),
                        quote.getNameid(),
                        quote.getSequ());
                return;
            }
        } catch (Exception e) {
            log.error("[{}] IBM MQ 消息处理失败, jsonMessage={}", expectedProvider, jsonMessage, e);
        } finally {
            quotePerformanceService.end(MetricConstants.IBMMQ_QUOTE_RECEIVER);
        }
    }

    public void process(String expectedProvider, String jsonMessage, QuoteReceiver receiver) {
        process(expectedProvider, jsonMessage, receiver, null);
    }

    private void applyServiceIdOverride(MQTranserBean quote, String serviceIdOverride) {
        if (serviceIdOverride != null && !serviceIdOverride.trim().isEmpty()) {
            quote.setServiceId(serviceIdOverride.trim());
        }
    }

    private String resolveSource(MQTranserBean quote) {
        return ServiceNameUtils.getPrefixServiceName(quote.getServiceId());
    }

    private String resolveProvider(String source, MQTranserBean quote) {
        if (!BaseConstants.PROVIDER_FXALL.equals(source)) {
            return source;
        }
        GradsPrice firstPrice = firstPrice(quote);
        if (firstPrice == null) {
            log.warn("[{}] 从 FXALL 报文提取 originator 失败", source);
            return null;
        }
        String provider = firstNonBlank(firstPrice.getAskEntryOriginator(), firstPrice.getBidEntryOriginator());
        if (provider == null || provider.trim().isEmpty()) {
            log.warn("[{}] 从 FXALL 报文提取 originator 失败", source);
            return null;
        }
        return provider;
    }

    private GradsPrice firstPrice(MQTranserBean quote) {
        if (quote.getGradsPriceList() == null || quote.getGradsPriceList().isEmpty()) {
            return null;
        }
        return quote.getGradsPriceList().get(0);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return null;
    }
}
