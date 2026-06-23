package com.cmbc.mds.forex.engine;

import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 行情引擎门面，负责编排通道生命周期。
 */
@Service
public class MarketDataEngine {
    private final MarketDataChannelRegistry channelRegistry;
    private final MarketDataWorkerService workerService;

    public MarketDataEngine(MarketDataChannelRegistry channelRegistry,
                            MarketDataWorkerService workerService) {
        this.channelRegistry = channelRegistry;
        this.workerService = workerService;
    }

    /**
     * 注册清洗通道，并为新通道启动 worker。
     */
    public void registerCleanChannel(String cleanId) {
        channelRegistry.registerCleanChannel(cleanId)
                .ifPresent(queue -> workerService.startCleanWorker(cleanId, queue));
    }

    /**
     * 关闭清洗通道；方法提交关闭事件后立即返回。
     */
    public void removeCleanChannel(String cleanId) {
        channelRegistry.removeCleanChannel(cleanId);
    }

    /**
     * 关闭清洗通道，并等待 worker 处理完成。
     * 注意：行情清洗有引用计数机制，只有所有订阅都被移除才应该被清理
     */
    public void removeCleanChannelAndWait(String cleanId, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> closeFuture = channelRegistry.removeCleanChannelWithCompletion(cleanId);
        closeFuture.get(timeout, unit);
    }

    /**
     * 注册聚合通道，并为新通道启动 worker。
     */
    public void registerMergeStrategy(String mergeId) {
        channelRegistry.registerMergeChannel(mergeId)
                .ifPresent(queue -> workerService.startMergeWorker(mergeId, queue));
    }

    /**
     * 关闭聚合通道；方法提交关闭事件后立即返回。
     */
    public void removeMergeStrategy(String mergeId) {
        channelRegistry.removeMergeChannel(mergeId);
    }

    /**
     * 关闭聚合通道，并等待 worker 完成状态清理。
     * 注意：聚合行情有引用计数机制，只有所有订阅都被移除才应该被清理
     */
    public void removeMergeStrategyAndWait(String mergeId, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> closeFuture = channelRegistry.removeMergeChannelWithCompletion(mergeId);
        closeFuture.get(timeout, unit);
    }
}
