package com.cmbc.mds.forex.quotes;

import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;

/**
 * 行情接收链路的轻量路由上下文。
 *
 * <p>Receiver 层完成 source/provider/symbol 与 cleanTopicKey 的一次性计算，
 * Adapter 和下游入队逻辑优先复用该结果，避免热路径重复拼接 topic key。
 */
public final class QuoteRoutingContext {

    private final String source;
    private final String provider;
    private final String symbol;
    private final String cleanTopicKey;

    private QuoteRoutingContext(String source, String provider, String symbol, String cleanTopicKey) {
        this.source = source;
        this.provider = provider;
        this.symbol = symbol;
        this.cleanTopicKey = cleanTopicKey;
    }

    public static QuoteRoutingContext of(String source, String provider, String symbol) {
        return new QuoteRoutingContext(source, provider, symbol,
                MarketDataTopic.buildTopicKey(source, provider, symbol));
    }

    public static QuoteRoutingContext withoutSymbol(String source, String provider) {
        return new QuoteRoutingContext(source, provider, null, null);
    }

    public String source() {
        return source;
    }

    public String provider() {
        return provider;
    }

    public String symbol() {
        return symbol;
    }

    public String cleanTopicKey() {
        return cleanTopicKey;
    }

    /**
     * 当 Adapter 未改变 source/provider/symbol 时复用入口计算出的 cleanTopicKey；
     * 若 Adapter 改写了这些字段，则按最终 Depth 重新生成，保证路由正确性优先。
     */
    public String cleanTopicKeyFor(Depth depth) {
        if (depth == null) {
            return cleanTopicKey;
        }
        if (cleanTopicKey != null
                && equalsValue(source, depth.getSource())
                && equalsValue(provider, depth.getProvider())
                && equalsValue(symbol, depth.getSymbol())) {
            return cleanTopicKey;
        }
        return MarketDataTopic.buildTopicKey(depth.getSource(), depth.getProvider(), depth.getSymbol());
    }

    private boolean equalsValue(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
