package com.cmbc.strategy.service;

import com.cmbc.strategy.domain.dto.HedgeStrategyRequest;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.service.instance.HedgeStrategyInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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