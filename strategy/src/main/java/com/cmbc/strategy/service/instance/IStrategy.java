package com.cmbc.strategy.service.instance;


import com.cmbc.strategy.domain.model.market.Depth;
import com.cmbc.strategy.domain.model.order.OrderReport;

public interface IStrategy {
    // === 生命周期 ===
    void start();
    void pause();
    void resume();
    void stop();

    // === 事件回调 (由框架层驱动) ===
    void onDepth(Depth depth);                      // 行情驱动
    void onOrderReport(OrderReport report);      // 订单回报驱动
    void onPositionUpdate(String accountId);     // 头寸变动驱动
}
