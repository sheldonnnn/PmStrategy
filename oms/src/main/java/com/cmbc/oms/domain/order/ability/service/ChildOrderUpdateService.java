package com.cmbc.oms.domain.order.ability.service;

import com.cmbc.oms.domain.event.MgapIncrementalOrderEvent;
import com.cmbc.oms.domain.exception.ExceptionNotificationService;
import com.cmbc.oms.domain.facade.apama.SendEventToApama;
import com.cmbc.oms.domain.order.ability.factory.ExecutionReportMapper;
import com.cmbc.oms.domain.order.ability.factory.OrderUpdateFactory;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.domain.order.model.entity.PmOrderEntity;
import com.cmbc.oms.domain.order.model.entity.PmTradeEntity;
import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import com.cmbc.oms.infrastructure.cache.OrderCacheManager;
import com.cmbc.oms.infrastructure.dao.PmOrderMapper;
import com.cmbc.oms.infrastructure.dao.PmTradeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author chendaqian
 * @date 2026/2/11
 * @time 10:52
 * @description 为解决 子单状态补全，抽离更新类与初始化拆开
 */
@Service
public class ChildOrderUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(ChildOrderUpdateService.class);
    @Autowired
    private OrderCacheManager orderCacheManager;
    @Autowired
    private OrderLifecycleService orderLifecycleService;
    @Autowired
    private OrderCancelService orderCancelService;
    @Autowired
    private PmOrderMapper orderMapper;
    @Autowired
    private PmTradeMapper tradeMapper;
    @Autowired
    private ExecutionReportMapper executionReportMapper;
    @Autowired
    private SendEventToApama sendEventToApama;
    @Autowired
    private OrderUpdateFactory factory;
    @Autowired
    private ExceptionNotificationService exceptionPushService;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    
    // 常量定义
    private static final long DELAY_TIME = 5; // 默认延迟时间为5秒，可根据实际需要调整 todo

    // 判断订单是否为部分成交或完全成交状态
    private boolean isPartialOrComplete(String status) {
        return OrderStatus.PARTIAL_FILL.getStatusCode().equals(status)
                || OrderStatus.FILLED.getStatusCode().equals(status);
    }

    // 获取订单超时时间
    private long getExpireTime(String orderId) {
        // 从缓存中获取未完成订单的信息
        Map<String, NewOrder> unFinishedChildOrder = orderCacheManager.getUnFinishedChildOrder();
        NewOrder newOrder = unFinishedChildOrder.get(orderId);
        return Objects.isNull(newOrder.getExpiredTime()) ? 0 : Long.parseLong(newOrder.getExpiredTime().toString());
    }

    // 调度订单超时撤单
    private void scheduleOrderTimeoutCancellation(String orderId, long expireTime) {
        // 开启定时任务，检查状态如果是已挂起就撤单
        try {
            scheduledExecutorService.schedule(() -> {
                if (orderCacheManager.isUnFinished(orderId) && !orderCacheManager.getChildOrder(orderId).isExceptionFlag()) {
                    orderCancelService.cancelOrder(orderId, "超时自动触发撤单", false);
                } else {
                    logger.info("订单已为终态，无需进行超时撤单");
                }
            }, expireTime, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            logger.error("Failed to schedule order timeout cancellation for order " + orderId + ": " + e.getMessage());
        }
    }

    public void persistOrder(String strategyOrderId, NewOrder newOrder) {
        // 持久化订单到数据库
        PmOrderEntity orderEntity = new PmOrderEntity();
        orderEntity.setOrderId(newOrder.getOrderId());
        orderEntity.setSymbol(newOrder.getSymbol());
        orderEntity.setPrice(newOrder.getPrice());
        orderEntity.setOrdType(newOrder.getType());
        orderEntity.setSide(newOrder.getSide());
        orderEntity.setOrderQty(newOrder.getOrderQty());
        orderEntity.setLeavesQty(newOrder.getOrderQty());
        orderEntity.setStrategyOrderId(strategyOrderId);
        orderEntity.setUserName(newOrder.getUserName());
        orderEntity.setCurrency(newOrder.getCurrency());
        orderEntity.setBusinessType(newOrder.getBusinessType());
        orderEntity.setInventoryType(newOrder.getInventoryType());
        orderEntity.setDomesticType(newOrder.getDomesticType());
        orderEntity.setOpenFlag(newOrder.getEoFlag());
        orderEntity.setShFlag(newOrder.getShFlag());
        orderEntity.setNetPosition(newOrder.getNetPosition()); // 添加敞口头寸

        orderEntity.setStrategyId(newOrder.getStrategyInstanceID());
        orderEntity.setInstanceId(newOrder.getStrategyInstanceID());
        orderEntity.setMarketSegmentId(newOrder.getMarketSegmentId());
        orderEntity.setStatus(String.valueOf(OrderStatus.NEW.getStatusCode()));
        orderEntity.setStatusMsg("创建订单");
        orderEntity.setCumQty(BigDecimal.ZERO);
        orderEntity.setAvgPx(BigDecimal.ZERO);
        orderEntity.setTagCode(newOrder.getPositionTagCode());
        orderEntity.setTagName(newOrder.getPositionTagName());
        orderEntity.setTimeInForce(newOrder.getTimeInForce());
        orderEntity.setExchId(newOrder.getExchCode());
        orderEntity.setCounterParty(newOrder.getCounterParty());
        orderEntity.setMarketSegmentId(newOrder.getMarketSegmentId());
        orderEntity.setSecurityType(newOrder.getSecurityType());
        orderEntity.setMemberId(newOrder.getMemberId());
        orderEntity.setClientId(newOrder.getClientId());
        orderEntity.setTraderNo(newOrder.getTraderNo());
        orderEntity.setTradePurpose(newOrder.getTradePurpose());
        orderEntity.setOrderTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        orderEntity.setOrderDate(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        orderMapper.insertNewOrder(orderEntity);
    }

    public void updateExceptionFlag(String orderId, boolean isExceptionFlag) {
        PmOrderEntity orderEntity = new PmOrderEntity();
        orderEntity.setOrderId(orderId);
        if(isExceptionFlag){
            orderEntity.setIsException("1"); //异常订单
        }else{
            orderEntity.setIsException("0");
        }
        try{
            orderMapper.updateOrder(orderEntity);
        }catch (Exception e){
            logger.error("Error update exception flag: " + orderEntity, e);
        }
    }

    public void persist(ExecutionReport executionReport) {
        try {
            logger.info("Persist order, executionReport: {}", executionReport);
            // 更新数据库
            PmOrderEntity orderEntity = executionReportMapper.toOrderEntity(executionReport);
            orderMapper.updateOrder(orderEntity);
            if (isPartialOrComplete(executionReport.getStatus())) {
                PmTradeEntity tradeEntity = executionReportMapper.toTradeEntity(executionReport);
                tradeMapper.insertTrade(tradeEntity);
            }
        } catch (Exception e) {
            //event.setCumAmt(order.getDealAmount().add(multiplyAmt));
            //order.setDealAmount(event.getLastAmt());//子单成交金额
            // 计算订单非未成交金额
            // BigDecimal buyAvgPrice;
            // BigDecimal orderBuyAvgPrice;
            // if (oreq.getCumQty().compareTo(BigDecimal.ZERO) != 0 && order.getLeavesQty().compareTo(order.getOrderQty()) != 0) {
            //     buyAvgPrice = oreq.getCumAmount().divide(executeQty.multiply(unit), scale: 2, RoundingMode.HALF_UP);
            //     orderBuyAvgPrice = order.getDealAmount().divide(orderCumQty.multiply(unit), scale: 2, RoundingMode.HALF_UP);
            // } else {
            //     buyAvgPrice = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            //     orderBuyAvgPrice = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            //     logger.warn("订单买方未分成交金额重新计算>>订单编号: {}, 累计成交数量: {}", event.getOrderId(), oreq.getCumQty());
            // }
            // oreq.setAvgPrice(buyAvgPrice); // 保留两位小数
            // //event.setAvgPx(orderBuyAvgPrice);
            // order.setAvgPrice(event.getAvgPx());
            // // 更换缓存中的母单,子单信息
            // orderCacheManager.cacheParentRequest(event.getStrategyOrderId(), oreq);
            // orderCacheManager.cacheUnFinishedChildOrder(order.getOrderId(), order);
        //} catch (Exception e) {
            logger.error("计算成交配对数据异常 - 订单号: {}", executionReport.getOrderId(), e);
        }
    }

    // 计算非未成交数据
    private void calculateSellExecutionData(ExecutionReport event) {
        // // 从缓存中获取母单，子单信息
        // StrategyOrder oreq = getOrderRequestFromCache(event.getOrderId());
        // NewOrder order = orderCacheManager.getChildOrder(event.getOrderId());
        
        // if (oreq == null || order == null) {
        //     return;
        // }
        // try {
        //     // 累计非未成交量
        //     BigDecimal executeQty = oreq.getCumQty().add(event.getLastQty());
        //     oreq.setCumQty(executeQty);
        //     //BigDecimal orderLeft = (order.getLeaveQty() == null ? order.getOrderQty() : order.getLeaveQty()).subtract(event.getLastQty());
        //     //order.setLeavesQty(event.getLeavesQty());//子单剩余成交量
        //     //BigDecimal orderCumQty = event.getCumQty();
        //     //event.setCumQty(orderCumQty);//子单累计成交量
        //     // 计算非未成交数量
        //     BigDecimal subtract = oreq.getQty().subtract(oreq.getCumQty());
        //     oreq.setLeavesQty(subtract);
        //     //event.setLeavesQty(orderLeft);

        //     // 计算非未成交比例
        //     if (oreq.getQty().compareTo(BigDecimal.ZERO) != 0) {
        //         BigDecimal sellRate = oreq.getCumQty().divide(oreq.getQty(), 2, RoundingMode.HALF_UP);
        //         oreq.setDealRate(sellRate); //保留两位小数
        //     } else {
        //         logger.warn("订单卖方非未成交计算异常>>订单编号: {}, 委托数量: {}", event.getOrderId(), oreq.getQty());
        //         oreq.setDealRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        //     }
        //     // 计算非未成交金额
        //     BigDecimal lastPriceExecuted = event.getLastPrice();
        //     BigDecimal lastQtyExecuted = event.getLastQty();
        //     BigDecimal unit = event.getUnit();
        //     BigDecimal multiplyAmt = lastPriceExecuted.multiply(lastQtyExecuted).multiply(unit);
        //     // 境内需要进行对金额计算乘放
        //     oreq.setCumAmount(oreq.getCumAmount().add(multiplyAmt));
        //     //event.setCumAmt(order.getDealAmount().add(multiplyAmt));
        //     //order.setDealAmount(event.getCumAmt());//子单成交金额
        //     // 计算订单非未成交均价
        //     BigDecimal sellAvgPrice;
        //     //BigDecimal orderSellAvgPrice;
        //     if (oreq.getCumQty().compareTo(BigDecimal.ZERO) != 0 && order.getLeavesQty().compareTo(order.getOrderQty()) != 0) {
        //         sellAvgPrice = oreq.getCumAmount().divide(executeQty.multiply(unit), 2, RoundingMode.HALF_UP);
        //         //orderSellAvgPrice = order.getDealAmount().divide(orderCumQty.multiply(unit), scale: 2, RoundingMode.HALF_UP);
        //     } else {
        //         sellAvgPrice = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        //         //orderSellAvgPrice = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        //         logger.warn("订单卖方非未成交金额计算异常>>订单编号: {}, 累计成交数量: {}", event.getOrderId(), oreq.getCumQty());
        //     }
        //     oreq.setAvgPrice(sellAvgPrice); // 保留两位小数
        //     //event.setAvgPx(orderSellAvgPrice);
        //     order.setAvgPrice(event.getAvgPx());
        //     // 更换缓存中的母单,子单信息
        //     orderCacheManager.cacheParentRequest(event.getStrategyOrderId(), oreq);
        //     orderCacheManager.cacheUnFinishedChildOrder(order.getOrderId(), order);
        // } catch (Exception e) {
        //     logger.error("计算卖方成交数据异常 - 订单号: {}", event.getOrderId(), e);
        // }
    }

    // 从缓存获取未满未 子单id orderId
    // private StrategyOrder getOrderRequestFromCache(String orderId) {
    //     String parentId = orderCacheManager.getParentId(orderId);
    //     if (parentId == null) {
    //         return null;
    //     }
    //     // 实现从缓存获取订单信息的逻辑
    //     return orderCacheManager.getParentRequest(parentId);
    // }

    // 发送订单更新到apama
    public void sendOrderUpdateToApama(ExecutionReport event) {
        String orderId = event.getOrderId();
        // 判断是否为Java策略订单
        if(orderCacheManager.getChildStatusMap().containsKey(orderId)){
            // 创建增量订单事件发送至Apama (apama遇到持仓毛刺不再更新)
            NewOrder newOrder = orderCacheManager.getUnFinishedChildOrder().get(orderId);
            String openingClosingType = newOrder.getOpeningClosingType();
            MgapIncrementalOrderEvent incrementalOrderManageEvent = 
                    factory.getIncrementalOrderManageEventFromOrderUpdate(event, openingClosingType);
            sendEventToApama.sendEventToPm(incrementalOrderManageEvent);
            logger.info("发送增量订单到APAMA处理策略: {}", incrementalOrderManageEvent);
        }
    }

    public void calculateMatchVolumeAndPrices(ExecutionReport report) {
        String orderId = report.getOrderId();
        String status = report.getStatus();
        // 5. 成交数据统计处理
        // if (isPartialOrComplete(status)) {
        //     // 当订单状态为成 (ENTRUST_DEAL或ENTRUST_DEAL_ALL) 时，系统进行详细的成交数据统计:
        //     // 5.1 买方或卖方数据统计 (目前计算的都是母单维度的金额统计. 实际基础要素测试 todo)
        //     if (BusinessConstant.BUY_SIDE.equals(report.getSide())) {
        //         //calculateBuyExecutionData(report);
        //     } else if (BusinessConstant.SELL_SIDE.equals(report.getSide())) {
        //         // 5.2 卖方成交数据统计
        //         //calculateSellExecutionData(report);
        //     } else {
        //         logger.warn("成交数据买卖方向获取异常==>订单编号: {}, 成交回报: {}", orderId, report.toString());
        //     }
        // }
    }
    
    public void setExpiredTime(String orderId) {
        // 5.3 如果超时自动撤单时间大于0(即策略设置了[JustAcknowledge])，则设置超时撤单机制
        long expireTime = getExpireTime(orderId);
        logger.info("[{}] 订单为确认状态，开始设置超时撤单机制, 超时时间: {}", orderId, System.currentTimeMillis(), expireTime);
        if (expireTime <= 0) {
            expireTime = 86400; // 默认设置为一天 (86400秒)
        }
        // 添加延迟时间
        // 设置订单超时撤单定时器 注意，这里使用异步任务来模拟apama的on wait机制
        scheduleOrderTimeoutCancellation(orderId, expireTime);
    }
    
    // 异常/拒单等信息推送至WEB端
    public void pushMsgToWeb(ExecutionReport executionReport, String errorMsg, int grade) {
        exceptionPushService.pushExceptionInfo(executionReport.getInstanceId(),
                executionReport.getUserName(), errorMsg, grade, "MM", executionReport.toString());
    }
}
