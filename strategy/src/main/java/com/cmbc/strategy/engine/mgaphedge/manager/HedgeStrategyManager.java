package com.cmbc.strategy.engine.mgaphedge.manager;

import com.cmbc.oms.domain.exception.ExceptionNotificationService;
import com.cmbc.oms.domain.facade.ExecutionReportListener;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.facade.strategy.OmsService;
import com.cmbc.strategy.constant.StrategyStatus;
import com.cmbc.strategy.domain.dto.HedgeStrategyRequest;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.hedge.ChaseRequest;
import com.cmbc.strategy.engine.mgaphedge.trigger.HedgeTrigger;
import com.cmbc.strategy.integration.IMarketDataService;
import com.cmbc.strategy.integration.IPositionService;
import com.cmbc.oms.domain.order.service.OrderAlgoService;
import com.cmbc.strategy.engine.core.context.StrategyContext;
import com.cmbc.strategy.engine.mgaphedge.instance.HedgeStrategyInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.cmbc.common.util.concurrent.ShardingThreadPool;

@Service
@Slf4j
public class HedgeStrategyManager implements ExecutionReportListener, com.cmbc.oms.domain.facade.MgapPositionUpdateListener, com.cmbc.oms.domain.facade.QuantPositionUpdateListener {

    private final Map<String, HedgeStrategyInstance> runningInstances = new ConcurrentHashMap<>();

    @Autowired
    private HedgeTrigger triggerEvaluator;

    // 策略外部交互服务
    @Autowired
    private OrderAlgoService algoService;
    @Autowired
    private IMarketDataService marketDataService;
    @Autowired
    private OmsService omsService;
    @Autowired
    private com.cmbc.oms.domain.exposure.service.MgapClientPositionService mgapClientPositionService;
    @Autowired
    private com.cmbc.oms.domain.exposure.service.QuantPositionManager quantPositionManager;
    @Autowired
    private IGoldHedgeStrategyInstanceService goldHedgeStrategyInstanceService;
    @Autowired
    private IPositionService positionService;
    @Autowired
    private KsdStaticQuoteCacheService ksdStaticQuoteCacheService;
    @Autowired
    private ExceptionNotificationService exceptionNotificationService;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("strategyEngineTaskScheduler")
    private TaskScheduler strategyEngineTaskScheduler; // 策略核心定时任务专属调度器

    @Autowired
    private StrategyConfigLoader configLoader;

    @Autowired
    private IGoldHedgeStrategyWebSocketService goldHedgeStrategyWebSocketService;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("goldHedgeIoExecutor")
    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor goldHedgeIoExecutor;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("goldHedgeEventPool")
    private com.cmbc.common.util.concurrent.ShardingThreadPool goldHedgeEventPool;

    @PostConstruct
    public void init() {
        omsService.registerListener(this);
        if (mgapClientPositionService != null) {
            mgapClientPositionService.registerListener(this);
        }
        if (quantPositionManager != null) {
            quantPositionManager.registerListener(this);
        }
    }

    @Override
    public void onMgapPositionUpdated() {
        triggerPositionCheck();
    }

    @Override
    public void onQuantPositionUpdated() {
        triggerPositionCheck();
    }
    
    private void triggerPositionCheck() {
        // 当积存金客盘头寸或量化平盘头寸更新时，遍历所有运行中的策略实例，通过快车道路由触发头寸监控
        if (!CollectionUtils.isEmpty(runningInstances)) {
            for (HedgeStrategyInstance instance : runningInstances.values()) {
                if (instance.isRunning()) {
                    goldHedgeEventPool.execute(instance.getInstanceId(), () -> {
                        instance.onPositionUpdateEvent();
                    });
                }
            }
        }
    }

    /**
     * 启动策略
     */
    public void startStrategy(HedgeStrategyRequest request) {
        String instanceId = request.getInstanceId();

        if (!CollectionUtils.isEmpty(runningInstances) && this.getRunningCount() > 0) {
            log.error("存在运行中的策略实例，不允许许多策略同时启动！");
            throw new IllegalStateException("存在运行中的策略实例，不允许多策略同时启动！");
        }

        if (runningInstances.containsKey(instanceId)) {
            throw new IllegalStateException("Strategy is running: " + instanceId);
        }

        goldHedgeIoExecutor.execute(() -> {
            // 1. 策略交互上下文初始化
            StrategyContext context = new StrategyContext();
            context.setTaskScheduler(strategyEngineTaskScheduler);
            context.setMarketDataService(marketDataService);
            context.setOmsService(omsService);
            context.setPositionService(positionService);
            context.setGoldHedgeStrategyInstanceService(goldHedgeStrategyInstanceService);
            context.setGoldHedgeStrategyWebSocketService(goldHedgeStrategyWebSocketService);
            context.setKsdStaticQuoteCacheService(ksdStaticQuoteCacheService);
            context.setExceptionNotificationService(exceptionNotificationService);
            context.setGoldHedgeIoExecutor(goldHedgeIoExecutor);
            // 2. 从数据库加载并组装配置
            HedgeStrategyConfig config = configLoader.loadConfig(request);

            // 将策略基础信息填充到 config 中
            config.setInstanceId(instanceId);
            config.setUserId(request.getUserName());
            config.setAccount(request.getAccount());
            config.setTagCode(request.getTagCode());
            config.setTagName(request.getTagName());
            config.setClientMemberInfo(request.getClientMemberInfo());
            config.setTraderNo(request.getTraderNo());
            config.setExchId(request.getExchId());
            config.setCounterParty(request.getCounterParty());

            log.info("Load strategy config--{}", config);

            // 3. 创建策略实例（注入 Config）
            HedgeStrategyInstance instance = new HedgeStrategyInstance(config, instanceId, triggerEvaluator, context);

            // 4. 启动并管理
            instance.start();
            runningInstances.put(instanceId, instance);
            log.info("策略启动成功: InstanceId={}", instanceId);
        });
    }

