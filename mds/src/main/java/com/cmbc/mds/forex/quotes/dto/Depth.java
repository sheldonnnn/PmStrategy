package com.cmbc.mds.forex.quotes.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Depth {
    private String quoteId; // 报价id
    private String symbol; // 币种
    private String source; // 源 FXALL UBS
    private String provider; // 交易对手 UBS GS JPMC
    private String serviceName; // 行情源服务名 UBS-FIX GS-FIX HSBC-FIX FXALL-FIX
    private List<BigDecimal> bidPrices; // 买价
    private List<BigDecimal> midPrices; // 中间价
    private List<BigDecimal> askPrices; // 卖价

    private List<BigDecimal> bidQuantities; // 买量
    private List<BigDecimal> askQuantities; // 卖量

    private Long transactionTime; // 报价时间
    private Long sendingTime; // 发送时间
    private Long createTime; // 创建时间

    private Map<String,String> extraParams; // 额外参数
}
