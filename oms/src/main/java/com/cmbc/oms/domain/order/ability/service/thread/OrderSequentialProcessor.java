package com.cmbc.oms.domain.order.ability.service.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author chendaqian
 * @date 2026/4/9
 * @time 18:17
 * @description 订单顺序处理器
 */
public class OrderSequentialProcessor {
    private final Logger log = LoggerFactory.getLogger(OrderSequentialProcessor.class);
    
    // 线程池: Java 21 虚拟线程 (高并发 IO 场景神器)
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    
    // 订单队列: key=orderId
    private final ConcurrentHashMap<String, OrderTaskQueue> queueMap = new ConcurrentHashMap<>();
    
    // 定时清理: 5 分钟无活动则清理 (可配置)
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    
    // 监控用
    private final AtomicLong totalProcessed = new AtomicLong();
    
    public OrderSequentialProcessor() {
        // 每 30 秒清理一次
        cleaner.scheduleAtFixedRate(this::cleanInactiveQueues, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 提交订单回报处理，保证同 orderId 严格有序
     */
    public void submit(String orderId, Runnable task) {
        queueMap.computeIfAbsent(orderId, k -> new OrderTaskQueue(executor, orderId))
                .addTask(task);
    }
    
    /**
     * 订单达到终态，主动清理队列 (最关键)
     */
    public void onOrderFinalized(String orderId) {
        OrderTaskQueue removed = queueMap.remove(orderId);
        if (removed != null) {
            log.debug("订单已终态，移除队列 orderId={}", orderId);
        }
    }
    
    /**
     * 定时清理: 长时间无更新且已为空的队列
     */
    private void cleanInactiveQueues() {
        long now = System.currentTimeMillis();
        queueMap.forEach((orderId, queue) -> {
            if (queue.tasks.isEmpty() && now - queue.getLastActiveTime() > 5 * 60 * 1000) { // 5 分钟
                queueMap.remove(orderId);
                log.debug("[定时清理] 超时无活动，移除 orderId={}", orderId);
            }
        });
    }
    
    /**
     * 单个订单串行任务队列 (不绑定线程)
     */
    private static class OrderTaskQueue {
        private final String orderId;
        private final Executor executor;
        private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();//线程安全
        private final AtomicBoolean scheduled = new AtomicBoolean(false);
        private volatile long lastActiveTime;
        
        public OrderTaskQueue(Executor executor, String orderId) {
            this.executor = executor;
            this.orderId = orderId;
            this.lastActiveTime = System.currentTimeMillis();
        }
        
        public void addTask(Runnable task) {
            tasks.offer(task);
            lastActiveTime = System.currentTimeMillis();
            trySchedule();
        }
        
        private void trySchedule() {
            if (scheduled.compareAndSet(false, true)) {
                executor.execute(this::runLoop);
            }
        }
        
        private void runLoop() {
            try {
                Runnable task;
                while ((task = tasks.poll()) != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                scheduled.set(false);
                if (!tasks.isEmpty()) {
                    trySchedule();
                }
            }
        }
        
        public long getLastActiveTime() { return lastActiveTime; }
    }
}
