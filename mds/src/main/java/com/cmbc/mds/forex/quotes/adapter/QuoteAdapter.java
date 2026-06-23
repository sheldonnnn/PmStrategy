package com.cmbc.mds.forex.quotes.adapter;

import com.cmbc.mds.forex.quotes.QuoteRoutingContext;

/**
 * 行情适配器接口
 * @param <T> 输入的源数据类型 (例如 MQTranserBean, DimpleKsdQuoteEvent 等)
 */
public interface QuoteAdapter<T> {
    /**
     * 适配并处理行情
     * @param payload 源数据对象
     */
    void adaptAndHandle(T payload, String source, String provider);

    /**
     * 使用 Receiver 预先计算好的路由上下文处理行情。
     */
    default void adaptAndHandle(T payload, QuoteRoutingContext context) {
        adaptAndHandle(payload, context.source(), context.provider());
    }
}
