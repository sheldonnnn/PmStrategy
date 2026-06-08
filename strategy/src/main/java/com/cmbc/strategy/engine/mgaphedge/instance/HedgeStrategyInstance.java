package com.cmbc.strategy.engine.mgaphedge.instance;

import com.cmbc.oms.constant.BaseConstants;
import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.domain.exposure.dto.HedgePositionSummary;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.strategy.constant.*;
import com.cmbc.strategy.domain.dto.ClientMemberInfo;
import com.cmbc.strategy.domain.entity.HedgeStrategyInstanceEntity;
import com.cmbc.strategy.domain.model.StrategyStatSummary;
import com.cmbc.strategy.domain.model.hedge.GoldStrategyBean;
import com.cmbc.strategy.domain.model.market.PloyPrices;
import com.cmbc.strategy.domain.model.market.SubscribeRequest;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.engine.core.context.StrategyContext;
import com.cmbc.strategy.engine.core.engine.BaseStrategy;
import com.cmbc.strategy.engine.mgaphedge.trigger.HedgeTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 套利策略实例实现类
 */
@Slf4j
public class HedgeStrategyInstance extends BaseStrategy<HedgeStrategyConfig> {

    // === 内部变量与组件 ===
    private final Map<String, StrategyStatSummary> HEDGE_STRATEGY_MAP = new ConcurrentHashMap<>();
    private final HedgeTrigger triggerEvaluator;

    // 业务组件
    protected final AtomicReference<StrategyStatus> status = new AtomicReference<>(StrategyStatus.CREATED);

    // 当前生效时间片
    private volatile SymbolTimeSlice activeTimeSlice;
    private long hedgingStartTime; // 平盘开始时间
    private long chaseStartTime; // 追单开始时间
    private Integer chaseNumber = 0;
    private BigDecimal firstQuotePrice; // 平盘执行首单记录的基准报价

    // === 定时任务句柄 ===
    private ScheduledFuture<?> executionTaskHandle;
    private ScheduledFuture<?> chaseTaskHandle;
    private ScheduledFuture<?> strategyMonitorPushTask;

    public HedgeStrategyInstance(HedgeStrategyConfig config, String instanceId, HedgeTrigger triggerEvaluator,
            StrategyContext strategyContext) {
        super(config, instanceId, strategyContext);
        this.triggerEvaluator = triggerEvaluator;
        this.status.set(StrategyStatus.CREATED);
    }

    // ============================== 生命周期实现 ==============================

    @Override
    public void start() {
        // 1. 初始化
        HedgePositionSummary position = getClientPosition();
        HedgeStrategyInstanceEntity entity = new HedgeStrategyInstanceEntity(instanceId, config, position);
        strategyContext.getHedgeStrategyInstanceService().saveInstance(entity);
        log.info("[{}] HedgeStrategy is starting...", instanceId);

        // 初始化全局变量缓存
        this.HEDGE_STRATEGY_MAP.clear();

        // 提前初始化当前时间片。如果当前无可用时间片，getOrRefreshActiveSlice 内部会触发 stop 停机
        getOrRefreshActiveSlice();
        if (this.status.get() == StrategyStatus.STOPPED) {
            log.warn("[{}] 策略启动时无可用平盘合约，已触发自动停机。", instanceId);
            pushExceptionInfo(3, "策略启动时无可用平盘合约，已触发自动停机！");
            return;
        }

        List<SubscribeRequest> subReqs = collectSubscriptions(config.getCounterParty(), config.getExchId());
        this.subscribe(subReqs, config.getUserId());

        // 2. 启动“监控模式”
        startMonitoringPhase();

        // 3. 策略行情信息定时推送
        this.strategyMonitorPushTask = schedule(this::pushStrategyMonitorInfo, 1000L);
    }

    @Override
    public void stop(String reason) {
        log.info("[{}] HedgeStrategy is Stopping...", instanceId);
        status.set(StrategyStatus.STOPPED);

        // 1. 停止业务调度定时任务（切断源头，不再产生新订单）
        stopTask(executionTaskHandle);
        stopTask(chaseTaskHandle);
        stopTask(strategyMonitorPushTask); // 移至自旋前，提早切断定时推送

        // 2. 撤销市场上有效订单
        cancelAllOrders();
        // 3.2 记录终态快照和成交汇总数据
        HedgeStrategyInstanceEntity entity = new HedgeStrategyInstanceEntity();
        entity.setInstanceId(instanceId);
        entity.setStatus(StrategyStatus.STOPPED.getCode());
        entity.setUpdateTime(new java.util.Date());
        entity.setEndTime(new java.util.Date());
        entity.setFinalPosition(getClientPosition());

        BigDecimal cumQty = BigDecimal.ZERO;
        BigDecimal cumAmount = BigDecimal.ZERO;
        for (com.cmbc.strategy.domain.model.StrategyStatSummary summary : HEDGE_STRATEGY_MAP.values()) {
            if (summary.getCumQty() != null) {
                cumQty = cumQty.add(summary.getCumQty());
            }
            if (summary.getCumAmount() != null) {
                cumAmount = cumAmount.add(summary.getCumAmount());
            }
        }
        entity.setCumQty(cumQty);
        entity.setCumAmount(cumAmount);

        if (strategyContext.getGoldHedgeIoExecutor() != null) {
            strategyContext.getGoldHedgeIoExecutor().execute(() -> {
                // 使用新的快照更新接口
                strategyContext.getHedgeStrategyInstanceService().updateStrategyInstanceSnapshot(entity);
            });
        } else {
            strategyContext.getHedgeStrategyInstanceService().updateStrategyInstanceSnapshot(entity);
        }

        // 3. 异步延时执行彻底停机逻辑（自旋等待撤单回执）
        new Thread(() -> {
            try {
                // 自旋判断，最大等待5秒，每0.5秒判断一次
                int maxRetries = 10;
                while (maxRetries > 0) {
                    java.util.List<?> pendingOrders = HedgeStrategyInstance.this.getPendingOrder();
                    if (pendingOrders == null || pendingOrders.isEmpty()) {
                        log.info("[{}] 挂单已全部撤销完成，退出自旋等待。", instanceId);
                        break;
                    }
                    log.info("[{}] 尚有未完成的撤单，自旋等待中... 剩余最大等待次数: {}", instanceId, maxRetries);
                    java.util.concurrent.TimeUnit.MILLISECONDS.sleep(500);
                    maxRetries--;
                }

                if (maxRetries <= 0) {
                    log.warn("[{}] 停机等待超时 (5S)，仍有未撤单或异常订单。强制执行停机逻辑！剩余挂单信息：{}", instanceId,
                            HedgeStrategyInstance.this.getPendingOrder());
//                    pushExceptionInfo(3, "策略停机等待超时(5秒)，已强制停机，请检查是否遗留未成挂单！");
                }

                log.info("[{}] 执行最终停机清理工作...", instanceId);
                // 3.1 趁着推送任务还没关，执行最后一次终态数据推送
                pushStrategyMonitorInfo();

            } catch (Exception e) {
                log.error("[{}] 异步停机处理过程发生异常", instanceId, e);
                pushExceptionInfo(3, "策略停止处理异常！！！");
            }
        }, "strategy-shutdown-" + instanceId).start();
    }

