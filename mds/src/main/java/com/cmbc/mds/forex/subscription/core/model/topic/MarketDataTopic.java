package com.cmbc.mds.forex.subscription.core.model.topic;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * [重构] 原始/清洗行情主题
 * <p>
 * 对应原来的 DataLevel.CLEAN。
 * 该 Topic 代表单一流动性提供商(Provider)的特定货币对(Symbol)行情。
 * <p>
 * 职责:
 * 1. 用于 Source Adapter 的 Fast-fail 校验。
 * 2. 触发 TopicActiveEvent 后，通知 CleanService 开启对应的数据清洗通道。
 */
@Getter
@Setter
public class MarketDataTopic implements SubscriptionTopic {

    // 标识为清洗后的原始数据
    public static final String TYPE = "MARKET_DATA_CLEAN";

    // source和provider共同构成一个完整的行情源：UBS.UBS FXALL.UBS FXALL.JPMC
    private final String source; // 价格源 UBS FXALL
    private final String provider;// 交易对手 UBS JPMC
    private final String symbol; // 交易币种，外资行使用斜杠格式：EUR/USD USD/JPY；贵金属使用原始格式：AU9999

    /**
     * 构建清洗行情主题
     * 
     * @param source   行情源 (例如: "UBS", "FXALL")
     * @param provider 提供商 (例如: "UBS", "HSBC")
     * @param symbol   货币对，外资行使用斜杠格式 (例如: "EUR/USD")
     */
    public MarketDataTopic(String source, String provider, String symbol) {
        // 简单的非空校验，防止生成无效Key
        this.source = Objects.requireNonNull(source, "Source cannot be null");
        this.provider = Objects.requireNonNull(provider, "Provider cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
    }

    public static String buildTopicKey(String ssource, String provider, String symbol) {
        // 格式: MD:CLEAN:{SOURCE}.{PROVIDER}:{SYMBOL}，source 与 provider 用 . 分隔，与 MergeDataTopic 的配对格式保持一致
        return BaseConstants.MARKET_DATA_CLEAN_KEY_PREFIX + ssource + "." + provider + ":" + symbol;
    }

    /**
     * 获取 TopicKey
     * 格式: MD:CLEAN:{SOURCE}.{PROVIDER}:{SYMBOL}
     * 例如: MD:CLEAN:UBS.UBS:EUR/USD
     */
    @Override
    public String getTopicKey() {
        // source 与 provider 用 . 分隔，symbol 前用 : 分隔
        return BaseConstants.MARKET_DATA_CLEAN_KEY_PREFIX + source + "." + provider + ":" + symbol;
    }

    @Override
    public String getTopicType() {
        return TYPE;
    }

    /**
     * 重写 equals
     * 包含 source, provider 和 symbol，保证主题的唯一性判断。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MarketDataTopic that = (MarketDataTopic) o;
        return Objects.equals(source, that.source) &&
                Objects.equals(provider, that.provider) &&
                Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, provider, symbol);
    }

    @Override
    public String toString() {
        return "MarketDataTopic{" +
                "source='" + source + '\'' +
                ", provider='" + provider + '\'' +
                ", symbol='" + symbol + '\'' +
                '}';
    }
}