package com.cmbc.strategy.service.instance;


import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.strategy.domain.model.market.Depth;
import com.cmbc.strategy.domain.model.order.OrderReport;

public interface IStrategy {
    // === 生命周期 ===
    void start();
    void pause();
    void resume();
    void stop(String reason);

    // === 事件回调 (由框架层驱动) ===
    void onMatch(ExecutionReport executionReport);                      // 行情驱动
    void onRtnOrder(ExecutionReport executionReport);
    void onOrderCancel(ExecutionReport executionReport);      // 订单回报驱动
    void onOrderRejected(ExecutionReport executionReport);     // 头寸变动驱动
    void onOtherEvent(ExecutionReport executionReport);
}
