package com.cmbc.oms.app.service.handler;

import com.cmbc.oms.app.service.AbstractExecutionHandler;
import com.cmbc.oms.domain.order.ability.service.ChildOrderUpdateService;
import com.cmbc.oms.domain.order.ability.service.OrderLifecycleService;
import com.cmbc.oms.domain.order.ability.service.OrderSyncManager;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.enums.EventActionType;
import com.cmbc.oms.domain.position.ability.PositionIncrementalService;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/**
 * @author chendaqian
 * @date 2026/6/3
 * @time 22:42
 * @description
 */
public class CancelHandler extends AbstractExecutionHandler {
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private ChildOrderUpdateService childOrderUpdateService;
    @Autowired
    private PositionIncrementalService positionIncrementalService;
    @Autowired
    private OrderLifecycleService orderLifecycleService;
    @Autowired
    private OrderSyncManager orderSyncManager;

    @Override
    public boolean supports(String actionType) { return EventActionType.CANCEL.name().equals(actionType); }

    @Override
    protected String getTargetStatus(ExecutionReport report) {
        // 这里处理状态映射 todo
        return report.getStatus();
    }

    @Override
    protected void doHandle(ExecutionReport report) {
        try{
            report.setCanceledQty(report.getLeavesQty());// 撤单数量
            if(BusinessConstant.BUSINESS_TYPE_MANUAL.equals(report.getBusinessType())){
                // 兼容管理台手工订单(OMS迁移后直接去掉 todo)
                childOrderUpdateService.managerOrderPersist(report);
                if (BusinessConstant.DOMESTIC_TYPE_OUTER.equals(report.getDomesticType())) {
                    // 境外手工订单确认做了冻结，撤销需求解冻
                    positionIncrementalService.handleIncrementOrderEvent(report);
                }
            }else{
                // 1.状态变更
                orderLifecycleService.processOrderLifecycleForUpdate(report);
                // 2.持久化订单(todo 需要跟状态变更一起事务更新)
                childOrderUpdateService.persist(report);
                // 2.1. 更新母单状态
                orderLifecycleService.updateParentOrderStatus(report.getStrategyOrderId(),report.getStatus());
                // 3.持仓更新
                if (BusinessConstant.DOMESTIC_TYPE_OUTER.equals(report.getDomesticType())) {
                    positionIncrementalService.handleIncrementOrderEvent(report);
                }
                // 4.一阶段需要将java侧订单增加事件发送apama进行持久化与头寸，持仓变更，二阶段重构去掉即可 todo
                childOrderUpdateService.sendOrderUpdateToApama( report);
            }
        }finally {
            // 2. 唤醒上游: 必须放在 finally 中, 确保业务报异常也不会导致上游死锁
            orderSyncManager.completeFuture(report.getOrderId(), report);
        }
    }
}
