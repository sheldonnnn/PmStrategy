package com.cmbc.strategy.domain.model.hedge;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 黄金对冲策略交易详情实体
 */
@Data
public class GoldHedgeStrategyBean {

    // 合约品种
    private String symbol;

    // 买卖方向 0:买 1:卖
    private String side;

    // 委托数量
    private BigDecimal orderQty;

    // 委托价格
    private BigDecimal price;

    // 成交均价 (人民币/g)
    private BigDecimal dealAvgPrice;

    // 成交数量
    private BigDecimal dealQty;

    // 成交重量 / 境外盎司 / 境内千克
    private BigDecimal dealWeight;

    /**
     * 追单次数
     */
    private Integer chaseNumber;

    // 成交金额
    private BigDecimal dealAmount;

    /**
     * 价格偏离度
     */
    private String priceDeviation;

    /**
     * 浮动损益
     */
    private BigDecimal profitLoss;

    // 汇率
    private BigDecimal fxRate;

}
