package com.cmbc.oms.app.service.handler;

import com.cmbc.oms.app.service.AbstractExecutionHandler;
import com.cmbc.oms.domain.exposure.service.QuantPositionManager;
import com.cmbc.oms.domain.order.ability.service.ChildOrderUpdateService;
import com.cmbc.oms.domain.order.ability.service.OrderLifecycleService;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.enums.EventActionType;
import com.cmbc.oms.domain.order.model.enums.OldOrderStatusEnum;
import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import com.cmbc.oms.domain.position.ability.PositionIncrementalService;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

/**
 * @author chendaqian
 * @date 2026/6/3
 * @time 19:11
 * @description
 */
@Component
@Slf4j
public class MatchHandler extends AbstractExecutionHandler {
    @Autowired
    private ChildOrderUpdateService childOrderUpdateService;
    @Autowired
    private PositionIncrementalService positionIncrementalService;
    @Autowired
    private OrderLifecycleService orderLifecycleService;
    @Autowired
    private QuantPositionManager quantPositionManager;
    @Autowired
    private AckHandler ackHandler;

    @Override
    public boolean supports(String actionType) { return EventActionType.MATCH.name().equals(actionType); }

    @Override
    protected String getTargetStatus(ExecutionReport report) {
        // 判断成交进度
        if (OldOrderStatusEnum.ENTRUST_DEAL_ALL.getStatusCode().equals( report.getApamaStatus())) {
            return OrderStatus.FILLED.getStatusCode();
        }
        return OrderStatus.PARTIAL_FILL.getStatusCode();
    }

    @Override
    public void handle(ExecutionReport report) {
        // 乱序介入：如果由于网络抖动，订单处于 NEW 状态就直接收到了 MATCH 回报
        if (OrderStatus.CREATED.getStatusCode().equals(report.getOldStatus()) ||
                OrderStatus.NEW.getStatusCode().equals(report.getOldStatus())) {
            log.warn("触发乱序看门狗！订单 {} 尚未收到 ACK，直接收到成交回报，标记触发 MockAck 补偿", report.getOrderId());
            report.setSequenceDisorder(true);
        }
        super.handle(report);
    }

    @Override
    protected void doHandle(ExecutionReport report) {
        if(BusinessConstant.BUSINESS_TYPE_MANUAL.equals(report.getBusinessType())){
            // 兼容管理台手工订单(OMS迁移后直接去掉 todo)
            childOrderUpdateService.managerOrderPersist(report);
            // 通知头寸/资金更新
            quantPositionManager.onExecutionReport(report);
            positionIncrementalService.handleIncrementOrderEvent(report);
        }else{
            if (report.isSequenceDisorder()) {
                // 补偿机制：伪造一个 ACK 注入系统，完善生命周期，并补偿境外持仓冻结
                ExecutionReport mockAckReport = createMockAckReport(report);
                ackHandler.doAckBusiness(mockAckReport);
            }
            // 计算成交均价并增量更新本地成交缓存
            childOrderUpdateService.calculateMatchVolumeAndPrices(report);
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

    private ExecutionReport createMockAckReport(ExecutionReport fillReport) {
        // 基于 Fill 回报，构建一个等效的 ACK (通常状态置为 CONFIRMED)
        ExecutionReport ack = SerializationUtils.clone(fillReport);
        ack.setStatus(OrderStatus.CONFIRMED.getStatusCode()); // 强制标记为确认状态
        return ack;
    }
}
