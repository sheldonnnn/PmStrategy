package com.cmbc.mds.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 行情性能统计服务 (支持嵌套统计)
 * 升级版：支持输出每个阶段的独立执行次数 (Count/TPS)、平均耗时与最近耗时。
 */
@Service
public class QuotePerformanceService {

    private static final Logger log = LoggerFactory.getLogger(QuotePerformanceService.class);

    @Value("${app.quote.performance.enabled:false}")
    private volatile boolean enabled;

    @Value("${app.quote.performance.report-interval-seconds:1}")
    private long reportIntervalSeconds;

    // 统计容器：Key -> Metric (包含 TotalLat, Samples, LastLat)
    private final Map<String, Metric> metrics = new ConcurrentHashMap<>();

    // 线程上下文：Key (阶段名) -> StartTime (纳秒)，无锁单体环境下使用 HashMap 更优
    private final ThreadLocal<Map<String, Long>> startTimeHolder = ThreadLocal.withInitial(java.util.HashMap::new);

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("QuotePerformanceService disabled.");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Quote-Perf-Monitor");
            t.setDaemon(true);
            return t;
        });

        // 每秒打印一次统计报告
        long intervalSeconds = Math.max(1, reportIntervalSeconds);
        scheduler.scheduleAtFixedRate(this::reportAndReset, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("QuotePerformanceService initialized. Enabled={}, reportIntervalSeconds={}", enabled, intervalSeconds);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 开始计时
     * 
     * @param phase 阶段名称 (如 "Total", "Process", "Adapter")
     */
    public void start(String phase) {
        if (!enabled) {
            return;
        }
        // 仅记录开始时间，不再进行硬编码的计数
        startTimeHolder.get().put(phase, System.nanoTime());
    }

    /**
     * 结束计时并归档
     * 
     * @param phase 阶段名称
     */
    public void end(String phase) {
        if (!enabled) {
            return;
        }

        Long start = startTimeHolder.get().remove(phase);
        if (start != null) {
            long duration = System.nanoTime() - start;
            // 聚合统计：记录耗时，同时 sampleCount 会自增，代表该阶段完成了一次
            metrics.computeIfAbsent(phase, k -> new Metric()).record(duration);
        }
    }

    /**
     * 清理上下文 (防止内存泄漏)
     */
    public void clear() {
        if (!enabled) {
            return;
        }
        startTimeHolder.remove();
    }

    /**
     * 清理当前线程中指定阶段的开始时间。
     *
     * <p>正常统计链路应优先使用 {@link #end(String)}，该方法仅用于明确放弃某个未完成阶段时的兜底清理。
     */
    public void clear(String phase) {
        if (!enabled) {
            return;
        }
        if (phase == null || phase.isBlank()) {
            return;
        }
        startTimeHolder.get().remove(phase);
    }

    /**
     * 定时报告并重置计数器
     */
    private void reportAndReset() {
        if (!enabled) {
            return;
        }

        // 检查是否有任意一个阶段有数据
        boolean hasData = metrics.values().stream().anyMatch(m -> m.statRef.get().sampleCount > 0);
        if (!hasData) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[Perf-Stat]");

        // 1. 为了日志输出顺序稳定，使用 ArrayList 排序，避免每秒创建 TreeMap 带来的额外 GC 开销
        java.util.List<String> sortedPhases = new java.util.ArrayList<>(metrics.keySet());
        java.util.Collections.sort(sortedPhases);

        // 2. 遍历打印
        for (String phase : sortedPhases) {
            Metric metric = metrics.get(phase);
            // 获取并原子性重置当前周期的统计数据，避免拆分计算时的数据错位
            Metric.Snapshot snapshot = metric.statRef.getAndSet(new Metric.Snapshot(0, 0));
            long totalLat = snapshot.totalLatency;
            long count = snapshot.sampleCount; // 获取该阶段在本周期的执行次数
            long lastLat = metric.lastLatency.get(); // 快照，不需要重置

            if (count > 0) {
                double avgMs = (totalLat / (double) count) / 1_000_000.0;
                double lastMs = lastLat / 1_000_000.0;

                // 格式化输出：增加了 Count (TPS)
                sb.append(String.format(" | %s: [Count: %d /s, Avg: %.3f ms, Last: %.3f ms]",
                        phase, count, avgMs, lastMs));
            }
        }

        log.info(sb.toString());
    }

    /**
     * 内部类：单个维度的统计指标 (运用 AtomicReference 快照实现原子采集，防止读写撕裂错位)
     */
    private static class Metric {
        static class Snapshot {
            final long totalLatency;
            final long sampleCount;

            Snapshot(long totalLatency, long sampleCount) {
                this.totalLatency = totalLatency;
                this.sampleCount = sampleCount;
            }
        }

        final java.util.concurrent.atomic.AtomicReference<Snapshot> statRef = new java.util.concurrent.atomic.AtomicReference<>(
                new Snapshot(0, 0));
        final AtomicLong lastLatency = new AtomicLong(); // 最近一次耗时不要求原子一致

        void record(long duration) {
            Snapshot prev, next;
            do {
                prev = statRef.get();
                next = new Snapshot(prev.totalLatency + duration, prev.sampleCount + 1);
            } while (!statRef.compareAndSet(prev, next));

            lastLatency.set(duration);
        }
    }
}