    @Override
    public void pause() {

        // 确保运行状态下才能暂停
        if (isRunning()) {
            if (status.compareAndSet(status.get(), StrategyStatus.PAUSED)) {
                //
                stopTask(executionTaskHandle);
                updateStatusSync();
                log.info("[{}] 收到管理端终止指令，策略已终止。", instanceId);
            }
        } else {
            log.warn("[{}] 当前状态为 {}, 无法执行暂停。", instanceId, status.get());
        }
    }

    @Override
    public void resume() {
        // 状态校验是否可暂停
        if (status.get() == StrategyStatus.PAUSED) {
            log.info("[{}] 收到管理端恢复指令，正在恢复...", instanceId);
            startMonitoringPhase();
        } else {

            log.warn("[{}] 当前状态为 {}, 无法执行恢复。", instanceId, status.get());
        }

    }

    // 组装订阅请求
    private List<SubscribeRequest> collectSubscriptions(String counterParty, String exchId) {

        List<SubscribeRequest> subscribeRequests = new ArrayList<>();

        if (config.getSymbolTimeSlices() == null)
            return subscribeRequests;

        for (SymbolTimeSlice slice : config.getSymbolTimeSlices()) {

            if (slice.getSymbol() == null)
                continue;

            SubscribeRequest req = new SubscribeRequest();
            // 境内外合规逻辑判定
            if (BaseConstants.DOMESTIC_TYPE_INNER.equals(slice.getDomesticType())) {
                req.setCounterParty(BaseConstants.SERVICE_NAME_DIMPLE);
                req.setExchId(BaseConstants.SERVICE_NAME_DIMPLE);

            } else {

                req.setCounterParty(counterParty);
                req.setExchId(exchId);
            }
            subscribeRequests.add(req);
        }

        SubscribeRequest req = new SubscribeRequest();
        req.setSymbol(config.getFxSymbol());
        req.setCounterParty(counterParty);
        req.setExchId(exchId);
        subscribeRequests.add(req);
        return subscribeRequests;

    }

    // ============================== 核心逻辑模块 ==============================

    /**
     * 阶段一：启动监控任务
     */
    private void startMonitoringPhase() {
        StrategyStatus currentStatus = status.get();
        if (!isRunning()) {
            log.warn("[{}] 策略已停止或熔断，拒绝切换到 MONITOR 状态。", instanceId);
            return;
        }

        if (!attemptTransition(currentStatus, StrategyStatus.MONITOR)) {
            log.error("[{}] 平盘监控切换失败：状态不满足，当前状态：{}", instanceId, this.status.get());
            return;
        }
        log.info("[{}] Switch to MONITOR phase.", instanceId);

        updateStatusSync();
        stopTask(executionTaskHandle);
        stopTask(chaseTaskHandle);

        // 主动触发一次检查，避免错过恰好发生的事件
        runMonitoringLogic();
    }

    /**
     * 阶段二：启动平盘逻辑
     */
    private void switchToExecution() {
        if (!attemptTransition(StrategyStatus.MONITOR, StrategyStatus.HEDGE)) {
            log.error("[{}] 拒绝平盘触发：状态不满足，当前状态：{}", instanceId, this.status.get());
            return;
        }
        this.firstQuotePrice = null; // 新一轮平盘执行启动，清理旧的基准报价
        updateStatusSync();
        log.info("[{}] Switch to EXECUTION phase.", instanceId);
        this.hedgingStartTime = System.currentTimeMillis();

        // 按照配置的时间间隔执行平盘逻辑
        this.executionTaskHandle = schedule(this::runExecutionLogic,
                config.getOrderIntervalSec().multiply(BigDecimal.valueOf(1000)).longValue());

        // 双重检查防并发泄露
        if (!isRunning()) {
            stopTask(this.executionTaskHandle);
        }
    }

