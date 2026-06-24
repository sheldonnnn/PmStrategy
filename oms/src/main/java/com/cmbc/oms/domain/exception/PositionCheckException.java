package com.cmbc.oms.domain.exception;

import org.springframework.context.ApplicationContextException;

/**
 * @author chendaqian
 * @date 2026/2/28
 * @time 14:40
 * @description
 */
public class PositionCheckException extends ApplicationContextException {
    private final String orderId;
    private final String errorId;   // 状态
    private final String errorMessage; // 错误信息
    public PositionCheckException(String orderId, String errorId,String errorMessage) {
        super(String.format("订单校验失败，[订单编号：%s，状态：%s，错误信息：%s]", orderId, errorId, errorMessage));
        this.orderId = orderId;
        this.errorId = errorId;
        this.errorMessage = errorMessage;
    }

    public String getOrderId() { return orderId; }

    public String getErrorId() { return errorId; }

    public String getErrorMessage() { return errorMessage; }
}
