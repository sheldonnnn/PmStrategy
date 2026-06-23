package com.cmbc.mds.forex.quotes.adapter.impl;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.GradsPrice;
import com.cmbc.mds.forex.quotes.dto.MQTranserBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaseMQTranserAdapterTest {

    private final TestableAdapter adapter = new TestableAdapter();

    /**
     * 测试语义：验证 bid 价格非法时只过滤 bid，ask 侧合法报价仍应保留下来。
     * 关注重点：单侧异常不能导致整笔行情丢弃；quoteId 应回退到 ask 报价 ID；无效 bid 的扩展字段不能写入。
     */
    @Test
    @DisplayName("BASE-ADAPTER-01 bid 非法时保留合法 ask")
    void keepsValidAskWhenBidPriceIsInvalid() {
        GradsPrice level = new GradsPrice();
        level.setBid("invalid");
        level.setBidSize("1000000");
        level.setBidCurrency("USD");
        level.setBidSq("BID-1");
        level.setAsk("7.2000");
        level.setAskSize("2000000");
        level.setAskCurrency("CNY");
        level.setAskSq("ASK-1");

        Depth depth = adapter.convert(quote(level), "UBS", "UBS");

        assertThat(depth).isNotNull();
        assertThat(depth.getBidPrices()).isEmpty();
        assertThat(depth.getBidQuantities()).isEmpty();
        assertThat(depth.getAskPrices()).containsExactly(new BigDecimal("7.2000"));
        assertThat(depth.getAskQuantities()).containsExactly(new BigDecimal("2000000"));
        assertThat(depth.getQuoteId()).isEqualTo("ASK-1");
        assertThat(depth.getExtraParams())
                .doesNotContainKey(BaseConstants.KEY_BID_PREFIX + "1" + BaseConstants.KEY_CURRENCY_SUFFIX)
                .containsEntry(BaseConstants.KEY_ASK_PREFIX + "1" + BaseConstants.KEY_CURRENCY_SUFFIX, "CNY");
    }

    /**
     * 测试语义：验证某一侧缺少数量时，该侧报价不写入 Depth，另一侧合法报价不受影响。
     * 关注重点：价格列表和数量列表必须始终成对增长，避免 Clean 阶段因长度不一致丢弃行情。
     */
    @Test
    @DisplayName("BASE-ADAPTER-02 缺少数量的一侧应被过滤")
    void ignoresSideWhenQuantityIsMissing() {
        GradsPrice level = new GradsPrice();
        level.setBid("7.1000");
        level.setBidSize("1000000");
        level.setBidSq("BID-1");
        level.setAsk("7.2000");
        level.setAskSq("ASK-1");

        Depth depth = adapter.convert(quote(level), "UBS", "UBS");

        assertThat(depth).isNotNull();
        assertThat(depth.getBidPrices()).containsExactly(new BigDecimal("7.1000"));
        assertThat(depth.getBidQuantities()).containsExactly(new BigDecimal("1000000"));
        assertThat(depth.getAskPrices()).isEmpty();
        assertThat(depth.getAskQuantities()).isEmpty();
        assertThat(depth.getQuoteId()).isEqualTo("BID-1");
    }

    /**
     * 测试语义：验证 bid/ask 两侧均无法形成合法价格数量对时，Adapter 返回 null。
     * 关注重点：无有效价格的行情不能进入 clean 队列，避免后续队列事件和清洗对象分配。
     */
    @Test
    @DisplayName("BASE-ADAPTER-03 双侧均无效时返回 null")
    void returnsNullWhenBothSidesAreInvalid() {
        GradsPrice level = new GradsPrice();
        level.setBid("invalid");
        level.setBidSize("1000000");
        level.setAsk("7.2000");

        assertThat(adapter.convert(quote(level), "UBS", "UBS")).isNull();
    }

    /**
     * 测试语义：验证多档报价中混合合法/非法档位时，输出的价格列表和数量列表仍保持同长度。
     * 关注重点：后续性能优化不能为了减少分配而破坏 bidPrices/bidQuantities、askPrices/askQuantities 的下标对应关系。
     */
    @Test
    @DisplayName("BASE-ADAPTER-04 价格和数量列表必须保持成对")
    void alwaysBuildsMatchingPriceAndQuantityLists() {
        GradsPrice first = new GradsPrice();
        first.setBid("7.1000");
        first.setBidSize("1000000");
        first.setAsk("invalid");
        first.setAskSize("1000000");

        GradsPrice second = new GradsPrice();
        second.setBid("7.0900");
        second.setAsk("7.2100");
        second.setAskSize("2000000");

        Depth depth = adapter.convert(quote(first, second), "UBS", "UBS");

        assertThat(depth).isNotNull();
        assertThat(depth.getBidPrices()).hasSameSizeAs(depth.getBidQuantities());
        assertThat(depth.getAskPrices()).hasSameSizeAs(depth.getAskQuantities());
        assertThat(depth.getBidPrices()).containsExactly(new BigDecimal("7.1000"));
        assertThat(depth.getAskPrices()).containsExactly(new BigDecimal("7.2100"));
    }

    private static MQTranserBean quote(GradsPrice... levels) {
        MQTranserBean bean = new MQTranserBean();
        bean.setExnm("EURUSD");
        bean.setServiceId("UBS");
        bean.setGradsPriceList(List.of(levels));
        return bean;
    }

    private static class TestableAdapter extends BaseMQTranserAdapter {
        private Depth convert(MQTranserBean bean, String source, String provider) {
            return super.convertToDepth(bean, source, provider);
        }
    }
}