    // 阶段三：启动追单处理
    private void switchToChase() {
        log.info("[{}] Switch to Chase phase.", instanceId);
        stopTask(executionTaskHandle);
        // 触发追单提醒发送到Web端
        strategyContext.getHedgeStrategyPushService().sendChasingRequest(this.instanceId, config.getUserId());

    }

    // 阶段三：启动追单处理
    private void startChaseStrategy() {
        if (!attemptTransition(StrategyStatus.HEDGE, StrategyStatus.CHASE)) {
            log.error("[{}] 拒绝追单：状态不满足，当前状态：{}", instanceId, this.status.get());
            stop("拒绝启动追单：状态不满足，当前状态" + this.status);
            return;
        }

        updateStatusSync();
        log.info("[{}] Switch to Chase phase.", instanceId);
        // 触发追单提醒发送到Web端
        this.chaseNumber = 0;

        // 追单阶段继续沿用之前的 firstQuotePrice，不清理基准价
        this.chaseStartTime = System.currentTimeMillis();

        this.chaseTaskHandle = schedule(this::runChaseLogic,
                config.getOrderIntervalSec().multiply(BigDecimal.valueOf(1000)).longValue());

        // 双重检查防并发泄露
        if (!isRunning()) {
            stopTask(this.chaseTaskHandle);
        }
    }

    /**
     * 监控逻辑：检查是否触发平盘条件
     */
    public void onPositionUpdateEvent() {
        if (status.get() == StrategyStatus.MONITOR) {
            runMonitoringLogic();
        }
    }

    private void runMonitoringLogic() {
        if (status.get() != StrategyStatus.MONITOR) {
            log.warn("[{}] 执行监控任务跳过：当前状态不是 MONITOR (Actual: {})", instanceId, status.get());
            return;
        }

        try {
            // 获取最新持仓
            HedgePositionSummary positionSummary = getClientPosition();
            if (positionSummary == null) {
                log.warn("[{}] 获取头寸数据为空，直接停止策略运行...", instanceId);
                pushExceptionInfo(3, "获取头寸数据为空，已触发自动停机！");
                this.stop("获取头寸数据为空，停止策略！！");
                return;
            }
            BigDecimal clientPos = positionSummary.getMgapHedgedPosition().add(positionSummary.getMgapClientPosition());
            BigDecimal hedgedPos = positionSummary.getFrozenNetPosition();
            BigDecimal openPos = positionSummary.getHedgedNetPosition();

            SymbolTimeSlice activeSymbolSlice = getOrRefreshActiveSlice();
            if (activeSymbolSlice == null) {
                return;
            }

            // 2.触发器校验
            boolean signal = triggerEvaluator.evaluate(clientPos, hedgedPos, openPos, activeSymbolSlice);

            // 3。触发切换
            if (signal) {
                log.info("[{}] trigger hedging, current clientPos:{}, hedgedPos:{}, triggerLimit:{}",
                        instanceId, clientPos, hedgedPos, activeSymbolSlice);
                switchToExecution();
            }
        } catch (Exception e) {
            log.error("[{}] 策略监控计算过程发生异常！", instanceId, e);
            pushExceptionInfo(3, "策略监控计算过程发生系统异常！");
        }
    }

    private void runExecutionLogic() {

        try {
            if (status.get() != StrategyStatus.HEDGE) {
                log.warn("[{}] Start Hedging failed! Current status is not HEDGING (Actual: {})", instanceId,
                        status.get());
                if (!isRunning()) {
                    stopTask(executionTaskHandle);
                }
                return;
            }
            // 1. 获取当前时间片配置与持仓
            SymbolTimeSlice currentSlice = getOrRefreshActiveSlice();
            if (currentSlice == null) {
                return;
            }
            long timeOutMs = config.getHedgingMaxTime().multiply(BigDecimal.valueOf(1000)).longValue();

            HedgePositionSummary positionSummary = getClientPosition();
            if (positionSummary == null) {
                stop("头寸数据为空，停止策略！！");
                return;
            }
            BigDecimal clientPos = positionSummary.getMgapClientPosition().add(positionSummary.getMgapHedgedPosition());
            BigDecimal openPos = positionSummary.getFrozenNetPosition();
            BigDecimal hedgedPos = positionSummary.getHedgedNetPosition();

            BigDecimal netPos = clientPos.add(hedgedPos);

            // 敞口安全
            if (isGapSafe(netPos, currentSlice)) {
                log.info("[{}] Position is safe. NetPos: {}", instanceId, netPos);

                // 尝试转回监控或执行收尾
                // if (attemptTransition(StrategyStatus.HEDGE, StrategyStatus.MONITOR)) {

                cancelAllOrders();
                startMonitoringPhase();
                // }
            } else if (System.currentTimeMillis() - hedgingStartTime > timeOutMs) {
                // 超时处理：切换到追单阶段
                log.info("[{}] 平盘执行超时，触发追单!", instanceId);
                switchToChase();
            } else {
                // 继续执行平盘逻辑（下单逻辑在此封装）
                handleHedgingExecution(currentSlice, positionSummary);
            }
        } catch (Exception e) {
            updateStatusSync();
            pushExceptionInfo(3, "平盘发单过程发生系统异常！");
        }
    }

