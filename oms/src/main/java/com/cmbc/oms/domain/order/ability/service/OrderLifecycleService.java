package com.cmbc.oms.domain.order.ability.service;

import com.apama.util.StringUtils;
import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import com.cmbc.oms.domain.order.model.enums.ParentOrderStatus;
import com.cmbc.oms.infrastructure.cache.OrderCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author chendaqian
 * @date 2026/1/27
 * @time 16:49
 * @description 订单全生命周期管理
 */
@Service
public class OrderLifecycleService {
    private final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);
    @Autowired
    private OrderCacheManager orderCacheManager;
    @Autowired
    private OrderStateMachineValidator orderStateMachineValidator;
    
    // 上行订单创建时的初始化方法
    public void initOrderLifecycle(StrategyOrder strategyOrder) {
        // 初始化缓存，不处理生命周期逻辑
        initOrderCache(strategyOrder);
    }
    
    // 下行订单更新时的处理方法
    public void processOrderLifecycleForUpdate(ExecutionReport report) {
        // 只处理订单生命周期逻辑和更新缓存，不重新初始化
        String orderId = report.getOrderId();
        String targetStatus = report.getStatus();
        String oldStatus = report.getOldStatus();
        log.info("处理订单生命周期, 子单ID:{}, 变更前状态码={}, 目的状态码={}", orderId, oldStatus, targetStatus);
        // 订单状态变更前置校验 (生命周期状态机管理, 防止非法状态覆盖更新原状态, 如撤单拒单不能将已经全部成交的订单更新掉等)
        if(isLegalStatus(oldStatus, targetStatus)){
            // 1.1. 将子单的状态缓存进行更新
            orderCacheManager.cacheChildOrderStatus(orderId, targetStatus);
        }
    }
    
    private void initOrderCache(StrategyOrder parentOrder) {
        // todo 是否需要扩展升级事务控制, 保证缓存初始化或者更新一致性
        // 初始化母子关系缓存
        if (parentOrder.getNewOrderList() != null) {
            
            // 初始化子单状态缓存
            for (NewOrder newOrder : parentOrder.getNewOrderList()) {
                orderCacheManager.cacheChildOrderStatus(
                        newOrder.getOrderId(), 
                        OrderStatus.CREATED.getStatusCode() // 初始化为新订单状态
                );
                
                // 初始化未完成子单缓存 (存提非终态子单)
                // 假设所有子单初始时都不是未完成状态, todo 添加业务逻辑判断
                orderCacheManager.cacheUnFinishedChildOrder(
                        newOrder.getOrderId(),
                        newOrder
                );
                // 初始化子单id -> 母单id缓存
                orderCacheManager.cacheChildParentMap(newOrder.getOrderId(), parentOrder.getOrderId());
            }
            
            // 初始化母子单关系缓存 (母单ID -> 子单ID列表)
            List<String> childOrderIds = parentOrder.getNewOrderList().stream()
                    .map(NewOrder::getOrderId)
                    .collect(Collectors.toList());
            orderCacheManager.cacheParentChildMap(
                    parentOrder.getOrderId(),
                    childOrderIds
            );
        }
        
        // 增加创母单缓存
        orderCacheManager.cacheParentRequest(parentOrder.getOrderId(), parentOrder);
    }
    
    private void processOrderLifecycle(String childOrderId, String targetStatus) {
        String oldStatus = orderCacheManager.getChildStatusMap().get(childOrderId);
        log.info("处理订单生命周期, 子单ID:{}, 变更前状态码={}, 目的状态码={}", childOrderId, oldStatus, targetStatus);
        // 订单状态变更前置校验 (生命周期状态机管理, 防止非法状态覆盖更新原状态, 如撤单拒单不能将已经全部成交的订单更新掉等)
        if(isLegalStatus(oldStatus, targetStatus)){
            // 1.1. 将子单的状态缓存进行更新
            orderCacheManager.cacheChildOrderStatus(childOrderId, targetStatus);
            // 1.2. 若子单状态为终态，则将子单从未完成子单缓存中移除
            // 不能在这里清理缓存会导致后续并发操作失败，在处理后一步处理
        }
    }
    
    public void updateParentOrderStatus(String parentOrderId, String childStatus){
        Map<String, StrategyOrder> parentRequestCache = orderCacheManager.getParentRequestCache();
        if(parentRequestCache.containsKey(parentOrderId)){
            StrategyOrder strategyOrder = parentRequestCache.get(parentOrderId);
            if(Objects.requireNonNull(OrderStatus.fromStatusCode(childStatus)).isFinal()){
                // 子单终态 原子计数增加
                int currentFinishedCount = strategyOrder.getFinishedChildCount().incrementAndGet();
                // 只有子单状态变化才进行更新母单状态
                String oldStatus = strategyOrder.getStatus();
                String newStatus = getNewStatus(oldStatus, currentFinishedCount, strategyOrder);
                if(!newStatus.equals(oldStatus)){
                    strategyOrder.setStatus(newStatus);
                    orderCacheManager.cacheParentRequest(parentOrderId, strategyOrder);
                }
            }
        }
    }
    
    private static String getNewStatus(String oldStatus, int currentFinishedCount, StrategyOrder strategyOrder) {
        String newStatus = oldStatus;
        if(currentFinishedCount >= strategyOrder.getTotalChildCount()){ // 全部子单结束
            if(strategyOrder.getCumQty().compareTo(strategyOrder.getQty()) == 0){
                newStatus = ParentOrderStatus.FILLED.getStatusCode(); // 全部成交
            }else if(strategyOrder.getCumQty().compareTo(BigDecimal.ZERO) > 0){
                newStatus = ParentOrderStatus.PARTIAL_CANCELLED.getStatusCode(); // 部分撤单/拒单
            }else{
                newStatus = ParentOrderStatus.CANCELLED.getStatusCode(); // 全部撤单
            }
            strategyOrder.setEnd(true);
        }else{ // 执行中
            if(strategyOrder.getCumQty().compareTo(BigDecimal.ZERO) > 0){
                newStatus = ParentOrderStatus.PARTIAL_FILL.getStatusCode(); // 部分成交
            }
        }
        return newStatus;
    }
    
    public boolean childOrderStatusIsChange(String childOrderId, String status) {
        if(!orderCacheManager.getChildStatusMap().containsKey(childOrderId)){
            log.error("子单状态缓存中未找到子单ID={}的缓存", childOrderId);
            return false;
        }
        // 获取子单状态
        String orderStatus = orderCacheManager.getChildOrderStatus(childOrderId);
        // 判断是否变更
        return !StringUtils.isEmpty(status) 
                && !status.equals(orderStatus);
    }
    
    public String getNewOrderStatus(ExecutionReport executionReport) {
        if(executionReport.getStatus() != null) {
            return executionReport.getStatus();
        }
        String childOrderId = executionReport.getOrderId();
        String oldStatus = executionReport.getApamaStatus();
        // 1、是否存在于缓存中
        boolean isContain = orderCacheManager.getChildStatusMap().containsKey(childOrderId);
        // 2、获取母单前状态
        String currentStatus = isContain ? orderCacheManager.getChildOrderStatus(childOrderId) : "0";
        executionReport.setOldStatus(currentStatus);
        if(isContain){
            executionReport.setStrategyOrderId(orderCacheManager.getParentId(childOrderId));
        }
        
        // 3、转换为新订单状态
        return OrderStatus.fromOldStatusCode(currentStatus, oldStatus).getStatusCode();
    }
    
    private boolean isLegalStatus(String startStatusInt, String endStatusInt) {
        if (startStatusInt==null || endStatusInt ==null) {
            return false;
        }
        
        try {
            // 使用状态机验证状态转换的合法性
            boolean legalTransition = orderStateMachineValidator.isLegalTransition(startStatusInt, endStatusInt);
            log.error("订单状态转换是否合法标志={}", legalTransition);
            return legalTransition;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public boolean hasCancelOrder(String orderId){
        return orderCacheManager.getCancelOrderStatusMap().containsKey(orderId);
    }
}
