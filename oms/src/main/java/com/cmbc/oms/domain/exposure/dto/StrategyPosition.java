package com.cmbc.oms.domain.exposure.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StrategyPosition {

    private BigDecimal mgapNewPosition;
    private BigDecimal hedgedNetPosition;
    private BigDecimal frozenNetPosition;
    private String updateTime;


}