    private void runChaseLogic() {
        try {
            if (status.get() != StrategyStatus.CHASE) {
                log.warn("[{}] 追单失败，当前非追单状态！（实际状态：{}）", instanceId, status.get());
                if (!isRunning()) {
                    stopTask(chaseTaskHandle);
                }
                return;
            }

            // 1. 获取当前时间片配置
            SymbolTimeSlice currentSlice = getOrRefreshActiveSlice();
            if (currentSlice == null) {
                // stop("该时间段无对应平盘合约，停止策略！"); // 停止策略 or 切换为监控状态
                return;
            }
            // 2. 超时检查逻辑
            long timeOutMs = config.getChaseMaxDuration().multiply(BigDecimal.valueOf(1000)).longValue();

            HedgePositionSummary positionSummary = getClientPosition();
            if (positionSummary == null) {
                stop("头寸数据为空，停止策略！！");
                return;
            }
            BigDecimal clientPos = positionSummary.getMgapHedgedPosition().add(positionSummary.getMgapClientPosition());
            BigDecimal openPos = positionSummary.getFrozenNetPosition();
            BigDecimal hedgedPos = positionSummary.getHedgedNetPosition();

            BigDecimal netPos = clientPos.add(hedgedPos); // 不包含挂单头寸
            // === 分支 A：敞口安全，平盘结束 ===
            if (isGapSafe(netPos, currentSlice)) {
                log.info("[{}] Position is safe, NetPos: {}.", instanceId, netPos);
                // 尝试流转：HEDGE -> MONITOR
                // if (attemptTransition(StrategyStatus.HEDGE, StrategyStatus.MONITOR)) {
                // cancelAllOrders(); // 撤销剩余挂单
                startMonitoringPhase(); // 重启监控任务
                // }
            } else if (System.currentTimeMillis() - this.chaseStartTime > timeOutMs
                    || chaseNumber >= config.getChaseNumber()) {
                // // 分支B：判断是否超时
                log.info("[{}] 追单执行超时或追单次数超出阈值（追单耗时：{}ms, 阈值：{}ms; 追单次数：{}，阈值：{}），停止策略！", instanceId,
                        System.currentTimeMillis() - this.chaseStartTime, timeOutMs, chaseNumber,
                        config.getChaseNumber());
                // strategyContext.getHedgeStrategyPushService().pushStrategyStatus(config.getUserId(),
                // this.instanceId, String.valueOf(this.status.get()));
                stop("追单执行超时或追单次数超出阈值，停止策略！");
                // //todo:i民生告警处理
            } else {
                // // 分支C：执行下单逻辑
                handleHedgingExecution(currentSlice, clientPos, hedgedPos, openPos, true);
                // // 统计追单次数
                chaseNumber += 1;
            }
        } catch (Exception e) {
            log.error("[{}]追单处理异常！！！", this.instanceId, e);
            pushExceptionInfo(3,"追单过程发生系统异常！");
        }
    }

    /**
     * 策略及行情信息定时推送
     * 推送行情及策略信息，定时推送
     */
    private void pushStrategyMonitorInfo() {
        try {
            HedgePositionSummary positionSummary = getClientPosition();
            PloyPrices ployPrice;
            // 直接读取当前生效的 timeSlice，避免触发撤单、停机等包含副作用的方法
            SymbolTimeSlice activeSymbolSlice = this.activeTimeSlice;
            if (activeSymbolSlice == null) {
                return;
            }
            if (BaseConstants.DOMESTIC_TYPE_INNER.equals(activeSymbolSlice.getDomesticType())) {
                // 境内
                ployPrice = getOnshorePloyPrice(activeSymbolSlice.getSymbol()); // 境内
            } else {
                ployPrice = getOffshorePloyPrice(activeSymbolSlice.getSymbol(), config.getExchId(),
                        config.getCounterParty()); // 境外
            }
            strategyContext.getHedgeStrategyPushService().pushStrategyStats(config.getUserId(),
                    this.instanceId, HEDGE_STRATEGY_MAP, positionSummary, ployPrice);
        } catch (Exception e) {
            log.error("sendGoldHedgeStrategyInfo error", e);
        }
    }

    // ============================================================
    // 3. 下单处理逻辑（分流与报价）
    // ============================================================

