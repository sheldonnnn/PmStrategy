package com.cmbc.oms.domain.exposure.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class HedgePositionSummary {
    // 积存金系统同步过来的平盘数据（非量化平盘）
    private BigDecimal mgapHedgedPosition;
    private BigDecimal hedgedNetPosition;
    private BigDecimal frozenNetPosition;
    // 积存金客盘头寸
    private BigDecimal mgapClientPosition;
    // 积存金客盘头寸更新时间
    private String updateTime;
}
