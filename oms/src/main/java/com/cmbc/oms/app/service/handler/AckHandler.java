package com.cmbc.oms.app.service.handler;

import com.cmbc.oms.app.service.AbstractExecutionHandler;
import com.cmbc.oms.domain.order.ability.service.ChildOrderUpdateService;
import com.cmbc.oms.domain.order.ability.service.OrderLifecycleService;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.enums.EventActionType;
import com.cmbc.oms.domain.position.ability.PositionIncrementalService;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author chendaqian
 * @date 2026/6/3
 * @time 18:56
 * @description
 */
@Component
public class AckHandler extends AbstractExecutionHandler {
    @Autowired
    private ChildOrderUpdateService childOrderUpdateService;
    @Autowired
    private PositionIncrementalService positionIncrementalService;
    @Autowired
    private OrderLifecycleService orderLifecycleService;

    @Override
    public boolean supports(String actionType) { return EventActionType.ACK.name().equals(actionType); }

    @Override
    protected String getTargetStatus(ExecutionReport report) {
        // 订单状态转换为新OMS字典
        String newStatus = orderLifecycleService.getNewOrderStatus(report);
        return newStatus;
    }

    @Override
    protected void doHandle(ExecutionReport report) { doAckBusiness(report); }

    // 处理 ACK 业务
    public void doAckBusiness(ExecutionReport report) {
        if(BusinessConstant.BUSINESS_TYPE_MANUAL.equals(report.getBusinessType())){
            // 兼容管理台手工订单(OMS迁移后直接去掉 todo)
            childOrderUpdateService.managerOrderPersist(report);
            if (BusinessConstant.DOMESTIC_TYPE_OUTER.equals(report.getDomesticType())) {
                positionIncrementalService.handleIncrementOrderEvent(report);
            }
        }else{
            // 1.状态变更
            orderLifecycleService.processOrderLifecycleForUpdate(report);
            // 2.持久化订单
            childOrderUpdateService.persist(report);
            // 3.设置超时撤单(境内)
            if(BusinessConstant.DOMESTIC_TYPE_INNER.equals(report.getDomesticType())){
                childOrderUpdateService.setExpiredTime(report.getOrderId());
            }
            // 4.境外贵金属延迟持仓冻结机制：正报时不扣减，收到正式 ACK 确认挂单后才执行持仓冻结
            if (BusinessConstant.DOMESTIC_TYPE_OUTER.equals(report.getDomesticType())) {
                positionIncrementalService.handleIncrementOrderEvent(report);
            }
            // 5.一阶段需要将java侧订单增加事件发送apama进行持久化与头寸，持仓变更，二阶段重构去掉即可 todo
            childOrderUpdateService.sendOrderUpdateToApama(report);
        }
    }
}
