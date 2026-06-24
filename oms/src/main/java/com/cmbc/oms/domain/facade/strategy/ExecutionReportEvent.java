package com.cmbc.oms.domain.facade.strategy;

import com.cmbc.oms.domain.order.model.ExecutionReport;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @author chendaqian
 * @date 2026/4/24
 * @time 19:52
 * @description
 */
@Getter
public class ExecutionReportEvent extends ApplicationEvent {
    private final ExecutionReport executionReport;
    private final String actionType;// 事件类型 ACK,REJECT,MATCH,CANCEL

    public ExecutionReportEvent(Object source, ExecutionReport executionReport, String actionType) {
        super(source);
        this.executionReport = executionReport;
        this.actionType = actionType;
    }
}
