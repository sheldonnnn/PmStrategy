package com.cmbc.oms.util.concurrent;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 业务通用的哈希分片多线程池
 * 保证相同 routingKey 的任务绝对串行执行于同一线程，实现无锁高并发安全。
 */
@Slf4j
public class ShardingThreadPool {

    private final int shardCount;
    private final ExecutorService[] executors;

    /**
     * @param shardCount 分片数量
     * @param threadNamePrefix 线程前缀名
     */
    public ShardingThreadPool(int shardCount, String threadNamePrefix) {
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be > 0");
        }
        this.shardCount = shardCount;
        this.executors = new ExecutorService[shardCount];
        for (int i = 0; i < shardCount; i++) {
            final int index = i;
            // 1. 修复 OOM 隐患：自定义有界队列与拒绝策略
            this.executors[i] = new ThreadPoolExecutor(
                    1, 1, // 核心、最大均为 1，保证绝对串行
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(10000), // 有界队列防御 OOM
                    r -> { // 2. 修复 Daemon 隐患：使用非守护线程
                        Thread t = new Thread(r, threadNamePrefix + "-" + index);
                        t.setDaemon(false); 
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy() // 背压策略：满了则阻塞上游调用方
            );
        }
    }

    /**
     * 根据 routingKey 进行哈希路由并执行任务
     *
     * @param routingKey 路由键 (如 folderId + symbol)
     * @param task       要执行的任务
     */
    public void execute(String routingKey, Runnable task) {
        if (routingKey == null) {
            routingKey = "DEFAULT_KEY";
        }
        // 3. 修复 Math.abs 极小值负数越界 Bug
        int shardIndex = (routingKey.hashCode() & Integer.MAX_VALUE) % shardCount;
        executors[shardIndex].execute(task);
    }

    /**
     * 直接按指定的 shardIndex 投递任务
     */
    public void execute(int shardIndex, Runnable task) {
        if (shardIndex < 0 || shardIndex >= shardCount) {
            throw new IllegalArgumentException("Invalid shard index: " + shardIndex);
        }
        executors[shardIndex].execute(task);
    }

    /**
     * 计算并返回路由键对应的分片号
     */
    public int getShardIndex(String routingKey) {
        if (routingKey == null) {
            return 0;
        }
        // 3. 修复 Math.abs 极小值负数越界 Bug
        return (routingKey.hashCode() & Integer.MAX_VALUE) % shardCount;
    }

    /**
     * 优雅停机，关闭所有分片线程
     */
    public void shutdown() {
        // 4. 修复优雅停机
        for (ExecutorService executor : executors) {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown(); // 拒绝新任务
            }
        }
        for (ExecutorService executor : executors) {
            if (executor != null) {
                try {
                    // 最多等 5 秒，让队列里的存量事件消化完
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("分片线程池未能如期优雅关闭，强行打断！");
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
