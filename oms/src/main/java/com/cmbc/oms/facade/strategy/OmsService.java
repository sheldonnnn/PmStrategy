package com.cmbc.oms.facade.strategy;

import com.cmbc.oms.domain.order.model.NewOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @description 订单事件交互实现
 */
@Service
public class OmsService {

    private final List<ExecutionReportListener> listeners = new ArrayList<>();

    private final static String cancelReason = "策略主动调用";

    @Autowired
    private OrderCancelService orderCancelService;

    @Autowired
    private OrderUpstreamAppService orderUpstreamAppService;

    @Autowired
    private OrderCacheManager orderCacheManager;

    /**
     * 发送订单更新事件，通过Spring事件监听器解耦，防止循环依赖
     */
    @EventListener
    public void onExecutionReportEvent(ExecutionReportEvent event) {
        ExecutionReport executionReport = event.getExecutionReport();
        if (executionReport == null) {
            return;
        }

        String actionType = event.getActionType();
        switch (actionType) {
            case "ACK":
                listeners.forEach(listener -> listener.onAck(executionReport));
                break;
            case "REJECT":
                listeners.forEach(listener -> listener.onReject(executionReport));
                break;
            case "MATCH":
                listeners.forEach(listener -> listener.onMatch(executionReport));
                break;
            case "CANCEL":
                listeners.forEach(listener -> listener.onCancel(executionReport));
                break;
            default:
                break;
        }
    }

    /**
     * 接收积存金平盘策略订单
     */
    public StrategyOrderRes newOrder(StrategyOrder strategyOrder) {
        try {
            return orderUpstreamAppService.handleNewOrder(strategyOrder);
        } catch (Exception e) {
            return StrategyOrderRes.fail(e.getMessage());
        }
    }

    public void registerListener(ExecutionReportListener executionReportListener) {
        this.listeners.add(executionReportListener);
    }

    /**
     * 接收策略撤单请求（根据订单id）
     */
    public void cancelOrderByOrderId(String orderId) {
        orderCancelService.handleCancelOrder(orderId, null, cancelReason);
    }

    /**
     * 接收策略撤单请求（根据策略id）
     */
    public void cancelOrderByStrategyId(String strategyId) {
        orderCancelService.handleCancelOrder(null, strategyId, cancelReason);
    }

    /**
     * 返回该策略实例id下所有在途中的订单
     */
    public List<NewOrder> getTradingByStrategyId(String strategyId) {
        return orderCacheManager.getUnFinishedChildOrderListByStrategyId(strategyId);
    }
}
