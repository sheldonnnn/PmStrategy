package com.cmbc.strategy.domain.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 策略成交统计摘要
 */
@Data
public class StrategyStatSummary {

    private final String symbol;
    private final String userName;
    private final String strategyInstanceId;

    private BigDecimal price;            // 最新委托价格
    private BigDecimal mktPrice;         // 市场价
    private String side;                 // 买卖方向

    private BigDecimal cumPendingQty;    // 委托数量
    private BigDecimal cumPendingWeight; // 委托重量
    private BigDecimal cumQty;           // 成交数量
    private BigDecimal cumWeight;        // 成交重量

    private BigDecimal cumAmount;        // 成交金额
    private BigDecimal avgPrice;         // 成交平均价格

    /**
     * 境内外标识 (0/1)
     */
    private String domesticType;

    // USD/CNH price
    private BigDecimal fxRate;

    /**
     * 构造函数：初始化汇总统计，将数值字段初始化为 ZERO
     */
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
     * 统一单位：将成交数量统一转换为克返回
     *
     * @param qty  数量
     * @param unit 单位换算比例
     * @return 转换后的重量
     */
    public BigDecimal convertToWeight(BigDecimal qty, BigDecimal unit) {
        // 如果是境外标识（假设"1"代表境外/盎司），则使用常量的盎司转克比例
        if ("1".equals(domesticType)) {
            // 这里对应图片中注释掉的逻辑或预留逻辑
            // return qty.multiply(BaseConstant.OUNCE_GRAM_UNIT);
        }
        // 默认根据传入的 unit 进行乘法转换
        return qty.multiply(unit);
    }
}
