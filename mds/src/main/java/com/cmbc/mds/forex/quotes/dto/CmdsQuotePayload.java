package com.cmbc.mds.forex.quotes.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * CMDS 原始行情报文 DTO。
 * <p>
 * 接收端直接反序列化成该对象，Adapter 后续只读取已绑定字段，避免 readTree/JsonNode
 * 带来的重复节点对象创建和字段查找开销。
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CmdsQuotePayload {

    private String exnm;
    private String runningNumber;
    private String quoteType;
    private String prcd;
    private String time;
    private String tpfg;
    private String term;
    private String cxfg;
    private String key;
    private List<CmdsGradsPrice> gradsPrices;

    /**
     * CMDS 单档买卖报价。
     */
    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CmdsGradsPrice {
        private String level;
        private String bid;
        private String bidSize;
        private String bidDate;
        private String bidTime;
        private String ask;
        private String askSize;
        private String askDate;
        private String askTime;
    }
}
