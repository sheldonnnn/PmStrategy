package com.cmbc.oms.app.service.handler;

import com.cmbc.oms.app.service.AbstractExecutionHandler;
import com.cmbc.oms.domain.order.ability.service.ChildOrderUpdateService;
import com.cmbc.oms.domain.order.ability.service.OrderLifecycleService;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.enums.EventActionType;
import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import com.cmbc.oms.domain.position.ability.PositionIncrementalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author chendaqian
 * @date 2026/6/3
 * @time 22:25
 * @description
 */
@Component
@Slf4j
public class InternalRejectHandler extends AbstractExecutionHandler {
    @Autowired
    private PositionIncrementalService positionIncrementalService;
    @Autowired
    private ChildOrderUpdateService childOrderUpdateService;
    @Autowired
    private OrderLifecycleService orderLifecycleService;

    @Override
    public boolean supports(String actionType) { return EventActionType.IN_REJECT.name().equals(actionType); }

    @Override
    protected String getTargetStatus(ExecutionReport report) {
        return OrderStatus.INTERNAL_FAILED.getStatusCode();
    }

    @Override
    protected void doHandle(ExecutionReport report) {
        report.setStatusMsg("[系统内阻/网关断连异常]: " + report.getStatusMsg());
        // 推送前端展示
        childOrderUpdateService.pushMsgToWeb(report,report.getStatusMsg(), 2);
        // 1.状态变更
        orderLifecycleService.processOrderLifecycleForUpdate(report);
        // 2.持久化订单
        childOrderUpdateService.persist(report);
        // 2.1. 更新母单状态
        orderLifecycleService.updateParentOrderStatus(report.getStrategyOrderId(),report.getStatus());
        // 3.资产风控自修复解冻
        if (report.isPositionFrozen()) {
            positionIncrementalService.handleIncrementOrderEvent(report);
        }
    }
}
