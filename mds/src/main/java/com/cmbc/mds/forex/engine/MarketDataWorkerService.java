package com.cmbc.mds.forex.engine;

import com.cmbc.mds.forex.engine.queue.ConflatingQueue;
import com.cmbc.mds.forex.engine.queue.MarketDataQueueEvent;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.MergeMessage;
import com.cmbc.mds.forex.quotes.service.CleanService;
import com.cmbc.mds.forex.quotes.service.MergeService;
import com.cmbc.mds.monitor.QuotePerformanceService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 负责启动和关闭行情处理 worker。
 */
@Service
public class MarketDataWorkerService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataWorkerService.class);

    private final ExecutorService workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final CleanService cleanService;
    private final MergeService mergeService;
    private final QuotePerformanceService quotePerformanceService;

    private volatile boolean running = true;

    public MarketDataWorkerService(MarketDataChannelRegistry channelRegistry,
                                   CleanService cleanService,
                                   MergeService mergeService,
                                   QuotePerformanceService quotePerformanceService) {
        this.cleanService = cleanService;
        this.mergeService = mergeService;
        this.quotePerformanceService = quotePerformanceService;
    }

    /**
     * 为指定清洗通道启动单独的虚拟线程 worker。
     */
    public void startCleanWorker(String cleanId, ConflatingQueue<MarketDataQueueEvent<Depth>> queue) {
        workerExecutor.submit(() -> runEventLoop(
                MarketDataWorkerType.CLEAN,
                cleanId,
                queue,
                depth -> cleanService.doCleanandRoute(cleanId, depth),
                () -> {
                }));
        log.info("[Worker] 清洗通道 worker 已启动: {}", cleanId);
    }

    /**
     * 为指定聚合通道启动单独的虚拟线程 worker。
     */
    public void startMergeWorker(String mergeId, ConflatingQueue<MarketDataQueueEvent<MergeMessage>> queue) {
        workerExecutor.submit(() -> runEventLoop(
                MarketDataWorkerType.MERGE,
                mergeId,
                queue,
                message -> mergeService.handleMergeEvent(mergeId, message),
                () -> mergeService.removeStrategyState(mergeId)));
        log.info("[Worker] 聚合通道 worker 已启动: {}", mergeId);
    }

    /**
     * 执行通用队列事件循环。
     */
    private <T> void runEventLoop(MarketDataWorkerType workerType,
                                  String channelId,
                                  ConflatingQueue<MarketDataQueueEvent<T>> queue,
                                  Consumer<T> dataHandler,
                                  Runnable closeHandler) {
        while (running) {
            try {
                MarketDataQueueEvent<T> event = queue.take();
                if (event.isClose()) {
                    handleCloseEvent(workerType, channelId, event, closeHandler);
                    break;
                }

                quotePerformanceService.start(workerType.metricName());
                try {
                    dataHandler.accept(event.payload());
                } finally {
                    quotePerformanceService.end(workerType.metricName());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logWorkerInterrupted(workerType, channelId, e);
                break;
            } catch (Exception e) {
                log.error("[Worker] {}任务异常 [{}]", workerType.displayName(), channelId, e);
            }
        }
        log.info("[Worker] {}通道 worker 已退出: {}", workerType.displayName(), channelId);
    }

    /**
     * 处理关闭事件并回填完成信号。
     */
    private <T> void handleCloseEvent(MarketDataWorkerType workerType,
                                      String channelId,
                                      MarketDataQueueEvent<T> event,
                                      Runnable closeHandler) {
        try {
            closeHandler.run();
            event.completeSuccessfully();
            log.info("[Worker] {}通道关闭事件处理完成: {}", workerType.displayName(), channelId);
        } catch (Exception e) {
            event.completeExceptionally(e);
            log.error("[Worker] {}通道关闭事件处理失败 [{}]", workerType.displayName(), channelId, e);
        }
    }

    /**
     * 记录 worker 中断原因。
     */
    private void logWorkerInterrupted(MarketDataWorkerType workerType, String channelId, InterruptedException e) {
        if (!running) {
            log.info("[Worker] {} worker 正常关闭 [{}]", workerType.displayName(), channelId);
        } else {
            log.error("[Worker] {} worker 被意外中断 [{}]", workerType.displayName(), channelId, e);
        }
    }

    /**
     * 停止所有 worker。
     */
    @PreDestroy
    public void shutdown() {
        log.info("[Worker] 正在关闭行情 worker...");
        running = false;
        workerExecutor.shutdownNow();
    }
}
