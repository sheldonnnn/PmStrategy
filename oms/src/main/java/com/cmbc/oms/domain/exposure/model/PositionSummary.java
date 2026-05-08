package com.cmbc.oms.domain.exposure.model;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 头寸汇总模型
 */
@Data
public class PositionSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private String symbol;             // 交易品种

    private BigDecimal netPosition;    // 净敞口头寸

    private BigDecimal netAmount;      // 净敞口金额

    private BigDecimal avgPrice;       // 均价

    private BigDecimal fxRate;         // 最新汇率

    private BigDecimal profitAndLoss;  // 浮动损益

    private String updateTime;         // 更新时间

}