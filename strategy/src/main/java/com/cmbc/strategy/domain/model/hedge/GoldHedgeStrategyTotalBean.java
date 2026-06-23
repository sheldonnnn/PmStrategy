package com.cmbc.strategy.domain.model.hedge;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 查询积存金积存金策略模版实体
 *
 * @Author: zm
 * @Date: Created on 20190808 14:56.
 */
@Data
public class GoldHedgeStrategyTotalBean {
    // 成交总重量
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal dealTotalQty;
    // 成交总金额
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal dealTotalAmt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal profitTotalLoss;  //总浮动损益
}
