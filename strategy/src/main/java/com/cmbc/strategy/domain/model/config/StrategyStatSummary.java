package com.cmbc.strategy.domain.model.config;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author chenliyao
 * @date 2026/2/5
 * @time 9:31
 * @description
 */
@Data
public class StrategyStatSummary {
    private final String symbol;
    private final String userName;
    private final String strategyInstanceId;
    private BigDecimal price;               //最新委托价格
    private BigDecimal mktPrice;            //市场价
    private String side;
    private BigDecimal cumPendingQty; // 委托数量
    private BigDecimal cumPendingWeight;//
    private BigDecimal cumQty;              // 成交数量
    private BigDecimal cumWeight;
    
    private BigDecimal cumAmount;           // 成交金额
    private BigDecimal avgPrice;            // 成交平均价格

    /**
     * 境内外标识 (0/1)
     */
    private String domesticType;
    // USD/CNH price
    private BigDecimal fxRate;


    public StrategyStatSummary(String userName, String strategyInstanceId, String symbol) {
        this.cumPendingQty = BigDecimal.ZERO;
        this.cumPendingWeight = BigDecimal.ZERO;
        this.cumQty = BigDecimal.ZERO;
        this.cumWeight = BigDecimal.ZERO;
        this.cumAmount = BigDecimal.ZERO;
        this.userName = userName;
        this.strategyInstanceId = strategyInstanceId;
        this.symbol = symbol;
    }

    /**
     * 统一单位，将成交数量统一转换为克返回
     * @return
     */
    public BigDecimal convertToWeight(BigDecimal qty,BigDecimal unit) {
        if("1".equals(domesticType)){
            return qty.multiply(BaseConstant.OUNCE_GRAM_UNIT);
        }
        return qty.multiply(unit);
    }

    /**
     * 计算境内境外成交价格  // todo  暂时先在整理计算，后续如果oms有调整的时候需要直接取值即可
     * @return
     */
    public BigDecimal getLastAmount(BigDecimal ounceGramUnit) {
        if("0".equals(domesticType)){
            return this.lastPrice.multiply(lastQty).multiply(new BigDecimal(1000)); // 境内是千克，直接返回即可
        }
        // 境外算法
        return (lastQty).multiply(ounceGramUnit).multiply(this.lastPrice);
    }
}
