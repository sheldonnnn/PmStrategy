package com.cmbc.mds.forex.engine;

import com.cmbc.mds.forex.engine.port.MarketDataQueueGateway;
import com.cmbc.mds.forex.engine.queue.ConflatingQueue;
import com.cmbc.mds.forex.engine.queue.MarketDataQueueEvent;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.MergeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理清洗和聚合通道队列。
 */
@Component
public class MarketDataChannelRegistry implements MarketDataQueueGateway {

    private static final Logger log = LoggerFactory.getLogger(MarketDataChannelRegistry.class);

    private final Map<String, ConflatingQueue<MarketDataQueueEvent<Depth>>> cleanQueues = new ConcurrentHashMap<>();
    private final Map<String, ConflatingQueue<MarketDataQueueEvent<MergeMessage>>> mergeQueues = new ConcurrentHashMap<>();
    private final Set<String> closingCleanChannels = ConcurrentHashMap.newKeySet();
    private final Set<String> closingMergeChannels = ConcurrentHashMap.newKeySet();
    private final Map<String, CompletableFuture<Void>> cleanCloseFutures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> mergeCloseFutures = new ConcurrentHashMap<>();

    /**
     * 注册清洗通道；通道关闭完成前拒绝同 key 重复注册。
     */
    public synchronized Optional<ConflatingQueue<MarketDataQueueEvent<Depth>>> registerCleanChannel(String cleanId) {
        if (closingCleanChannels.contains(cleanId)) {
            log.debug("[ChannelRegistry] 清洗通道正在关闭，拒绝重复注册: {}", cleanId);
            return Optional.empty();
        }

        ConflatingQueue<MarketDataQueueEvent<Depth>> newQueue = new ConflatingQueue<>();
        ConflatingQueue<MarketDataQueueEvent<Depth>> existingQueue = cleanQueues.putIfAbsent(cleanId, newQueue);
        if (existingQueue != null) {
            log.debug("[ChannelRegistry] 清洗通道已存在: {}", cleanId);
            return Optional.empty();
        }
        return Optional.of(newQueue);
    }

    /**
     * 关闭清洗通道；不等待 worker 退出。
     */
    public void removeCleanChannel(String cleanId) {
        removeCleanChannelWithCompletion(cleanId);
    }

