package com.cmbc.oms.app.service.handler;

import com.cmbc.oms.app.service.AbstractExecutionHandler;
import com.cmbc.oms.domain.exposure.service.QuantPositionManager;
import com.cmbc.oms.domain.order.ability.service.ChildOrderUpdateService;
import com.cmbc.oms.domain.order.ability.service.OrderLifecycleService;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.enums.EventActionType;
import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import com.cmbc.oms.domain.position.ability.PositionIncrementalService;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author chendaqian
 * @date 2026/6/3
 * @time 22:40
 * @description
 */
@Slf4j
@Component
public class RejectHandler extends AbstractExecutionHandler {
    @Autowired
    private ChildOrderUpdateService childOrderUpdateService;
    @Autowired
    private PositionIncrementalService positionIncrementalService;
    @Autowired
    private OrderLifecycleService orderLifecycleService;
    @Autowired
    private QuantPositionManager quantPositionManager;

    @Override
    public boolean supports(String actionType) { return EventActionType.REJECT.name().equals(actionType); }

    @Override
    protected String getTargetStatus(ExecutionReport report) {
        return OrderStatus.FAILED.getStatusCode();
    }

    @Override
    protected void doHandle(ExecutionReport report) {
        if(BusinessConstant.BUSINESS_TYPE_MANUAL.equals(report.getBusinessType())){
            // 兼容管理台手工订单(OMS迁移后直接去掉 todo)
            childOrderUpdateService.managerOrderPersist(report);
            // 通知头寸/资金更新
            quantPositionManager.onExecutionReport(report);
        }else{
            // 推送前端展示
            childOrderUpdateService.pushMsgToWeb(report,report.getStatusMsg(), 2);
            // 1.状态变更
            orderLifecycleService.processOrderLifecycleForUpdate(report);
            // 2.持久化订单
            childOrderUpdateService.persist(report);
            // 3.更新母单状态
            orderLifecycleService.updateParentOrderStatus(report.getStrategyOrderId(),report.getStatus());
            // 4.通知头寸/资金更新
            //quantPositionManager.onExecutionReport(report);
            positionIncrementalService.handleIncrementOrderEvent(report);
            // 5.一阶段需要将java侧订单增加事件发送apama进行持久化与头寸，持仓变更，二阶段重构去掉即可
            childOrderUpdateService.sendOrderUpdateToApama( report);
        }
    }
}
