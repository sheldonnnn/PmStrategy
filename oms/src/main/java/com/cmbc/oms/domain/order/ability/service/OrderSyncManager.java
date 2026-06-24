package com.cmbc.oms.domain.order.ability.service;

import com.cmbc.oms.domain.order.model.ExecutionReport;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author chendaqian
 * @date 2026/6/18
 * @time 9:27
 * @description 全局同步管理器
 */
@Component
public class OrderSyncManager {
    // 缓存等待中的撤单请求：Key 为 orderId
    private final Map<String, CompletableFuture<ExecutionReport>> cancelFutures = new ConcurrentHashMap<>();
    
    // 注册并获取同步凭证
    public CompletableFuture<ExecutionReport> registerFuture(String orderId) {
        CompletableFuture<ExecutionReport> future = new CompletableFuture<>();
        cancelFutures.put(orderId, future);
        return future;
    }
    
    // 下游唤醒阻塞的线程
    public void completeFuture(String orderId, ExecutionReport report) {
        CompletableFuture<ExecutionReport> future = cancelFutures.remove(orderId);
        if (future != null) {
            future.complete(report);
        }
    }
    
    // 兜底清理，防止内存溢出
    public void removeFuture(String orderId) { cancelFutures.remove(orderId); }
}
