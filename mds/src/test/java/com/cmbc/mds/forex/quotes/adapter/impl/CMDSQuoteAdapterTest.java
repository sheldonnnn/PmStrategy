package com.cmbc.mds.forex.quotes.adapter.impl;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.constants.InterConstants;
import com.cmbc.mds.forex.quotes.dto.CmdsQuotePayload;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CMDSQuoteAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TestableCMDSQuoteAdapter adapter = new TestableCMDSQuoteAdapter();

    /**
     * 测试语义：验证 CMDS DTO 可以直接转换为 Depth，并保留报价、数量、交易模式和顶层扩展字段。
     * 关注重点：CMDS Adapter 不再依赖 JsonNode；字段读取必须来自 CmdsQuotePayload，且 symbol 规范化为业务统一格式。
     */
    @Test
    @DisplayName("CMDS-ADAPTER-01 DTO 行情应转换为标准 Depth")
    void convertsGradsPricesToDepth() throws Exception {
        CmdsQuotePayload payload = objectMapper.readValue("""
                {
                  "exnm": "USDCNY",
                  "runningNumber": "RN-001",
                  "quoteType": "ODM",
                  "prcd": "M0501",
                  "time": "20260603 10:15:30",
                  "gradsPrices": [
                    {
                      "level": "1",
                      "bid": "7.1000",
                      "bidSize": "1000000",
                      "ask": "7.2000",
                      "askSize": "2000000"
                    }
                  ]
                }
                """, CmdsQuotePayload.class);

        Depth depth = adapter.convert(payload, "CMDS", "CMDS");

        assertThat(depth).isNotNull();
        assertThat(depth.getQuoteId()).isEqualTo("RN-001");
        assertThat(depth.getSource()).isEqualTo("CMDS");
        assertThat(depth.getProvider()).isEqualTo("CMDS");
        assertThat(depth.getSymbol()).isEqualTo("USD/CNY");
        assertThat(depth.getBidPrices()).containsExactly(new BigDecimal("7.1000"));
        assertThat(depth.getBidQuantities()).containsExactly(new BigDecimal("1000000"));
        assertThat(depth.getAskPrices()).containsExactly(new BigDecimal("7.2000"));
        assertThat(depth.getAskQuantities()).containsExactly(new BigDecimal("2000000"));
        assertThat(depth.getExtraParams())
                .containsEntry(BaseConstants.SERVICE_NAME_KEY1, BaseConstants.SERVICE_NAME_CMDS)
                .containsEntry(InterConstants.EXTRA_KEY_VALUE_TRADE_MODE, BaseConstants.TRADE_MODE_ODM)
                .containsEntry("prcd", "M0501");
    }

    /**
     * 测试语义：验证 CMDS 报价档位中数值字段非法时，该侧报价被过滤；当 bid/ask 均无有效价格时返回 null。
     * 关注重点：非法数值不能写入价格/数量列表，避免 Clean 阶段接收到长度不匹配或不可解析的数据。
     */
    @Test
    @DisplayName("CMDS-ADAPTER-02 无有效买卖价时应丢弃")
    void skipsPayloadWithoutValidPrices() throws Exception {
        CmdsQuotePayload payload = objectMapper.readValue("""
                {
                  "exnm": "USDCNY",
                  "gradsPrices": [
                    {
                      "bid": "invalid",
                      "bidSize": "1000000"
                    }
                  ]
                }
                """, CmdsQuotePayload.class);

        assertThat(adapter.convert(payload, "CMDS", "CMDS")).isNull();
    }

    /**
     * 测试语义：验证 CMDS 交易对必须来自 exnm，不能从 key 等辅助字段猜测。
     * 关注重点：避免将非标准 key 误解析为 symbol，导致错误行情进入订阅和聚合链路。
     */
    @Test
    @DisplayName("CMDS-ADAPTER-03 缺少 exnm 时应丢弃")
    void skipsPayloadWithoutExnmEvenWhenKeyContainsSymbol() throws Exception {
        CmdsQuotePayload payload = objectMapper.readValue("""
                {
                  "key": "A_B_C_EURUSD",
                  "gradsPrices": [
                    {
                      "bid": "1.1000",
                      "bidSize": "1000000"
                    }
                  ]
                }
                """, CmdsQuotePayload.class);

        assertThat(adapter.convert(payload, "CMDS", "CMDS")).isNull();
    }

    private static class TestableCMDSQuoteAdapter extends CMDSQuoteAdapter {
        private Depth convert(CmdsQuotePayload payload, String source, String provider) {
            return super.convertToDepth(payload, source, provider);
        }
    }
}
