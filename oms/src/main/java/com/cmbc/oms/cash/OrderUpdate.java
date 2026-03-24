package com.cmbc.oms.cash;

import lombok.Data;

import java.math.BigDecimal;

/**
 * OMS 推送的订单成交、状态变更事件 DTO
 */
@Data
public class OrderUpdate {

    /**
     * 策略实例 ID
     */
    private String instanceId;

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 交易方向: BUY, SELL
     */
    private String side;

    /**
     * 订单状态: FILLED, PARTIALLY_FILLED, CANCELED, REJECTED 等
     */
    private String status;

    /**
     * 单次推送的增量成交数量 (Incremental/Last Filled Qty)
     */
    private BigDecimal dealQty;

    /**
     * 订单总委托数量
     */
    private BigDecimal orderQty;

    /**
     * 成交序号/流水号 (也可用于去重)
     */
    private String matchNo;

    private String orderId;
    private String businessType;
    private String tagCode;
    private String tagName;
    private BigDecimal lastPrice;

    private String domesticType;

}