    /**
     * 启动追单操作
     */
    public void startChaseStrategy(ChaseRequest request) {
        if (request == null || StringUtils.isEmpty(request.getInstanceId())) {
            log.warn("收到非法事件，InstanceId为空: {}", request);
            return;
        }

        // 主业务计算逻辑，合约订单已存在
        HedgeStrategyInstance instance = runningInstances.get(request.getInstanceId());
        if ("0".equals(request.getIsChase())) {
            log.warn("InstanceId: {}不允许追单，停止策略！", request.getInstanceId());
            instance.stop("交易员拒绝追单，停止策略！");
            return;
        }

        // 启动追单并进行管理
        instance.startChaseStrategy();
    }

    @Override
    public void onAck(ExecutionReport executionReport) {
        if (executionReport == null || StringUtils.isEmpty(executionReport.getInstanceId())) {
            log.warn("收到非法事件，InstanceId为空: {}", executionReport);
            return;
        }
        HedgeStrategyInstance instance = runningInstances.get(executionReport.getInstanceId());
        if (instance != null) {
            goldHedgeEventPool.execute(executionReport.getInstanceId(), () -> {
                instance.onRtnOrder(executionReport);
            });
        }
    }

    @Override
    public void onReject(ExecutionReport executionReport) {
        if (executionReport == null || StringUtils.isEmpty(executionReport.getInstanceId())) {
            log.warn("收到非法事件，InstanceId为空: {}", executionReport);
            return;
        }
        HedgeStrategyInstance instance = runningInstances.get(executionReport.getInstanceId());
        if (instance != null) {
            goldHedgeEventPool.execute(executionReport.getInstanceId(), () -> {
                instance.onOrderRejected(executionReport);
            });
        }
    }

    @Override
    public void onMatch(ExecutionReport executionReport) {
        log.info("[{}] 分成交事件: {}", executionReport.getInstanceId(), executionReport);
        if (executionReport == null || StringUtils.isEmpty(executionReport.getInstanceId())) {
            log.warn("收到非法事件，InstanceId为空: {}", executionReport);
            return;
        }
        HedgeStrategyInstance instance = runningInstances.get(executionReport.getInstanceId());
        if (instance != null) {
            goldHedgeEventPool.execute(executionReport.getInstanceId(), () -> {
                instance.onMatch(executionReport);
            });
        }
    }

    @Override
    public void onCancel(ExecutionReport executionReport) {
        if (executionReport == null || StringUtils.isEmpty(executionReport.getInstanceId())) {
            log.warn("收到非法事件，InstanceId为空: {}", executionReport);
            return;
        }
        HedgeStrategyInstance instance = runningInstances.get(executionReport.getInstanceId());
        if (instance != null) {
            goldHedgeEventPool.execute(executionReport.getInstanceId(), () -> {
                instance.onOrderCancel(executionReport);
            });
        }
    }

    /**
     * 停止/暂停/恢复策略操作
     */
    public void operateStrategy(HedgeStrategyUpdateRequest request) {
        HedgeStrategyInstance instance = runningInstances.get(request.getInstanceId());
        if (instance != null) {
            String oprType = request.getOprType();

            if ("1".equals(oprType)) {
                log.info("Strategy stop req.instanceId: {}", request.getInstanceId());
                instance.stop();
            } else if ("2".equals(oprType)) {
                log.info("Strategy pause {} req.instanceId: {}", "pause", request.getInstanceId());
                instance.pause();
            } else if ("4".equals(oprType)) {
                log.info("Strategy resume {} req.instanceId: {}", "resume", request.getInstanceId());
                instance.resume();
            }
            log.info("Strategy {} stopped.", request.getInstanceId());
        }
    }

    @PreDestroy
    public void stopAllStrategyGraceFully() {
        log.info("All strategy is stopping...");
        if (!CollectionUtils.isEmpty(runningInstances)) {
            for (HedgeStrategyInstance instance : runningInstances.values()) {
                if (instance.isRunning()) {
                    instance.stop();
                    log.info("策略{}已停止", instance.getInstanceId());
                }
            }
        }
        log.info("所有策略已关闭");
    }

    /**
     * 查询积存进策略信息
     */
    public GoldStrategyBean queryStrategyInstance(QueryHedgeStrategyInstanceRequest request) {
        HedgeStrategyInstance instance = runningInstances.get(request.getInstanceId());
        if (instance != null) {
            // 证明策略实例存在，查询具体明细
            GoldStrategyBean res = instance.getHedgeStrategyInstanceInfo();
            return res;
        }

        // 数据库查询策略运行数据
        GoldStrategyBean res = new GoldStrategyBean();
        GoldHedgeStrategyInstanceEntity goldHedgeStrategyInstanceEntity = goldHedgeStrategyInstanceService.queryInfoByInstanceId(request.getInstanceId());
        if (Objects.isNull(goldHedgeStrategyInstanceEntity)) {
            res.setInstanceId(request.getInstanceId());
            res.setUserName(request.getUserName());
        } else {
            res.setInstanceId(goldHedgeStrategyInstanceEntity.getInstanceId());
            res.setStatus(StrategyStatus.fromStatusCode(goldHedgeStrategyInstanceEntity.getStatus()).getFinDescription());
            res.setMessage(goldHedgeStrategyInstanceEntity.getStatus().toString());
            res.setUserName(goldHedgeStrategyInstanceEntity.getCreateBy());
        }
        return res;
    }

    public int getRunningCount() {
        return (int) runningInstances.values().stream().filter(HedgeStrategyInstance::isRunning).count();
    }
}