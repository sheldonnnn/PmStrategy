package com.cmbc.strategy.service.instance;


import com.cmbc.strategy.domain.model.market.PloyPrices;
import com.cmbc.strategy.domain.model.order.OrderRequest;
import com.cmbc.strategy.integration.IMarketDataService;
import com.cmbc.strategy.integration.IOrderService;
import com.cmbc.strategy.integration.IPositionService;
import com.cmbc.strategy.domain.model.market.Depth;
import com.cmbc.strategy.domain.model.order.NewOrder;
import com.cmbc.strategy.domain.model.config.StrategyConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public abstract class BaseStrategy<T extends StrategyConfig> implements IStrategy {

    protected IMarketDataService marketDataService;
    protected IOrderService orderService;
    protected IPositionService positionService;
    protected final String instanceId;
    protected final T config; // 泛型配置



    // 状态管理

    public BaseStrategy(T config,String instanceId) {
        this.instanceId = instanceId;
        this.config = config;
    }

    // ================== 通用能力封装 (API for Sub-classes) ==================

    /**
     * SDK能力: 发送订单
     */
    protected void newOrderSingle(NewOrder request) {
//        if (!status.get().canTrade()) {
//            log.warn("[{}] Strategy not running, reject order.");
//            return;
//        }
//        orderService().sendOrder(request);
    }

    protected void newOrderVwap(OrderRequest request) {
//        if (!status.get().canTrade()) {
//            log.warn("[{}] Strategy not running, reject order.");
//            return;
//        }
//        orderService().sendOrder(request);
    }

    /**
     * SDK能力: 撤销订单
     */
//    protected void cancelOrder(String orderId) {
//        orderService().cancelOrder(orderId);
//    }

    /**
     * SDK能力: 撤销本策略所有活跃订单  todo
     */
    protected void cancelAllOrders() {
//        orderService().cancelAll(instanceId);
    }

    /**
     * SDK能力: 订阅行情
     */
    protected void subscribe(List<String> symbols) {
//        marketDataService().subscribe(symbols);
    }
    protected PloyPrices getPloyDepth(String symbol, List<String> sources) {
        return new PloyPrices();
    }
    //todo
//    protected Depth getMarketDataSnapshot(String symbol, List<String> sources) {
//        return new Depth();
//    }


}