    private void handleHedgingExecution(SymbolTimeSlice symbolSlice, BigDecimal clientPos, BigDecimal hedgedPos,
            BigDecimal openPos, boolean isChase) {
        // 包含挂单敞口的净头寸，单位：g
        BigDecimal netPos = clientPos.add(hedgedPos).add(openPos);

        // 1、确定买卖方向，计算下单量
        BigDecimal orderWeight;
        Side side;
        BigDecimal maxOrderQty = BigDecimal.ZERO;
        BigDecimal orderQty = BigDecimal.ZERO;

        if (netPos.compareTo(BigDecimal.ZERO) > 0) {
            orderWeight = netPos.subtract(symbolSlice.getEndLongPosition()); // 单位为g
            side = Side.SELL;
        } else {
            orderWeight = symbolSlice.getEndShortPosition().subtract(netPos); // 默认endShortPosition < 0
            side = Side.BUY;
        }

        boolean isAbroad = BaseConstants.DOMESTIC_TYPE_OUTER.equalsIgnoreCase(symbolSlice.getDomesticType());
        BigDecimal unit;
        // 境内合约最大下单量控制
        if (!isAbroad) {
            if ("2".equals(symbolSlice.getContractType())) { // 期货合约
                maxOrderQty = isChase ? config.getChaseFutureMaxOrderQty() : config.getFutureMaxOrderQty();
            } else {
                maxOrderQty = isChase ? config.getChaseSpotMaxOrderQty() : config.getSpotMaxOrderQty();
            }
            orderWeight = orderWeight.min(maxOrderQty); // 下单量不超过配置中单笔最大下单量
            unit = symbolSlice.getUnit();
            // 计算下单量
            orderQty = unitConvert(orderWeight, unit);
        } else {
            maxOrderQty = isChase ? config.getChaseXauMaxOrderQty() : config.getXauMaxOrderQty();
            orderQty = unitConvert(orderWeight, BaseConstants.OUNCE_GRAM).min(maxOrderQty);
        }

        // 2. 校验：敞口是否满足最小下单单位（1手）
        if (orderQty.compareTo(BigDecimal.ONE) < 0) {
            log.warn("[{}] 敞口不足1手，忽略下单。orderWeight: {}, Unit: {}, symbol: {}",
                    instanceId, orderWeight, symbolSlice.getUnit(), symbolSlice.getSymbol());
            strategyContext.getHedgeStrategyPushService().pushStrategyStatus(
                    config.getUserId(), this.instanceId, String.valueOf(this.status.get().getCode()));
            return;
        }

        // 组装策略母单
        StrategyOrder strategyOrder = buildStrategyOrder(orderQty, side, symbolSlice, isAbroad, isChase);
        if (strategyOrder == null) {
            // log.warn("[{}] 构建名单失败。忽略下单。orderWeight: {}, symbol: {}", instanceId,
            // orderWeight, symbolSlice.getSymbol());
            // strategyContext.getHedgeStrategyPushService().pushStrategyStatus(config.getUserId(),
            // this.instanceId, String.valueOf(this.status.get().getCode()));
            return;
        }

        sendStrategyOrder(strategyOrder);
        log.info("[{}] 发送策略订单: {}", instanceId, strategyOrder);
        strategyContext.getHedgeStrategyPushService().pushStrategyStatus(config.getUserId(),
                this.instanceId, String.valueOf(this.status.get().getCode()));
    }

    // todo:待完善补全
    private StrategyOrder buildStrategyOrder(BigDecimal orderQty, Side side, SymbolTimeSlice symbolSlice,
            boolean isAbroad, boolean isChase) {
        StrategyOrder strategyOrder = new StrategyOrder();
        strategyOrder.setSide(side.getCode());
        strategyOrder.setSymbol(symbolSlice.getSymbol());
        strategyOrder.setTimeOut(config.getOrderTimeoutSec());
        strategyOrder.setUserId(config.getUserId());
        strategyOrder.setDomesticType(symbolSlice.getDomesticType());
        strategyOrder.setQty(orderQty);
        strategyOrder.setTagCode(config.getTagCode());
        strategyOrder.setTagName(config.getTagName());
        strategyOrder.setOffsetFlag(config.getOffsetFlag());
        strategyOrder.setTraderNo(config.getTraderNo());
        strategyOrder.setBusinessType(BaseConstants.ORDER_TAG_TYPE_MGAPHEDGE); // todo 业务类型

        if (!isAbroad) {
            // A. 获取行情快照
            PloyPrices depth = getOnshorePloyPrice(symbolSlice.getSymbol());
            if (depth == null) {
                log.error("[{}] 行情缺失，无法下单: {}", instanceId, symbolSlice.getSymbol());
                pushExceptionInfo(3, "境内行情数据缺失，无法计算报价并下单！");
                strategyContext.getHedgeStrategyPushService().pushStrategyStatus(config.getUserId(),
                        this.instanceId, String.valueOf(this.status.get().getCode()), "行情缺失，无法下单！");
                return null;
            }
            // B. 计算价格
            BigDecimal price = calculateQuotePrice(depth, side, config.getPriceBaseType(), symbolSlice.getc(), isChase);
            if (price == null) {
                log.warn("[{}] 计算报价失败!!", instanceId);
                pushExceptionInfo(3, "计算报价失败，无法正常下单！");
                strategyContext.getHedgeStrategyPushService().pushStrategyStatus(config.getUserId(),
                        this.instanceId, String.valueOf(this.status.get().getCode()), "计算报价失败！");
                return null;
            }
            // C.涨跌停校验
            KsdStaticQuoteInfo ksdStaticQuoteInfo = strategyContext.getKsdStaticQuoteCacheService()
                    .getByInstrumentId(symbolSlice.getSymbol());
            if (ksdStaticQuoteInfo == null) {
                log.warn("[{}] 涨跌停价格为空，禁止下单!!", instanceId);
                pushExceptionInfo(3, "涨跌停价格为空，禁止下单！");
                strategyContext.getHedgeStrategyPushService().pushStrategyStatus(config.getUserId(),
                        this.instanceId, String.valueOf(this.status.get().getCode()), "涨跌停价格为空，禁止下单!");
                return null;
            }
            BigDecimal upLimitBuffer;
            BigDecimal downLimitBuffer;
            // 1. 根据合约类型获取不同的涨跌幅缓冲配置 (配置值除以100，转为系数)
            if ("2".equals(symbolSlice.getContractType())) {// 期货合约
                downLimitBuffer = config.getShfeLimitBuffer().divide(BigDecimal.valueOf(100)).add(BigDecimal.ONE);
                upLimitBuffer = BigDecimal.ONE.subtract(config.getShfeLimitBuffer().divide(BigDecimal.valueOf(100)));
            } else {// 其他合约
                downLimitBuffer = config.getSgeLimitBuffer().divide(BigDecimal.valueOf(100)).add(BigDecimal.ONE);
                upLimitBuffer = BigDecimal.ONE.subtract(config.getShfeLimitBuffer().divide(BigDecimal.valueOf(100)));
            }
            // 2. 报价范围校验：如果报价超出了涨跌停价格的缓冲区间，则禁止下单
            if (price.compareTo(ksdStaticQuoteInfo.getLowerLimitPrice().multiply(downLimitBuffer)) < 0
                    || price.compareTo(ksdStaticQuoteInfo.getUpperLimitPrice().multiply(upLimitBuffer)) > 0) {
                log.warn("[{}] 合约{} 报价未在涨跌停缓冲范围内，禁止下单!!", instanceId, symbolSlice.getSymbol());
                pushExceptionInfo(3, "合约报价未在涨跌停缓冲范围内，禁止下单！");
                strategyContext.getHedgeStrategyPushService().pushStrategyStatus(config.getUserId(),
                        this.instanceId, String.valueOf(this.status.get()), "报价未在涨跌停缓冲范围内，禁止下单!!");
                return null;
            }
            // 统一校验：全局报价偏离度保护 (涵盖平盘和追单，防止价格冲击过大)
            if (this.firstQuotePrice == null) {
                // 记录首单基准价格 (可能是平盘的首单，也可能是追单的首单)
                this.firstQuotePrice = price;
                log.info("[{}] 记录首笔平盘基准下单价: {}", instanceId, this.firstQuotePrice);
            } else {
                if (config.getChaseOrderDeviation() != null) {
                    // 使用局部变量 price 计算偏离度，修复了之前使用尚未赋值的 strategyOrder.getPrice() 导致的空指针隐患
                    BigDecimal currentDeviation = price.subtract(this.firstQuotePrice)
                            .divide(this.firstQuotePrice, 4, RoundingMode.HALF_UP).abs();
                    if (currentDeviation.compareTo(config.getChaseOrderDeviation()) > 0) {
                        pushExceptionInfo(3, "报价偏离度过大触发熔断，已停止发单！");
                        log.error("[{}] 报价偏离度过大触发熔断！首笔基准报价: {}, 当前报价: {}, 偏离度: {}, 配置阈值: {}", instanceId,
                                this.firstQuotePrice, price, currentDeviation,
                                config.getChaseOrderDeviation());
                        stop(symbolSlice.getFxSymbol() + "报价偏离度超出设定范围，停止策略！"); // 停止策略
                        return null;
                    }
                }
            }
            strategyOrder.setPrice(price);
            ClientMemberInfo clientMemberInfo = config.getClientMemberInfo().get(symbolSlice.getExchCode());
            strategyOrder.setMemberId(clientMemberInfo.getMemberId());
            strategyOrder.setClientId(clientMemberInfo.getClientId());
        } else {
            PloyPrices depth = getOffshorePloyPrice(symbolSlice.getSymbol(), config.getExchId(),
                    config.getCounterParty());
            if (depth == null) {
                log.error("[{}] 行情缺失，无法下单: {}", instanceId, symbolSlice.getSymbol());
                pushExceptionInfo(3, "境外行情缺失，无法下单！");
                strategyContext.getHedgeStrategyPushService().pushStrategyStatus(config.getUserId(),
                        this.instanceId, String.valueOf(this.status.get()), "境外行情缺失，无法下单!!");
                return null;
            }
            strategyOrder.setExchCode(config.getExchId());
            strategyOrder.setCounterParty(config.getCounterParty());
        }
        return strategyOrder;
    }

