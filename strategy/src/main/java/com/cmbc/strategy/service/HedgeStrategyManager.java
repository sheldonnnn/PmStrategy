package com.cmbc.strategy.service;

import com.cmbc.strategy.domain.dto.HedgeStrategyRequest;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.service.instance.HedgeStrategyInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class HedgeStrategyManager {

    // 策略实例池: Map<InstanceId, StrategyInstance>
    private final Map<String, HedgeStrategyInstance> runningInstances = new ConcurrentHashMap<>();

    @Autowired
    private HedgeTrigger triggerEvaluator;
    @Autowired
    private OrderAlgoService algoService;
    @Autowired
    private TaskScheduler taskScheduler; // Spring提供的线程池调度器

    @Autowired
    private StrategyConfigLoader configLoader;
    /**
     * 启动策略
     */
    public void startStrategy(HedgeStrategyRequest request) {
        String instanceId = request.getInstanceId();
        if (runningInstances.containsKey(instanceId)) {
            throw new IllegalStateException("Strategy is running: " + instanceId);
        }

        // 2. 从数据库加载并组装配置 (这一步是同步的，确保配置有效才启动)
        log.info("开始加载策略配置... BaseId={}, ContractId={}", request.getBaseConfigId(), request.getSymbolConfigId());
        HedgeStrategyConfig config = configLoader.loadConfig(request.getBaseConfigId(), request.getSymbolConfigId());

        // 将 instanceId 绑定到 config 中 (方便策略内部使用)
        config.setInstanceId(instanceId);

        // 3. 创建策略实例 (注入 Config)
        // 这里的 config 是一个填满了数据的POJO，后续策略运行直接读内存，不再查库
        HedgeStrategyInstance instance = new HedgeStrategyInstance(config, instanceId, triggerEvaluator, algoService);

        // 4. 启动并管理
        instance.start();
        runningInstances.put(instanceId, instance);
        log.info("策略启动成功: InstanceId={}", instanceId);
    }

    /**
     * 停止策略
     */
    public void stopStrategy(String instanceId) {
        HedgeStrategyInstance instance = runningInstances.get(instanceId);
        if (instance != null) {
            instance.stop();
//            runningInstances.remove(instanceId);
            log.info("Strategy {} stopped.", instanceId);
        }
    }

    /**
     * 核心逻辑：优雅停机机制
     * 当 Spring Boot 服务停止（如触发 kill 命令）时，自动执行此方法
     */
    @PreDestroy
    public void stopAllStrategiesGracefully() {
        log.warn("========== 系统准备停止，开始优雅关闭所有积存金平盘策略 ==========");

        // 1. 向所有运行中的策略发送停止信号（修改它们的 volatile 标志位）
        if (!runningInstances.isEmpty()) {
            for (AbstractHedgeStrategy strategy : runningInstances.values()) {
                strategy.stop();
            }
            log.info("已向 {} 个正在运行的策略发送了退出指令.", runningInstances.size());
        }

        // 2. 关闭线程池（不再接受新任务）
        strategyThreadPool.shutdown();

        try {
            // 3. 等待策略线程自行结束，给予它们 10 秒的清理时间 (比如去执行撤单操作)
            log.info("等待策略线程安全退出，超时时间为 10 秒...");
            if (!strategyThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                // 如果 10 秒后还有策略卡死（比如在等某个僵死的网络 I/O），则执行暴力中断
                log.error("超时！部分策略未能安全退出，执行强行中断 (shutdownNow)！");
                strategyThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("等待线程池关闭时被意外打断，强制退出！", e);
            strategyThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 4. 清理内存字典
        runningInstances.clear();
        log.warn("========== 所有积存金平盘策略已停止完毕，允许系统主进程退出 ==========");
    }

    public void dispatchOrderEvent(OrderEvent event) {
        if (event == null || event.getInstanceId() == null) {
            log.warn("收到非法订单事件, InstanceId为空: {}", event);
            return;
        }

        String instanceId = event.getInstanceId();

        // 1. 从内存中找到对应的正在运行的策略实例
        HedgeStrategyInstance instance = runningInstances.get(instanceId);

        if (instance != null) {
            // 2. 异步派发给策略实例的内部队列
            instance.onOmsEventAsync(event);
        } else {
            // 如果策略已经停止，但 OMS 还有延迟到达的回报，可以在这里做个兜底的落库记录
            log.info("策略实例 [{}] 未运行，忽略订单事件: {}", instanceId, event.getOrderId());
        }
    }


    public int getRunningCount() {
        return (int) runningInstances.values().stream().filter(HedgeStrategyInstance::isRunning).count();
    }
}