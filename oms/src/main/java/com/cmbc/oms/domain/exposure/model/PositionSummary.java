package com.cmbc.oms.domain.exposure.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PositionSummary implements Serializable {
    private String symbol;  //交易品种
    private BigDecimal netPosition;  //净敞口头寸
    private BigDecimal netPositionUSD; // 美元敞口
    private BigDecimal netPositionXAU; // XAU头寸
    private BigDecimal netAmount;   //净敞口金额
    private BigDecimal avgPrice;   //均价
    private BigDecimal fxRate;    //最新汇率
    private BigDecimal profitAndLoss;   //浮动损益
    private String updateTime;   //更新时间
}