    /**
     * 【细化】价格计算引擎
     * 基于配置的交易模式 从行情中提取基准价并加点
     */
    private BigDecimal calculateQuotePrice(PloyPrices ployPrices, Side side, String priceBaseType, String contractType,
            boolean isChase) {

        BigDecimal basePrice = null;
        log.info("[{}] 计算基准: {}", instanceId, priceBaseType);
        // 1. 获取基准价 (Base Price)
        // 对手方最优: 吃对手价 (买入看卖一，卖出看买一) todo:枚举值管理
        if (isChase || "1".equalsIgnoreCase(priceBaseType)) {
            basePrice = (side == Side.BUY) ? ployPrices.getBestAskPx() : ployPrices.getBestBidPx(); // 直接选取对手最优档位
        } else if ("0".equals(priceBaseType)) {
            basePrice = (side == Side.BUY) ? ployPrices.getSecondBestAskPx() : ployPrices.getSecondBestBidPx();
        }
        // 挂本方价: 本方最优和本方次优
        else if ("3".equals(priceBaseType)) {
            basePrice = (side == Side.BUY) ? ployPrices.getBestBidPx() : ployPrices.getBestAskPx();
        } else if ("4".equals(priceBaseType)) {
            basePrice = (side == Side.BUY) ? ployPrices.getSecondBestBidPx() : ployPrices.getSecondBestAskPx();
        }
        // N (Neutral/中性): 中间价
        else if ("2".equals(priceBaseType)) {
            basePrice = ployPrices.getMidPx();
        }

        if (basePrice == null)
            return null;

        // 2. 叠加点差 (Spread)
        // 买入价 = 基准 + 买入点差
        // 卖出价 = 基准 + 卖出点差
        BigDecimal finalPrice;
        BigDecimal spread;
        if (side == Side.BUY) {
            if ("2".equals(contractType)) {
                spread = isChase ? config.getFutureBuyChaseSpread() : config.getFutureBidSpread();
            } else {
                spread = isChase ? config.getSpotBuyChaseSpread() : config.getSpotBidSpread();
            }
        } else {
            if ("2".equals(contractType)) {
                spread = isChase ? config.getFutureSellChaseSpread() : config.getFutureOfrSpread();
            } else {
                spread = isChase ? config.getSpotSellChaseSpread() : config.getSpotOfrSpread();
            }
        }

        finalPrice = basePrice.add(spread);
        log.info("计算报价结束，挂单基准价: {}, 挂单点差: {}, 最终挂单价: {}", basePrice, spread, finalPrice);
        return finalPrice;
    }

