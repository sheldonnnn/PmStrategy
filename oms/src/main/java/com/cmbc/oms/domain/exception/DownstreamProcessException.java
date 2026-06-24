package com.cmbc.oms.domain.exception;

import com.cmbc.oms.domain.order.model.ExecutionReport;
import lombok.Data;

/**
 * @author chendaqian
 * @date 2026/5/25
 * @time 18:16
 * @description 下行处理异常
 */
@Data
public class DownstreamProcessException extends RuntimeException{
    private final String actionType;
    private final ExecutionReport report; // 现场快照

    public DownstreamProcessException(String message,Throwable cause,String actionType, ExecutionReport report) {
        super(String.format("[%s]订单 %s 处理失败: %s",report.getOrderId(),actionType, message), cause);
        this.actionType = actionType;
        this.report = report;
    }

    // 构造器
    public DownstreamProcessException(String message,String actionType, ExecutionReport report) {
        this(message,null,actionType,report);
    }
}
