package com.cmbc.strategy.engine.core.timer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 量化引擎通用时间调度服务
 */
@Slf4j
@Service
public class StrategyTimerService {

    // 核心调度线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    // 缓存策略实例的定时任务句柄，以便停机时清理
    private final Map<String, List<ScheduledFuture<?>>> instanceTasks = new ConcurrentHashMap<>();

    /**
     * 为策略实例注册通用延迟事件
     * @param instanceId 策略实例ID
     * @param delayMs    延迟毫秒数
     * @param event      事件对象
     * @param listener   回调监听器
     */
    public <T extends StrategyEvent> void scheduleEvent(String instanceId, long delayMs, T event, StrategyEventListener<T> listener) {
        if (delayMs < 0) return;
        
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                log.info("[{}] 触发系统定时事件: {}", instanceId, event.getClass().getSimpleName());
                listener.onTimeEvent(event);
            } catch (Exception e) {
                log.error("[{}] 时间事件分发处理异常!", instanceId, e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        
        instanceTasks.computeIfAbsent(instanceId, k -> new CopyOnWriteArrayList<>()).add(future);
    }

    /**
     * 为策略实例注册每日循环执行的时间事件
     */
    public <T extends StrategyEvent> void scheduleDailyEvent(String instanceId, java.time.LocalTime targetTime, T event, StrategyEventListener<T> listener) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime targetDateTime = java.time.LocalDateTime.of(now.toLocalDate(), targetTime);
        
        if (targetDateTime.isBefore(now) || targetDateTime.equals(now)) {
            targetDateTime = targetDateTime.plusDays(1);
        }
        
        long initialDelayMs = java.time.Duration.between(now, targetDateTime).toMillis();
        long periodMs = TimeUnit.DAYS.toMillis(1);
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("[{}] 触发系统每日定时事件: {}", instanceId, event.getClass().getSimpleName());
                listener.onTimeEvent(event);
            } catch (Exception e) {
                log.error("[{}] 时间事件分发处理异常!", instanceId, e);
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
        
        instanceTasks.computeIfAbsent(instanceId, k -> new CopyOnWriteArrayList<>()).add(future);
    }

    /**
     * 清理策略实例的所有时间任务
     */
    public void clearTimeTasks(String instanceId) {
        List<ScheduledFuture<?>> tasks = instanceTasks.remove(instanceId);
        if (tasks != null) {
            for (ScheduledFuture<?> task : tasks) {
                task.cancel(false);
            }
        }
    }
}
