package com.cmbc.strategy.service.instance;

import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.domain.exposure.dto.StrategyPosition;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;
import com.cmbc.strategy.domain.model.market.PloyPrices;
import com.cmbc.strategy.domain.model.market.SubscribeRequest;
import com.cmbc.strategy.service.StrategyContext;
import com.cmbc.strategy.util.OrderUtil;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * BaseStrategy 抽象基类
 * 泛型 T 需继承 StrategyConfig
 */
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

    // ============================== 通用能力封装 (API for Sub-classes) ==============================

    /**
     * 调度任务
     */
    protected ScheduledFuture<?> schedule(Runnable task, long delay) {
        if (strategyContext.getTaskScheduler() == null) {
            log.error("TaskScheduler is not initialized.");
            return null;
        }
        return strategyContext.getTaskScheduler().scheduleWithFixedDelay(task, Duration.ofMillis(delay));
    }

    public String getInstanceId() {
        return instanceId;
    }

    /**
     * SDK能力：发送订单
     */
    protected void sendStrategyOrder(StrategyOrder strategyOrder) {
        if (strategyOrder.getOrderId() == null) {
            strategyOrder.setOrderId(OrderUtil.generateStrategyOrderId());
        }
        strategyOrder.setInstanceId(instanceId);
        strategyOrder.setOrderType("LIMIT"); //todo
        // 此处对应图片底部逻辑，调用相关服务发送订单
        strategyContext.getOmsService().newOrder(strategyOrder);
    }

    protected void cancelAllOrders() {
        try{
            strategyContext.getOmsService().cancelOrderByStrategyId(instanceId);
        }catch (Exception e){
            log.error("cancelAllOrders error: {}", e.getMessage());
        }

    }
    /**
     * SDK能力：订阅行情
     */
    protected void subscribe(List<SubscribeRequest> subscribeReq, String userId) {
        strategyContext.getMarketDataService().subscribe(subscribeReq, instanceId, userId);
    }

    protected PloyPrices getOnshorePloyPrice(String symbol) {
        PloyPrices ployPrices = strategyContext.getMarketDataService().getPloyPrice(symbol, "DIMPLE", "DIMPLE");
        return ployPrices;
    }

    protected PloyPrices getOffshorePloyPrice(String symbol, String exchIds, String counterParties) {
        PloyPrices ployPrices = strategyContext.getMarketDataService().getPloyPrice(symbol, exchIds, counterParties);
        return ployPrices;
    }

//    protected Depth getMarketDataSnapshot(String symbol, List<String> sources) {
//        // 图片显示此处返回 new Depth() 或类似逻辑
//        return new Depth();
//    }

    protected StrategyPosition getClientPosition() {
        return strategyContext.getPositionService().getMgapPositionSummary();
    }

    protected PositionSnapshot getFolderPosition(String folderId) {
        return strategyContext.getPositionService().getFolderPositionSummary(folderId);
    }
}