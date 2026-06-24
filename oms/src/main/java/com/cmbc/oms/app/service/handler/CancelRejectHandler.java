package com.cmbc.oms.app.service.handler;

import com.cmbc.oms.app.service.AbstractExecutionHandler;
import com.cmbc.oms.domain.order.ability.service.ChildOrderUpdateService;
import com.cmbc.oms.domain.order.ability.service.OrderLifecycleService;
import com.cmbc.oms.domain.order.ability.service.OrderSyncManager;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.enums.EventActionType;
import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import com.cmbc.oms.infrastructure.cache.OrderCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author chendaqian
 * @date 2026/6/3
 * @time 22:26
 * @description
 */
@Component
@Slf4j
public class CancelRejectHandler extends AbstractExecutionHandler {
    @Autowired
    private OrderCacheManager orderCacheManager;
    @Autowired
    private ChildOrderUpdateService childOrderUpdateService;
    @Autowired
    private OrderLifecycleService orderLifecycleService;
    @Autowired
    private OrderSyncManager orderSyncManager;

    @Override
    public boolean supports(String actionType) { return EventActionType.CANCEL_REJECT.name().equals(actionType); }

    @Override
    protected String getTargetStatus(ExecutionReport report) {
        // 动态状态对齐 (Reconciliation)：根据本地缓存中确切的成交数据，决定回滚去向
        String currentStatus = report.getOldStatus();

        if(OrderStatus.FILLED.getStatusCode().equals(currentStatus) ) {
            log.info("订单 {} 撤单失败，系统侦测到已全成，对齐修正终态为 FILLED", report.getOrderId());
            return OrderStatus.FILLED.getStatusCode();
        }else if(OrderStatus.PARTIAL_FILL.getStatusCode().equals(currentStatus)){
            log.info("订单 {} 撤单失败，系统侦测到部分成交，状态原地对齐为 PARTIAL_FILL", report.getOrderId());
            return OrderStatus.PARTIAL_FILL.getStatusCode();
        }else if(OrderStatus.CONFIRMED.getStatusCode().equals(currentStatus)){
            log.info("订单 {} 撤单失败，系统侦测到已挂单，状态原地对齐为 CONFIRMED", report.getOrderId());
            return OrderStatus.CONFIRMED.getStatusCode();
        }
        log.error("订单 {} 撤单失败，系统侦测到未知状态，状态原地对齐为 原状态", report.getOrderId());
        return currentStatus;
    }

    @Override
    protected void doHandle(ExecutionReport report) {
        try{
            log.error("订单 {} 撤单被拒逻辑对齐完毕，拒单底层理由：{}", report.getOrderId(), report.getStatusMsg());
            // 推送前端展示
            childOrderUpdateService.pushMsgToWeb(report,report.getStatusMsg(), 2);
            // 如果没有发送撤单，交易所主动返回的撤单拒单需要处理，实际撤单成功 todo
            if(!orderLifecycleService.hasCancelOrder(report.getOrderId())){
                //
                //
            }
        }finally {
            // 2. 唤醒上游: 必须放在 finally 中, 确保业务报异常也不会导致上游死锁
            orderSyncManager.completeFuture(report.getOrderId(), report);
        }
    }
}