    /**
     * 时间片刷新逻辑
     */
    private SymbolTimeSlice getOrRefreshActiveSlice() {
        LocalTime now = LocalTime.now();
        // Step 1: 快速检查 (Fast Path)
        if (activeTimeSlice != null && now.isBefore(this.activeTimeSlice.getEndTime().minusSeconds(15))
                && now.isAfter(activeTimeSlice.getStartTime())) {
            return activeTimeSlice;
        }
        // Step 2: 进入缓冲期或重新查找
        if (activeTimeSlice != null && now.isBefore(this.activeTimeSlice.getEndTime())
                && now.isAfter(this.activeTimeSlice.getEndTime().minusSeconds(15))) {
            cancelAllOrders();
            return null;
        }
        return refreshActiveTimeSlice(now);
    }

    // 遍历合约配置
    private synchronized SymbolTimeSlice refreshActiveTimeSlice(LocalTime now) {
        // 从 Config List 查找当前时间片并更新缓存
        SymbolTimeSlice newSlice = config.findSlice(now);
        if (newSlice != null) {

            if (this.activeTimeSlice == null || !newSlice.getId().equals(this.activeTimeSlice.getId())) {
                log.info("[{}] symbolSlice change to {}", instanceId, newSlice);
                // 关键：如果场合发生了变化，需要执行相关业务操作，如撤单
                handleSymbolChange(activeTimeSlice, newSlice);
            }

            this.activeTimeSlice = newSlice;
        } else {
            log.info("[{}] 当前时间段无可用平盘合约，策略即将停机", instanceId);
            stop("该时间段无对应平盘合约，停止策略！");
            return null;
        }
        return this.activeTimeSlice;
    }

    public void handleSymbolChange(SymbolTimeSlice oldSlice, SymbolTimeSlice newSlice) {
        // 1. 撤单
        cancelAllOrders();
        // 2. 清理基准价，下个合约重新记录首次价格
        this.firstQuotePrice = null;
    }

    /**
     * 尝试状态流转（原子性操作）
     *
     * @param expectedStatus 期望的当前状态（前置条件）
     * @param targetState    目标状态
     * @return 是否流转成功
     */
    private boolean attemptTransition(StrategyStatus expectedStatus, StrategyStatus targetState) {
        // CAS 原子更新（防止并发修改）
        if (status.compareAndSet(expectedStatus, targetState)) {
            log.info("[{}] 状态流转成功：{} -> {}", instanceId, expectedStatus, targetState);
            return true;
        } else {
            // CAS 失败意味着在校验和更新之间状态被其他线程修改了
            log.warn("[{}] 状态流转失败：CAS并发冲突。原状态可能已变更。", instanceId);
            return false;
        }
    }

    // ---------------- 辅助方法 ----------------

    private BigDecimal unitConvert(BigDecimal gap, BigDecimal unit) {
        return gap.divide(unit, 0, RoundingMode.UP); // 向上取整，最多平超一手
    }

    private void stopTask(ScheduledFuture<?> task) {
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
    }

    public boolean isRunning() {
        return this.status.get() != StrategyStatus.STOPPED && this.status.get() != StrategyStatus.MELTDOWN;
    }

    /**
     * 敞口安全判定逻辑
     * 不再使用固定比例，而是直接读取 SymbolTimeSlice 中的终止线
     */
    private boolean isGapSafe(BigDecimal gap, SymbolTimeSlice rule) {
        // Gap > 0 (缺货/多头敞口)：需买入平盘，直到 Gap <= 多头终止线
        if (gap.compareTo(BigDecimal.ZERO) > 0) {
            return gap.compareTo(rule.getEndLongPosition()) <= 0;
        }
        // Gap < 0 (多货/空头敞口)：需卖出平盘，直到 Abs(Gap) <= 空头终止线
        else {
            return gap.abs().compareTo(rule.getEndShortPosition()) <= 0;
        }
    }

    // ---------------- SDK 事件接收 ----------------

    @Override
    public void onMatch(ExecutionReport executionReport) {
        log.info("[{}] 收到成交事件：{}", instanceId, executionReport);
        try {
            // 获取并更新合约汇总信息
            StrategyStatSummary statSummary = calMatchSummary(executionReport);
            HEDGE_STRATEGY_MAP.put(statSummary.getSymbol() + ":" + statSummary.getSide(), statSummary);
        } catch (Exception e) {
            log.error("[{}] 处理成交事件异常！event: {}", instanceId, executionReport, e);
            pushExceptionInfo(3, "处理订单成交回报事件异常！");
        }
    }

