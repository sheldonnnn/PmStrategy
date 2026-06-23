package com.cmbc.strategy.engine.core;


import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.subscription.controller.StrategySubscriptionController;

import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.controller.dto.StrategyOrderRes;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;
import com.cmbc.oms.domain.exposure.model.HedgePositionSummary;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.infrastructure.util.OrderUtil;
import com.cmbc.strategy.domain.model.market.SubscribeRequest;

import com.cmbc.strategy.domain.model.config.StrategyConfig;
import com.cmbc.strategy.engine.context.StrategyContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public abstract class BaseStrategy<T extends StrategyConfig> implements IStrategy {

    @Resource
    protected StrategySubscriptionController strategySubscriptionController;

    protected final StrategyContext strategyContext;
    protected final String instanceId;
    protected final T config; // 泛型配置

    // 状态管理
    public BaseStrategy(T config, String instanceId, StrategyContext strategyContext) {
        this.instanceId = instanceId;
        this.config = config;
        this.strategyContext = strategyContext;
    }

    // ================== 通用能力封装 (API for Sub-classes) ==================

    protected ScheduledFuture<?> schedule(Runnable task, long delay) {
        if (strategyContext.getTaskScheduler() == null) {
            log.error("TaskScheduler is not initialized.");
            return null;
        }
        return strategyContext.getTaskScheduler().scheduleWithFixedDelay(task, Duration.ofMillis(delay));
    }

    public String getInstanceId() { return instanceId; }
    /**
     * SDK能力: 发送订单
     */
    protected StrategyOrderRes sendStrategyOrder(StrategyOrder strategyOrder) {
        if(strategyOrder.getOrderId() == null){
            strategyOrder.setOrderId(OrderUtil.generateStrategyOrderId());
        }
        strategyOrder.setInstanceId(instanceId);
        log.info("发送订单, {}", strategyOrder);
        return strategyContext.getOmsService().newOrder(strategyOrder);
    }
    /**
     * SDK能力: 撤销订单
     */
    protected void cancelOrder(String orderId) {
        strategyContext.getOmsService().cancelOrderByOrderId(orderId);
    }

    /**
     * SDK能力: 撤销本策略所有活跃订单
     */
    protected void cancelAllOrders() {
        try {
            strategyContext.getOmsService().cancelOrderByStrategyId(instanceId);
        }catch(Exception e){
            log.error("[{}] cancelAllOrders error!!!",instanceId,e);
        }
    }

    protected List<NewOrder> getPendingOrder(){
        return strategyContext.getOmsService().getTradingByStrategyId(this.instanceId);
    }

    /**
     * SDK能力: 订阅行情
     */
    protected void subscribe(List<SubscribeRequest> subscribeReq, String userId) {
        strategyContext.getMarketDataService().subscribe(subscribeReq, instanceId,userId);
    }

    protected PloyPrices getOnshorePloyPrice(String symbol) {
        PloyPrices ployPrices = strategyContext.getMarketDataService().getPloyPrice(symbol, "DIMPLE", "DIMPLE");
        return ployPrices;
    }

    protected PloyPrices getOffshorePloyPrice(String symbol, String exchIds, String counterParties) {
        PloyPrices ployPrices = strategyContext.getMarketDataService().getPloyPrice(symbol,exchIds,counterParties);
        return ployPrices;
    }


//    protected Depth getMarketDataSnapshot(String symbol, List<String> sources) {
//        return new Depth();
//    }

    protected HedgePositionSummary getHedgePositionSummary(){
        return strategyContext.getPositionService().getMgapPositionSummary();
    }

    protected PositionSnapshot getFolderPosition(String folderId){
        return strategyContext.getPositionService().getFolderPositionSummary(folderId);
    }

}