    /**
     * 关闭清洗通道并返回 worker 处理完成信号。
     */
    public synchronized CompletableFuture<Void> removeCleanChannelWithCompletion(String cleanId) {
        CompletableFuture<Void> existingFuture = cleanCloseFutures.get(cleanId);
        if (existingFuture != null) {
            log.debug("[ChannelRegistry] 清洗通道正在关闭，复用关闭结果: {}", cleanId);
            return existingFuture;
        }

        ConflatingQueue<MarketDataQueueEvent<Depth>> queue = cleanQueues.remove(cleanId);
        if (queue == null) {
            log.debug("[ChannelRegistry] 清洗通道不存在或已关闭: {}", cleanId);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> completion = new CompletableFuture<>();
        closingCleanChannels.add(cleanId);
        cleanCloseFutures.put(cleanId, completion);
        completion.whenComplete((ignored, ex) -> cleanupCleanCloseState(cleanId, completion, ex));

        if (!queue.close(MarketDataQueueEvent.close(completion))) {
            completion.complete(null);
        }
        log.info("[ChannelRegistry] 清洗通道关闭事件已提交: {}", cleanId);
        return completion;
    }

    /**
     * 注册聚合通道；通道关闭完成前拒绝同 key 重复注册。
     */
    public synchronized Optional<ConflatingQueue<MarketDataQueueEvent<MergeMessage>>> registerMergeChannel(String mergeId) {
        if (closingMergeChannels.contains(mergeId)) {
            log.debug("[ChannelRegistry] 聚合通道正在关闭，拒绝重复注册: {}", mergeId);
            return Optional.empty();
        }

        ConflatingQueue<MarketDataQueueEvent<MergeMessage>> newQueue = new ConflatingQueue<>();
        ConflatingQueue<MarketDataQueueEvent<MergeMessage>> existingQueue = mergeQueues.putIfAbsent(mergeId, newQueue);
        if (existingQueue != null) {
            log.debug("[ChannelRegistry] 聚合通道已存在: {}", mergeId);
            return Optional.empty();
        }
        return Optional.of(newQueue);
    }

    /**
     * 关闭聚合通道；不等待 worker 退出。
     */
    public void removeMergeChannel(String mergeId) {
        removeMergeChannelWithCompletion(mergeId);
    }

    /**
     * 关闭聚合通道并返回 worker 处理完成信号。
     */
    public synchronized CompletableFuture<Void> removeMergeChannelWithCompletion(String mergeId) {
        CompletableFuture<Void> existingFuture = mergeCloseFutures.get(mergeId);
        if (existingFuture != null) {
            log.debug("[ChannelRegistry] 聚合通道正在关闭，复用关闭结果: {}", mergeId);
            return existingFuture;
        }

        ConflatingQueue<MarketDataQueueEvent<MergeMessage>> queue = mergeQueues.remove(mergeId);
        if (queue == null) {
            log.debug("[ChannelRegistry] 聚合通道不存在或已关闭: {}", mergeId);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> completion = new CompletableFuture<>();
        closingMergeChannels.add(mergeId);
        mergeCloseFutures.put(mergeId, completion);
        completion.whenComplete((ignored, ex) -> cleanupMergeCloseState(mergeId, completion, ex));

        if (!queue.close(MarketDataQueueEvent.close(completion))) {
            completion.complete(null);
        }
        log.info("[ChannelRegistry] 聚合通道关闭事件已提交: {}", mergeId);
        return completion;
    }

    /**
     * 判断清洗通道是否仍处于活跃状态。
     */
    public boolean isCleanChannelActive(String cleanId) {
        return cleanQueues.containsKey(cleanId);
    }

    /**
     * 判断聚合通道是否仍处于活跃状态。
     */
    public boolean isMergeChannelActive(String mergeId) {
        return mergeQueues.containsKey(mergeId);
    }

    /**
     * 将行情写入清洗队列；关闭中的通道会被丢弃。
     */
    @Override
    public void pushToClean(String cleanId, Depth depth) {
        if (closingCleanChannels.contains(cleanId)) {
            log.debug("[ChannelRegistry] 丢弃正在关闭的清洗通道数据: {}", cleanId);
            return;
        }

        ConflatingQueue<MarketDataQueueEvent<Depth>> queue = cleanQueues.get(cleanId);
        if (queue != null && queue.offer(MarketDataQueueEvent.data(depth))) {
            return;
        }
        log.debug("[ChannelRegistry] 丢弃未注册或已关闭清洗通道数据: {}", cleanId);
    }

    /**
     * 将消息写入聚合队列；关闭中的通道会被丢弃。
     */
    @Override
    public void pushToMerge(String mergeId, MergeMessage message) {
        if (closingMergeChannels.contains(mergeId)) {
            log.debug("[ChannelRegistry] 丢弃正在关闭的聚合通道数据: {}", mergeId);
            return;
        }

        ConflatingQueue<MarketDataQueueEvent<MergeMessage>> queue = mergeQueues.get(mergeId);
        if (queue != null && queue.offer(MarketDataQueueEvent.data(message))) {
            return;
        }
        log.debug("[ChannelRegistry] 丢弃未注册或已关闭聚合通道数据: {}", mergeId);
    }

    /**
     * 清理清洗通道关闭状态。
     */
    private void cleanupCleanCloseState(String cleanId, CompletableFuture<Void> completion, Throwable ex) {
        synchronized (this) {
            closingCleanChannels.remove(cleanId);
            cleanCloseFutures.remove(cleanId, completion);
        }
        if (ex != null) {
            log.error("[ChannelRegistry] 清洗通道关闭失败: {}", cleanId, ex);
        }
    }

    /**
     * 清理聚合通道关闭状态。
     */
    private void cleanupMergeCloseState(String mergeId, CompletableFuture<Void> completion, Throwable ex) {
        synchronized (this) {
            closingMergeChannels.remove(mergeId);
            mergeCloseFutures.remove(mergeId, completion);
        }
        if (ex != null) {
            log.error("[ChannelRegistry] 聚合通道关闭失败: {}", mergeId, ex);
        }
    }
}