    /**
     * 合约信息统一计算内部调用
     *
     * @param executionReport
     */
    private StrategyStatSummary calMatchSummary(ExecutionReport executionReport) {
        // 获取缓存合约详细信息
        StrategyStatSummary statSummary = HEDGE_STRATEGY_MAP
                .get(executionReport.getSymbol() + ":" + executionReport.getSide());

        // 利空 --
        if (null == statSummary) {
            statSummary = new StrategyStatSummary(config.getUserId(), this.instanceId, executionReport.getSymbol());
            statSummary.setSide(executionReport.getSide()); // 买卖方向
            statSummary.setPrice(executionReport.getPrice()); // 委托价格
            statSummary.setCumQty(executionReport.getLastQty());
            statSummary.setCumAmount(executionReport.getLastAmt());
            statSummary.setAvgPrice(executionReport.getAvgPx());
            statSummary.setDomesticType(executionReport.getDomesticType());
        } else {
            statSummary.getCumQty().add(executionReport.getLastQty()); // 成交重量累计
            statSummary.setPrice(executionReport.getPrice()); // 委托价格
            statSummary.getCumAmount().add(executionReport.getLastAmt()); // 成交金额累计
        }

        // 委托数量进行扣减
        statSummary.setCumPendingQty(statSummary.getCumPendingQty().subtract(executionReport.getLastQty()));
        statSummary.setCumPendingQty(statSummary.getCumPendingQty().max(BigDecimal.ZERO));

        if (BaseConstants.DOMESTIC_TYPE_INNER.equals(statSummary.getDomesticType())) {
            // 境内
            statSummary.setCumWeight(statSummary.convertToWeight(statSummary.getCumQty(), executionReport.getUnit())); // 统一单位换算
            statSummary.setMktPrice(getOnshorePloyPrice(executionReport.getSymbol()).getMidPx());
        } else {
            // 境外
            statSummary
                    .setCumWeight(statSummary.convertToWeight(statSummary.getCumQty(), BaseConstants.OUNCE_GRAM_UNIT)); // 统一单位换算
            statSummary.setFxRate(
                    getOffshorePloyPrice("USD/CNH", config.getExchId(), config.getCounterParty()).getMidPx()); // todo
            // 汇率获取
            statSummary.setMktPrice(
                    getOffshorePloyPrice(executionReport.getSymbol(), config.getExchId(), config.getCounterParty())
                            .getMidPx());
        }

        statSummary.setAvgPrice(
                statSummary.getCumAmount().divide(statSummary.getCumWeight(), 3, BigDecimal.ROUND_HALF_UP));
        statSummary.setCumPendingWeight(
                statSummary.convertToWeight(statSummary.getCumPendingQty(), executionReport.getUnit()));

        return statSummary;
    }

    @Override
    public void onRtnOrder(ExecutionReport executionReport) {
        log.info("[{}] receive order confirm: {}", instanceId, executionReport);
        // StrategyStatSummary orderQtyResult = calOrderRtnSummary(executionReport);
        // HEDGE_STRATEGY_MAP.put(executionReport.getSymbol() + ":" +
        // executionReport.getSide(), orderQtyResult);
    }

    @Override
    public void onOrderCancel(ExecutionReport executionReport) {
        log.info("[{}] receive order cancel: {}", instanceId, executionReport);
    }

    @Override
    public void onOrderRejected(ExecutionReport executionReport) {
        log.info("[{}] receive order reject: {}", instanceId, executionReport);
    }

    public void onOtherEvent(ExecutionReport executionReport) {
        // ...
    }

    public Map<String, StrategyStatSummary> getHdegeStrategyMap() {
        return HEDGE_STRATEGY_MAP;
    }

    /**
     * 查询策略详细信息并进行返回
     *
     * @return
     */
    public GoldStrategyBean getHdegeStrategyInstanceInfo() {
        PloyPrices onshorePloyPrice = null;
        if (BaseConstants.DOMESTIC_TYPE_INNER.equals(activeTimeSlice.getDomesticType())) {
            // 境内
            onshorePloyPrice = getOnshorePloyPrice(activeTimeSlice.getSymbol());
        } else {
            onshorePloyPrice = getOffshorePloyPrice(activeTimeSlice.getSymbol(), config.getExchId(),
                    config.getCounterParty());
        }

        GoldStrategyBean goldHedgeStrategyInstanceInfo = strategyContext.getHedgeStrategyInstanceService()
                .getGoldHedgeStrategyInstanceInfo(config.getUserId(), instanceId);
        if (null == goldHedgeStrategyInstanceInfo) {
            goldHedgeStrategyInstanceInfo = new GoldStrategyBean();
        }
        goldHedgeStrategyInstanceInfo.setInstanceId(instanceId);
        goldHedgeStrategyInstanceInfo.setStatus(StrategyStatus.fromStatusCode(status.get().getCode()));
        goldHedgeStrategyInstanceInfo
                .setMessage(StrategyStatus.fromStatusCode(status.get().getCode()).getFinDescription());

        return goldHedgeStrategyInstanceInfo;
    }

    public void pushExceptionInfo(Integer level, String message){
        if (strategyContext.getGoldHedgeIoExecutor() != null) {
            strategyContext.getGoldHedgeIoExecutor().execute(() -> {
                strategyContext.getExceptionNotificationService().pushExceptionInfo(this.instanceId,config.getUserId(),message,level,"积存金平盘策略",null);
            });
        } else {
            strategyContext.getExceptionNotificationService().pushExceptionInfo(this.instanceId,config.getUserId(),message,level,"积存金平盘策略",null);
        }
    }

    private void updateStatusSync() {
        StrategyStatus latestStatus = this.status.get();
        // 1. 同步落库
        strategyContext.getHedgeStrategyInstanceService().updateStatus(
                config.getUserId(), instanceId, String.valueOf(latestStatus.getCode()), null);
        
        // 2. 异步推送
        if (strategyContext.getGoldHedgeIoExecutor() != null) {
            strategyContext.getGoldHedgeIoExecutor().execute(() -> {
                strategyContext.getHedgeStrategyPushService().pushStrategyStatus(
                        config.getUserId(), instanceId, String.valueOf(latestStatus.getCode()), null);
            });
        } else {
            strategyContext.getHedgeStrategyPushService().pushStrategyStatus(
                    config.getUserId(), instanceId, String.valueOf(latestStatus.getCode()), null);
        }
    }

}