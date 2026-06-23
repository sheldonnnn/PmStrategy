package com.cmbc.mds.forex.quotes.adapter;

import com.cmbc.mds.forex.engine.port.MarketDataQueueGateway;
import com.cmbc.mds.forex.quotes.QuoteRoutingContext;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.monitor.QuotePerformanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 适配器抽象基类。
 *
 * <p>负责通用流程：转换源对象、执行厂商扩展逻辑、写入清洗队列。
 */
public abstract class AbstractQuoteAdapter<T> implements QuoteAdapter<T> {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected MarketDataQueueGateway queueGateway;
    @Autowired
    protected QuotePerformanceService quotePerformanceService;

    @Override
    public void adaptAndHandle(T payload, String source, String provider) {
        adaptAndHandle(payload, QuoteRoutingContext.withoutSymbol(source, provider));
    }

    @Override
    public void adaptAndHandle(T payload, QuoteRoutingContext context) {
        Depth depth = convertToDepth(payload, context.source(), context.provider());
        if (depth == null) {
            return;
        }

        try {
            doProviderSpecificHandle(depth, payload);
        } catch (Exception e) {
            String sourceInfo = (payload != null) ? payload.getClass().getSimpleName() + ": " + payload : "null";
            log.error("[{}] 厂商特定逻辑处理异常, payload={}", depth.getProvider(), sourceInfo, e);
            return;
        }

        sendToDownstream(depth, context);
    }

    /**
     * 核心转换逻辑，由具体适配器实现。
     */
    protected abstract Depth convertToDepth(T payload, String source, String provider);

    /**
     * 厂商特定逻辑，默认不处理。
     */
    protected void doProviderSpecificHandle(Depth depth, T source) {
    }

    private void sendToDownstream(Depth depth, QuoteRoutingContext context) {
        String cleanId = context.cleanTopicKeyFor(depth);
        if (log.isDebugEnabled()) {
            log.debug("[Adapter] 推送至清洗队列: cleanId={}", cleanId);
        }
        // 这里是整个行情接收入口链路的终点，统一推入下游的高并发清洗队列 (MarketDataQueueGateway)
        queueGateway.pushToClean(cleanId, depth);
    }
}
