package com.cmbc.oms.domain.exposure.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class HedgePositionSummary {

    private BigDecimal mgapHedgedPosition;
    private BigDecimal hedgedNetPosition;
    private BigDecimal frozenNetPosition;
    // 积存金客盘头寸
    private BigDecimal mgapClientPosition;
    private BigDecimal mgapClientPrice;     //积存金客盘报价
    
    // 积存金客盘头寸更新时间
    private String updateTime;
}
