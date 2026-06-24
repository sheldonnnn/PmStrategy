package com.cmbc.oms.infrastructure.cache;

import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author chendaqian
 * @date 2026/2/4
 * @time 19:32
 * @description
 */
@Component
@Data
public class OrderCacheManager {

    // 母单请求缓存 (母单ID -> 母单请求)
    private final Map<String, StrategyOrder> parentRequestCache = new ConcurrentHashMap<>();

    // 母子单关系缓存 (母单ID -> 子单ID列表)
    private final Map<String, List<String>> parentChildMap = new ConcurrentHashMap<>();

    // 子单id与母单id映射 (子单 id -> 母单id)
    private final Map<String, String> childParentMap = new ConcurrentHashMap<>();

    // 母单状态缓存 (母单ID -> 状态，用于快速查询)
    private final Map<String, String> parentStatusMap = new ConcurrentHashMap<>();

    // 外部订单id与本地id映射 (marketId -> orderId)
    private final Map<String, String> marketIdOrderIdMap = new ConcurrentHashMap<>();

    // 子单状态缓存 (子单ID -> 状态，用于快速查询)
    private final Map<String, String> childStatusMap = new ConcurrentHashMap<>();

    // 未完成子单缓存 (子单ID -> 子单对象，用于超时检查，仅缓存非终态子单)
    private final Map<String, NewOrder> unfinishedChildOrderMap = new ConcurrentHashMap<>();

    // 撤单订单状态缓存 (子单ID -> 状态，用于快速查询)
    private final Map<String, String> cancelOrderStatusMap = new ConcurrentHashMap<>();

    public void cacheParentRequest(String orderId, StrategyOrder parentOrder) {
        parentRequestCache.put(orderId, parentOrder);
    }

    // 子单状态缓存
    public void cacheChildOrderStatus(String childOrderId, String statusCode) {
        childStatusMap.put(childOrderId, statusCode);
        // 更新 未完成订单缓存里的订单状态
        if (unfinishedChildOrderMap.containsKey(childOrderId)) {
            NewOrder order = unfinishedChildOrderMap.get(childOrderId);
            if (order != null) {
                order.setStatus(statusCode);
            }
        }
    }

    // 未完成子单缓存
    public void cacheUnFinishedChildOrder(String childOrderId, NewOrder newOrder) {
        unfinishedChildOrderMap.put(childOrderId, newOrder);
    }

    // 母子单关系缓存
    public void cacheParentChildMap(String parentId, List<String> childOrderIds) {
        parentChildMap.put(parentId, childOrderIds);
    }

    public void cacheChildParentMap(String childOrderId,String parentId ) {
        childParentMap.put(childOrderId ,parentId );
    }

    // 根据母单id清除缓存
    public void removeOrderCache(String parentOrderId) {
        // 全部进行清除
        // 1. 获取该母单下的所有子单ID
        List<String> childOrderIds = parentChildMap.get(parentOrderId);
        
        // 2. 清除所有相关子单的缓存
        if (childOrderIds != null) {
            for (String childOrderId : childOrderIds) {
                unfinishedChildOrderMap.remove(childOrderId);
                childStatusMap.remove(childOrderId);
                // 2.2. 清除撤单状态缓存
                cancelOrderStatusMap.remove(childOrderId);
                // 2.3. 清除子单母单关系缓存
                childParentMap.remove(childOrderId);
            }
            childOrderIds.clear();
        }
        // 3. 清除母单缓存
        parentRequestCache.remove(parentOrderId);
        // 4. 清除母子单关系缓存
        parentChildMap.remove(parentOrderId);
    }

    // 清除所有终态子单缓存(慎用)
    public void removeFinalOrder(String orderId) {
        childStatusMap.remove(orderId);
        unfinishedChildOrderMap.remove(orderId);
        cancelOrderStatusMap.remove(orderId);
    }

    public StrategyOrder getParentRequestCache(String orderId) {
        return parentRequestCache.getOrDefault(orderId, null);
    }

    // 根据子单id获取子单信息
    public NewOrder getChildOrder(String childOrderId) {
        return unfinishedChildOrderMap.getOrDefault(childOrderId, null);
    }

    public String getParentId(String orderId) { return childParentMap.getOrDefault(orderId, null); }
    public Map<String, NewOrder> getUnFinishedChildOrder() { return this.unfinishedChildOrderMap; }

    // 判断订单是否为终态
    public boolean isUnFinished(String orderId) {
        if (orderId == null) {
            return false;
        }
        return this.unfinishedChildOrderMap.containsKey(orderId);
    }

    // 获取该策略id下所有存在且为非终态订单id集合
    public List<String> getUnFinishedChildOrderIdsByStrategyId(String strategyId) {
        if (strategyId == null) {
            return new ArrayList<>();
        }
        return this.unfinishedChildOrderMap.entrySet().stream()
                .filter( entry -> {
                    NewOrder order = entry.getValue();
                    return order != null && strategyId.equals(order.getStrategyInstanceID());
                })
                .map(Map.Entry::getKey)
                .toList();
    }

    // 获取该策略id下所有存在且为非终态订单list
    public List<NewOrder> getUnFinishedChildOrderListByStrategyId(String strategyId) {
        if (strategyId == null) {
            return new ArrayList<>();
        }
        return this.unfinishedChildOrderMap.entrySet().stream()
                .filter( entry -> {
                    NewOrder order = entry.getValue();
                    return order != null && strategyId.equals(order.getStrategyInstanceID());
                })
                .map(Map.Entry::getValue).sorted(Comparator.comparing(NewOrder::getOrderID,Comparator.reverseOrder()))
                .toList();
    }

    public String getChildOrderStatus(String childOrderId) {
        return this.childStatusMap.getOrDefault(childOrderId, null);
    }

    // 撤单请求原子处理
    public boolean tryLockCancelOrder(String orderId,String statusCode) {
        String result = cancelOrderStatusMap.putIfAbsent(orderId, statusCode);
        // 检查订单是否已经被撤单处理过，如果返回null，说明原先没有，现在成功塞入，抢到了锁
        return result == null;
    }
}
