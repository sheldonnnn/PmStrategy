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
public class GoldHedgeStrategyBean {
    // 合约品种
    private String symbol;
    // 买卖方向 0 :买 1: 卖
    private String side;
    //    // 委托数量
//    private BigDecimal orderQty;
//    // orderPrice 委托价格
//    private BigDecimal price;
    // 成交均价 (人民币/g)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal dealAvgPrice;
    // 成交数量
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal dealQty;
    // 成交重量 /境外盎司/境内千克
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal dealWeight;
    /**
     * 追单次数
     */
    private Integer chaseNumber;
    // 成交金额
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal dealAmount;
    /**
     * 价格偏离度
     */
    private String priceDeviation;
    /**
     * 浮动损益
     */
    private BigDecimal profitLoss;  //浮动损益
    // 汇率
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal fxRate;

}
