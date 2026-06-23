package com.cmbc.strategy.engine.core;

import com.cmbc.oms.domain.order.model.ExecutionReport;

public interface IStrategy {
    // === 生命周期 ===
    void start();
    void pause();
    void resume();
    void stop(String reason);

    // === 事件回调 (由策略管理层驱动) ===
    void onMatch(ExecutionReport executionReport);
    void onRtnOrder(ExecutionReport executionReport);     //委托
    void onOrderCancel(ExecutionReport executionReport);
    void onOrderRejected(ExecutionReport executionReport);
    void onOtherEvent(ExecutionReport executionReport);
//    void onDepth(Depth depth);                          // 行情驱动
//    void onPositionUpdate(String accountId);            // 头寸变动驱动

}
