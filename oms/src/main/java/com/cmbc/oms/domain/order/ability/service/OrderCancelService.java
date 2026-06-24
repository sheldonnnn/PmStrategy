package com.cmbc.oms.domain.order.ability.service;

import com.cmbc.oms.domain.event.CancelOrderEvent;
import com.cmbc.oms.domain.facade.apama.SendEventToApama;
import com.cmbc.oms.domain.order.ability.factory.NewOrderEventFactory;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import com.cmbc.oms.infrastructure.cache.OrderCacheManager;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author chendaqian
 * @date 2026/3/11
 * @time 14:13
 * @description 撤单服务
 */
@Service
public class OrderCancelService {
    private static final Logger logger = LoggerFactory.getLogger(OrderCancelService.class);

    @Autowired
    private OrderCacheManager orderCacheManager;
    @Autowired
    private SendEventToApama sendEventToApama;
    @Autowired
    private NewOrderEventFactory newOrderEventFactory;
    @Autowired
    private OrderSyncManager orderSyncManager;

    private final static int syncWaitTime = 10;// 默认超时时间10秒

    /**
     * 处理撤单请求
     * @param reason 策略主动调用、手工调用、超时自动触发
     */
    public boolean handleCancelOrder(String orderId, String strategyId, String reason) {
        if(StringUtils.isNotBlank(orderId)){
            return syncCancelSingleOrder(orderId,reason);
        }else if (StringUtils.isNotBlank(strategyId)){
            return syncCancelStrategyOrder(strategyId,reason);
        }else {
            logger.error("撤单请求订单id和策略id不能同时为空");
            return false;
        }
    }

    private boolean syncCancelSingleOrder(String orderId, String reason){
        // 1.根据订单id来撤单
        // 1.1 判断订单是否存在且为非终态订单，同时不是异常状态订单
        // 1.4校验订单状态
        if(!orderCacheManager.isUnFinished(orderId)
                || orderCacheManager.getUnFinishedChildOrder().get(orderId).isExceptionFlag()){
            logger.error("撤单失败，原因：订单 {} 不存在或已经为异常订单，不允许自动撤单", orderId);
            return false;
        }
        // 2. 发送撤单并获取Future
        CompletableFuture<ExecutionReport> future =cancelOrder(orderId,reason, true);
        if(future==null){
            return false;
        }
        // 3. 阻塞等待并校验结果
        try{
            ExecutionReport report = future.get(syncWaitTime, TimeUnit.SECONDS);
            if(report == null
                    || !OrderStatus.CANCELLED.getStatusCode().equals(report.getStatus())
                    || !OrderStatus.PARTIAL_CANCELLED.getStatusCode().equals(report.getStatus())){
                logger.error("单笔撤单失败订单 {} ", orderId);
                return false;
            }else{
                return true;
            }
        } catch (TimeoutException e) {
            logger.error("单笔订单撤单等待超时{}秒，将统计已返回的部分结果",orderId,syncWaitTime);
            return false;
        } catch (Exception e){
            logger.error("单笔订单撤单过程发生异常 ", orderId, e);
            return false;
        } finally {
            // 清理缓存
            orderCacheManager.removeOrderCache(orderId);
        }
    }

    private boolean syncCancelStrategyOrder(String strategyId, String reason){
        // 2.根据策略id来撤单
        // 2.1查找策略id下所有存在且为非终态订单
        List<String> cancelOrderIds = orderCacheManager.getUnFinishedChildOrderIdsByStrategyId(strategyId);
        if (cancelOrderIds == null || cancelOrderIds.isEmpty()) {
            // 没有在途单，默认成功
            return true;
        }
        List<CompletableFuture<ExecutionReport>> futures = new ArrayList<>();
        List<String> validOrderIds = new ArrayList<>();
        // 2.2 循环处理订单撤单
        for (String cancelOrderId : cancelOrderIds) {
            // 不为异常订单才允许发起撤单
            Map<String, NewOrder> unfinishedOrders = orderCacheManager.getUnFinishedChildOrder();
            if (unfinishedOrders != null) {
                NewOrder order = unfinishedOrders.get(cancelOrderId);
                if (order != null && !order.isExceptionFlag()) {
                    CompletableFuture<ExecutionReport> future = cancelOrder(cancelOrderId, reason, true);
                    if (future != null) {
                        futures.add(future);
                        validOrderIds.add(cancelOrderId);
                    }else{
                        return false;
                    }
                }
            }
        }
        // 3. 阻塞等待与极简校验
        boolean isAllSuccess = true;
        
        if(!futures.isEmpty()){
            try{
                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                allOf.get(syncWaitTime, TimeUnit.SECONDS);

                for (CompletableFuture<ExecutionReport> future : futures){
                    ExecutionReport report = future.join();
                    if(report == null
                            || !OrderStatus.CANCELLED.getStatusCode().equals(report.getStatus())
                            || !OrderStatus.PARTIAL_CANCELLED.getStatusCode().equals(report.getStatus())){
                        isAllSuccess = false;
                        break; // 只要有一个失败就返回失败
                    }
                }
            } catch (TimeoutException e) {
                logger.warn("策略 {} 撤单等待超时{}秒，将统计已返回的部分结果", strategyId,syncWaitTime);
                isAllSuccess = false;
            } catch (Exception e){
                logger.error("撤单异常发送过程发生异常, strategyId: " + strategyId, e);
                isAllSuccess = false;
            } finally {
                // 清理缓存
                for (String validOrderId : validOrderIds){
                    orderCacheManager.removeOrderCache(validOrderId);
                }
            }
        }
        return isAllSuccess;
    }

    /**
     * 取消订单
     * @param orderId 订单ID
     */
    public CompletableFuture<ExecutionReport> cancelOrder(String orderId,String reason,boolean requireSync) {
        // 是否未被处理过(解决并发问题, 这里采用原子操作put)
        if (!orderCacheManager.tryLockCancelOrder(orderId, OrderStatus.NEW.getStatusCode())) {
            logger.error("撤销订单失败: {},撤单原因: {}",orderId, "has already been received, no action needed.");
            return null;
        }
        CompletableFuture<ExecutionReport> future = null;
        try {
            // 1. 如果需要同步等待，注册凭证
            if (requireSync) {
                future = orderSyncManager.registerFuture(orderId);
            }

            // 2. 组装撤单事件
            CancelOrderEvent cancelOrderEvent = null;
            Map<String, NewOrder> unfinishedOrders = orderCacheManager.getUnFinishedChildOrder();
            if (unfinishedOrders != null) {
                NewOrder order = unfinishedOrders.get(orderId);
                if (order != null) {
                    cancelOrderEvent = newOrderEventFactory.createCancelOrderEvent(order, reason);
                }
            }

            if (cancelOrderEvent == null) {
                logger.error("撤销订单失败: {}, 撤单原因: {}", orderId, "cancelOrderEvent == null");
                if (requireSync) orderSyncManager.removeFuture(orderId);
                return null;
            }

            // 3. 异步发送到 Apama
            sendEventToApama.sendEventToApama(cancelOrderEvent);
            logger.info("撤销事件发送成功: {}, 撤单原因: {}", orderId, reason);
            
            return future;

        } catch (Exception e) {
            logger.error("撤单发送过程发生异常, orderId: " + orderId, e);
            if (requireSync) orderSyncManager.removeFuture(orderId);
            return null;
        }
    }
}
