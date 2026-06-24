package com.cmbc.oms.domain.order.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author chendaqian
 * @date 2026/1/27
 * @time 16:50
 * @description 子订单实体
 */
@Data
public class NewOrder {

    // 母单id
    private String parentOrderId;
    /**
     * 订单ID (orderId)
     */
    private String orderId;

    /**
     * 合约 (symbol)
     */
    private String symbol;

    /**
     * 价格 (price)
     */
    private BigDecimal price;

    /**
     * 买卖方向 (side)
     */
    private String side;

    /**
     * 订单类型 (type)
     */
    private String type;

    /**
     * 数量 (quantity)
     */
    private BigDecimal orderQty;

    // 订单剩余数量
    private BigDecimal leavesQty;
    // 累计成交金额
    private BigDecimal dealAmount;
    // 成交均价
    private BigDecimal dealAvgPrice;
    // 净敞口
    private BigDecimal netPosition;
    // 订单委托时间
    private LocalDateTime orderTime; // 订单委托时间

    public NewOrder() {
        this.dealAmount = BigDecimal.ZERO;
        this.dealAvgPrice = BigDecimal.ZERO;
        this.netPosition = BigDecimal.ZERO;
        this.isExceptionFlag = false;
    }

    /**
     * 交易市场 (exchange)
     */
    private String exchange;

    /**
     * 交易员 (ownerId)
     */
    private String ownerId;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 客户号
     */
    private String localOrderNo;

    /**
     * 席位号
     */
    private String memberId;

    /**
     * 头寸标签名称
     */
    private String positionTagName;

    /**
     * 投保标志
     */
    private String shFlag;

    /**
     * 策略ID
     */
    private String strategyID;

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 策略实例ID
     */
    private String strategyInstanceID;

    /**
     * 每手乘数
     */
    private Integer unit;

    private String quoteEntryId;

    /**
     * 过期时间
     */
    private String traderNo;
    // 订单状态
    private String status;

    /**
     * 步长
     */
    private Integer stepPosition;

    private String openingClosingType;
    private String tradePurpose;

    private boolean isExceptionFlag; // 是否异常订单 默认为false

    /**
     * 区分境内外是否乘合约乘数
     * @return
     */
    @Deprecated
    public BigDecimal getLeavesWeight(BigDecimal unit) {
        return null;
    }
}
