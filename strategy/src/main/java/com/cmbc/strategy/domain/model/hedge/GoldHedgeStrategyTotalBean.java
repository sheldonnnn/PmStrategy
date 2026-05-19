package com.cmbc.strategy.domain.model.hedge;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GoldHedgeStrategyTotalBean {

    //成交总重量
    private BigDecimal dealTotalQty;
    private BigDecimal dealTotalPrice;

}
