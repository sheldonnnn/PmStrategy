package com.cmbc.mds.forex.quotes.receiver;

import com.cmbc.mds.forex.common.utils.ServiceNameUtils;
import com.cmbc.mds.forex.common.utils.SymbolUtils;
import com.cmbc.mds.forex.provider.service.ForeignBankConnectionService;
import com.cmbc.mds.forex.quotes.QuoteRoutingContext;
import com.cmbc.mds.forex.quotes.adapter.QuoteAdapterRouter;
import com.cmbc.mds.forex.quotes.dto.HeartbeatResponse;
import com.cmbc.mds.forex.quotes.dto.MQTranserBean;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 抽象行情接收器。
 *
 * <p>协议层负责识别报文类型、处理心跳、完成必要的对象绑定；业务层负责订阅前置校验和 Adapter 路由。
 */
public abstract class QuoteReceiver {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected QuoteAdapterRouter quoteAdapterRouter;

    @Autowired
    private SubscriptionCoreService subscriptionCoreService;

    @Autowired
    protected ForeignBankConnectionService foreignBankConnectionService;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * 接收已经完成对象绑定的 MQTranserBean。
     *
     * <p>该入口用于 MQ 文本主路径，避免先 readTree 再 treeToValue 的重复反序列化。
     */
    protected void receiveAndDispatch(MQTranserBean quote, String source, String provider, String defaultSymbol) {
        if (quote == null) {
            return;
        }

        // 1. 根据 messageType 进行基础验证
        String messageType = trimToNull(quote.getMessageType());
        String tpfg = trimToNull(quote.getTpfg());

        if (!"MQTranserBean".equalsIgnoreCase(messageType)) {
            logDiscardedMessage(quote, source, provider, messageType, tpfg, "unsupported messageType");
            return;
        }

        if (tpfg == null) {
            logDiscardedMessage(quote, source, provider, messageType, null, "missing tpfg");
            return;
        }

        if ("SPOT".equalsIgnoreCase(tpfg)) {
            dispatchSpotQuote(quote, source, provider, defaultSymbol);
        } else if ("FWD".equalsIgnoreCase(tpfg)) {
            logDiscardedMessage(quote, source, provider, messageType, tpfg, "unsupported forward quote");
        } else {
            logDiscardedMessage(quote, source, provider, messageType, tpfg, "unsupported tpfg");
        }
    }

    private void dispatchSpotQuote(MQTranserBean quote, String source, String provider, String defaultSymbol) {
        if (quote.getServiceId() == null) {
            quote.setServiceId(source);
        }

        String symbol = (quote.getSymbol() != null && !quote.getSymbol().isEmpty())
                ? quote.getSymbol()
                : quote.getExnm();
        symbol = SymbolUtils.formatSymbol(symbol);
        if (symbol == null || symbol.isEmpty()) {
            symbol = defaultSymbol;
        }

        processQuote(quote, source, provider, symbol);
    }

    private String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        return trimToNull(value.textValue());
    }

    private void logDiscardedMessage(
            JsonNode jsonNode,
            String source,
            String provider,
            String messageType,
            String tpfg,
            String reason) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "[行情丢弃] reason={}, source={}, provider={}, messageType={}, tpfg={}, serviceId={}, symbol={}, exnm={}, nameid={}, sequ={}",
                reason,
                source,
                provider,
                messageType,
                tpfg,
                textOrNull(jsonNode, "serviceId"),
                textOrNull(jsonNode, "symbol"),
                textOrNull(jsonNode, "exnm"),
                textOrNull(jsonNode, "nameid"),
                textOrNull(jsonNode, "sequ"));
    }

    private void logDiscardedMessage(
            MQTranserBean quote,
            String source,
            String provider,
            String messageType,
            String tpfg,
            String reason) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "[行情丢弃] reason={}, source={}, provider={}, messageType={}, tpfg={}, serviceId={}, symbol={}, exnm={}, nameid={}, sequ={}",
                reason,
                source,
                provider,
                messageType,
                tpfg,
                quote.getServiceId(),
                quote.getSymbol(),
                quote.getExnm(),
                quote.getNameid(),
                quote.getSequ());
    }

    private void handleHeartbeat(JsonNode jsonNode, String source) {
        try {
            HeartbeatResponse heartbeat = objectMapper.treeToValue(jsonNode, HeartbeatResponse.class);
            handleHeartbeat(heartbeat, source);
        } catch (Exception e) {
            log.error("心跳处理异常: source={}, jsonNode={}", source, jsonNode, e);
        }
    }

    /**
     * 处理已经完成对象绑定的心跳报文。
     */
    protected void handleHeartbeat(HeartbeatResponse heartbeat, String source) {
        if (heartbeat == null) {
            return;
        }
        boolean isConnected = Boolean.TRUE.equals(heartbeat.getConnected());
        String transport = heartbeat.getTransport() != null ? heartbeat.getTransport() : "UNKNOWN";

        if (source == null || source.trim().isEmpty()) {
            source = ServiceNameUtils.getPrefixTransportName(transport);
        }
        foreignBankConnectionService.refreshLastActive(source, isConnected);
    }

    /**
     * 核心业务处理：刷新连接状态、执行订阅前置校验、路由到 Adapter。
     */
    protected void processQuote(Object payload, String source, String provider, String symbol) {
        // 1. 更新通道最后活跃时间（存活检测）
        foreignBankConnectionService.refreshLastActive(source, true);

        // 2. 订阅前置过滤：如果没有下游订阅该组合，则直接丢弃报文，避免后续的格式转换浪费 CPU
        QuoteRoutingContext routingContext = QuoteRoutingContext.of(source, provider, symbol);
        boolean hasCleanSubscriber = subscriptionCoreService.hasSubscribers(routingContext.cleanTopicKey());
        if (!hasCleanSubscriber) {
            if (log.isDebugEnabled()) {
                log.debug("[前置过滤] 丢弃未订阅行情 source={}, symbol={}", source, symbol);
            }
            return;
        }

        // 3. 将负载数据及上下文交给路由器，分发到对应源的 Adapter
        quoteAdapterRouter.route(payload, routingContext);
    }
}
