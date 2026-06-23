package com.cmbc.strategy.engine.hedge.manager;

import com.alibaba.fastjson.JSONObject;
import com.cmbc.common.util.ShardingThreadPool;
import com.cmbc.mds.ksd.cache.KsdStaticQuoteCacheService;
import com.cmbc.oms.constant.BaseConstants;
import com.cmbc.oms.domain.exception.ExceptionNotificationService;
import com.cmbc.oms.domain.exposure.model.HedgePositionSummary;
import com.cmbc.oms.domain.exposure.service.MgapClientPositionService;
import com.cmbc.oms.domain.exposure.service.QuantPositionManager;
import com.cmbc.oms.domain.facade.strategy.api.ExecutionReportListener;
import com.cmbc.oms.domain.facade.strategy.api.MgapPositionUpdateListener;
import com.cmbc.oms.domain.facade.strategy.api.QuantPositionUpdateListener;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.infrastructure.facadeimpl.strategy.OmsService;
import com.cmbc.strategy.configuration.BusinessException;
import com.cmbc.strategy.constant.StrategyStatus;
import com.cmbc.strategy.domain.dto.HedgeStrategyChaseRequest;
import com.cmbc.strategy.domain.dto.HedgeStrategyRequest;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.domain.model.hedge.GoldStrategyBean;
import com.cmbc.strategy.engine.context.StrategyContext;
import com.cmbc.strategy.engine.hedge.instance.HedgeStrategyInstance;
import com.cmbc.strategy.engine.hedge.trigger.HedgeTrigger;
import com.cmbc.strategy.integration.IHedgeStrategyPersistService;
import com.cmbc.strategy.integration.IHedgeStrategyWebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class HedgeStrategyManager implements ExecutionReportListener, MgapPositionUpdateListener, QuantPositionUpdateListener {

    private final Map<String, HedgeStrategyInstance> runningInstances = new ConcurrentHashMap<>();

    @Autowired
    private HedgeTrigger triggerEvaluator;
<<<<<<< HEAD
    
    // @Autowired
    // private OrderAlgoService algoService;
    
    // @Autowired
    // private IMarketDataService marketDataService;
    
    @Autowired
    private OmsService omsService;
    
    @Autowired
    private IHedgeStrategyPersistService goldHedgeStrategyInstanceService;
    
    // @Autowired
    // private IPositionService positionService;
    
    @Autowired
    private KsdStaticQuoteCacheService ksdStaticQuoteCacheService;
    
    @Autowired
    private ExceptionNotificationService exceptionNotificationService;
    
    @Autowired
    private MgapClientPositionService mgapClientPositionService;
    
    @Autowired
    private QuantPositionManager quantPositionManager;
    
    // @Autowired
    // private ImsMessageNotifyService imsMessageNotifyService;
    
    @Autowired
    @Qualifier("strategyEngineTaskScheduler")
    private TaskScheduler strategyEngineTaskScheduler; // Spring提供的线程池调度器

    @Autowired
    private StrategyConfigLoader configLoader;

    @Autowired
    private IHedgeStrategyWebSocketService goldHedgeStrategyWebSocketService;
    
    @Autowired
    @Qualifier("hedgeIoExecutor")
    private ThreadPoolTaskExecutor ioExecutor;
    
    @Autowired
    @Qualifier("hedgeStrategyEventPool")
    private ShardingThreadPool eventExecutor;

    @PostConstruct
    public void init() {
        omsService.registerListener((ExecutionReportListener) this);
        mgapClientPositionService.registerListener(this);
        quantPositionManager.registerListener(this);
    }

    /**
     * 启动策略
     */
    public void startStrategy(HedgeStrategyRequest request) throws BusinessException {
        String instanceId = request.getInstanceId();
        if (!CollectionUtils.isEmpty(runningInstances)) {
            // ...
        }
        if (runningInstances.containsKey(instanceId)) {
            throw new IllegalStateException("策略正在运行中: " + instanceId);
        }

        if (null == request.getForceStart() || (!request.getForceStart())) {
            HedgePositionSummary positionSummary = mgapClientPositionService.getMgapPositionSummary();
            if (positionSummary == null) {
                throw new IllegalStateException("启动策略失败, 头寸数据获取异常！！");
            } else {
                BigDecimal netPosition = positionSummary.getMgapClientPosition()
                        .add(positionSummary.getMgapHedgedPosition()).add(positionSummary.getHedgedNetPosition());
                if (netPosition.abs().compareTo(BigDecimal.valueOf(500000)) > 0) {
                    throw new BusinessException("9", "头寸数据异常, 当前净头寸为: " + netPosition);
                }
            }
        }

        // 2. 从数据库加载并组装配置（这一步是同步的，确保配置有效才启动）
        // 新增启动时最大下单量校验 20260615
        HedgeStrategyConfig config = configLoader.loadConfig(request);
        if(config.getSymbolTimeSlices() != null) {
            for(SymbolTimeSlice slice : config.getSymbolTimeSlices()){
                if(BaseConstants.DOMESTIC_TYPE_INNER.equals(slice.getDomesticType())){
                    String symbol = slice.getSymbol();
                    // ContractInfoBasic contractInfoBasic = basicParamManager.getContractInfo(symbol);
                    // ...
                }
            }
        }

        ioExecutor.execute(() -> {
            // 1. 准备/分发上下文依赖
            StrategyContext context = new StrategyContext();
            context.setTaskScheduler(strategyEngineTaskScheduler);
            // context.setMarketDataService(marketDataService);
            context.setOmsService(omsService);
            // context.setPositionService(positionService);

            context.setGoldHedgeStrategyInstanceService(goldHedgeStrategyInstanceService);
            context.setGoldHedgeStrategyWebSocketService(goldHedgeStrategyWebSocketService);
            context.setKsdStaticQuoteCacheService(ksdStaticQuoteCacheService);
            context.setExceptionNotificationService(exceptionNotificationService);
            context.setGoldHedgeIoPool(goldHedgeIoPool);
            context.setGoldHedgeEventPool(goldHedgeEventPool);
            context.setStrategyTimerService(strategyTimerService);

            // 3. 将策略基础信息填充到 config 中
            config.setInstanceId(instanceId);
            config.setUserId(request.getUserName());
            config.setAccount(request.getAccount());
            config.setTagCode(request.getTagCode());
            config.setTagName(request.getTagName());
            config.setClientMemberInfo(request.getClientMemberInfo());
            config.setTraderNo(request.getTraderNo());
            config.setMaxVolume(request.getMaxVolume());
            config.setExchId(request.getExchId());
            config.setCounterParty(request.getCounterParty());
            log.info("Load strategy config: {}", JSONObject.toJSONString(config));

            // 3. 创建策略实例 (注入 Config)
            HedgeStrategyInstance instance = new HedgeStrategyInstance(config, instanceId, triggerEvaluator, context);
            // 4. 启动与缓存
            instance.start();
            runningInstances.put(instanceId, instance);
            log.info("策略启动成功: instanceId={}", instanceId);
        });
    }

    public ApiResponse valitPosition() {
        ApiResponse res = new ApiResponse();
        try {
            HedgePositionSummary positionSummary = mgapClientPositionService.getMgapPositionSummary();
            if (positionSummary == null) {
                res.setCode("9");
                res.setMessage("头寸数据获取异常，禁止启动策略！！");
            } else {
                BigDecimal netPosition = positionSummary.getMgapClientPosition()
                        .add(positionSummary.getMgapHedgedPosition()).add(positionSummary.getHedgedNetPosition());
                if (netPosition.abs().compareTo(BigDecimal.valueOf(500000)) > 0) {
                    res.setCode("1");
                    res.setMessage("头寸数据异常, 当前净头寸为: " + netPosition);
                }
            }
        } catch (Exception e){
            log.error("头寸数据获取异常", e);
            res.setCode("9");
            res.setMessage("头寸数据获取异常: " + e.getMessage());
        }
        return res;
    }

    public void startChaseStrategy(HedgeStrategyChaseRequest request) {
        if (request == null || StringUtils.isEmpty(request.getInstanceId())) {
            log.warn("收到追单事件, InstanceId为空: {}", request);
            return;
        }

        HedgeStrategyInstance instance = runningInstances.get(request.getInstanceId());
        if ("0".equals(request.getIsChase())) {
            log.warn("instanceId: {} 交易员拒绝追单，停止策略！", request.getInstanceId());
            instance.stop("交易员拒绝追单，停止策略！");
            return;
        }

        instance.startChaseStrategy();
    }

    @Override
    public void onQuantPositionUpdate() { }

    @Override
    public void onMgapPositionUpdate() { }

    private void triggerPositionUpdate() {
        for (HedgeStrategyInstance instance : runningInstances.values()) {
            if (instance.isRunning()) {
                eventExecutor.execute(instance.getInstanceId(), () -> {
                    instance.onPositionUpdateEvent();
                });
            }
        }
    }

    @Override
    public void onAck(ExecutionReport executionReport) {
        if (executionReport == null || StringUtils.isEmpty(executionReport.getInstanceId())) {
            log.warn("收到回执事件, InstanceId为空: {}", executionReport);
            return;
        }

        HedgeStrategyInstance instance = runningInstances.get(executionReport.getInstanceId());
        if (instance != null) {
            // ...
        }
    }

    @Override
    public void onReject(ExecutionReport executionReport) {
        if (executionReport == null || StringUtils.isEmpty(executionReport.getInstanceId())) {
            log.warn("收到拒单事件, InstanceId为空: {}", executionReport);
            return;
        }

        HedgeStrategyInstance instance = runningInstances.get(executionReport.getInstanceId());
        if (instance != null) {
            goldHedgeStatPool.execute(executionReport.getInstanceId(), () -> {
                instance.onOrderRejected(executionReport);
            });
        }
    }

    @Override
    public void onMatch(ExecutionReport executionReport) {
        if (executionReport == null || StringUtils.isEmpty(executionReport.getInstanceId())) {
            log.warn("收到成交事件, InstanceId为空: {}", executionReport);
            return;
        }

        HedgeStrategyInstance instance = runningInstances.get(executionReport.getInstanceId());
        if (instance != null) {
            goldHedgeStatPool.execute(executionReport.getInstanceId(), () -> {
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
            goldHedgeStatPool.execute(executionReport.getInstanceId(), () -> {
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
