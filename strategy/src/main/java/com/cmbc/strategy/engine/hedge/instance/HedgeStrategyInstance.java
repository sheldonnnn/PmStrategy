package com.cmbc.strategy.engine.hedge.instance;

import com.cmbc.oms.constant.BaseConstants;
import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.domain.exposure.dto.HedgePositionSummary;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.NewOrder;
import com.cmbc.strategy.constant.*;
import com.cmbc.strategy.domain.dto.ClientMemberInfo;
import com.cmbc.strategy.domain.entity.HedgeStrategyInstanceEntity;
import com.cmbc.strategy.domain.model.StrategyStatSummary;
import com.cmbc.strategy.domain.model.hedge.GoldStrategyBean;
import com.cmbc.strategy.domain.model.market.PloyPrices;
import com.cmbc.strategy.domain.model.market.SubscribeRequest;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.engine.context.StrategyContext;
import com.cmbc.strategy.engine.core.timer.StrategyEvent;
import com.cmbc.strategy.engine.core.timer.StrategyEventListener;
import com.cmbc.strategy.engine.hedge.event.HedgeTimeSliceEvent;
import com.cmbc.strategy.engine.core.engine.BaseStrategy;
import com.cmbc.strategy.engine.hedge.trigger.HedgeTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.FormattingTuple;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 套利策略实例实现类
 */
@Slf4j
public class HedgeStrategyInstance extends BaseStrategy<HedgeStrategyConfig> implements StrategyEventListener<HedgeTimeSliceEvent> {

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
    // ================= 生命钩子实现 =================

    @Override
    public void start() {
        // 1. 初始化
        // 初始化实例实体
        HedgeStrategyInstanceEntity hedgeStrategyInstanceEntity =
                new HedgeStrategyInstanceEntity(instanceId, config, getHedgePositionSummary());
        strategyContext.getHedgeStrategyInstanceService().insertStrategyInstanceStatus(hedgeStrategyInstanceEntity);
        log.info("[{}] HedgeStrategy is starting...", instanceId);

        // 初始化全局变量缓存
        this.HEDGE_STRATEGY_MAP.clear();

        // 初始化当前时间片。如果当前无可用时间片，自动停机
        this.activeTimeSlice = config.findSlice(LocalTime.now());
        if (this.activeTimeSlice == null) {
            reportException(3, "策略启动时无可用平盘合约，已触发自动停机！");
            stop("该时间段无对应平盘合约，停止策略！");
            return;
        }

        // 注册定时任务
        // 注册定时任务
        registerTimeSlices();
        List<SubscribeRequest> subReqs = collectSubscriptions(config.getCounterParty(), config.getExchId());
        this.subscribe(subReqs, config.getUserId());
        // 注册
        startMonitoringPhase();
        // 启动定时监控运行状态
        this.strategyMonitorTaskHandle = schedule(this::pushStrategyMonitorInfo, 1000);
    }

    @Override
    public void stop(String reason) {
        // 取消订阅
        log.info("[{}] HedgeStrategy is stopping... reason:{}", instanceId, reason);
        status.set(StrategyStatus.STOPPED);

        // 1.停止所有内部定时任务
        stopTask(executionTaskHandle);
        stopTask(chaseTaskHandle);
        stopTask(strategyMonitorTaskHandle);
        // 2. 取消所有未成交的订单
        cancelAllOrders();

        HedgeStrategyInstanceEntity entity = new HedgeStrategyInstanceEntity();
        entity.setInstanceId(instanceId);
        entity.setStatusCode(StrategyStatus.STOPPED.getCode());
        entity.setUpdateBy(config.getUserId());
        entity.setRemark(reason);
        Date now = new Date();
        entity.setUpdateTime(now);
        entity.setEndTime(now);
        entity.setFinalPositionSnapshot(getHedgePositionSummary());
        BigDecimal cumWeight = BigDecimal.ZERO;
        BigDecimal cumAmount = BigDecimal.ZERO;
        // 更新统计数据 (已平重量= 所有平盘单成交重量之和)
        for (StrategyStatSummary strategyStatSummary : HEDGE_STRATEGY_MAP.values()) {
            if (strategyStatSummary.getCumWeight() != null) {
                cumWeight = cumWeight.add(strategyStatSummary.getCumWeight());
            }
            if (strategyStatSummary.getCumAmount() != null) {
                cumAmount = cumAmount.add(strategyStatSummary.getCumAmount());
            }
        }
        entity.setCumQty(cumWeight);
        entity.setCumAmount(cumAmount);
        // 更新策略实例状态为结束管理
        strategyContext.getHedgeStrategyInstanceService().updateInstanceSnapshot(entity);

        // 异步延迟10秒后停止逻辑，保证管理端能够打平订单显示
        new Thread(() -> {
            try {
                // 等待撤单，最大等待3秒
                int maxRetries = 6;
                while (maxRetries > 0) {
                    List<StrategyOrder> pendingOrders = this.getPendingOrder();
                    if (pendingOrders == null || pendingOrders.isEmpty()) {
                        log.info("[{}] 策略实例内部无未结订单,退出...", instanceId);
                        break;
                    }
                    log.info("[{}] 仍有未结订单，等待撤单中...剩余最大等待次数:{}", instanceId, maxRetries);
                    Thread.sleep(500);
                    maxRetries--;
                }
                if (maxRetries <= 0) {
                    log.warn("[{}] 策略实例停止等待超时，强制执行停止逻辑!", instanceId);
                }
                pushStrategyMonitorInfo();
            } catch (Exception e) {
                reportException(e, "异步停止处理线程异常！！", instanceId);
            }
        }, "strategy-shutdown-" + instanceId).start();
    }

    @Override
    public void pause() {
        // 校验合法性
        // 确保只有在运行状态下才能暂停
        if (isRunning()) {
            if (status.compareAndSet(StrategyStatus.MONITOR, StrategyStatus.PAUSED)) {
                // 1. 停止内部定时任务
                stopTask(executionTaskHandle);
                stopTask(chaseTaskHandle);
                // 确保没有执行任务
                cancelAllOrders();
                log.info("[{}] 当前策略中断停止指令，策略已中止(PAUSED).", instanceId);
            }
        } else {
            log.warn("[{}] 当前状态为{}，无法执行暂停...", instanceId, status.get());
        }
    }

    @Override
    public void resume() {
            // 校验合法性
        if (status.get() == StrategyStatus.PAUSED) {
            // 继续执行
            log.info("[{}] 当前策略继续恢复中，正在恢复...", instanceId);
            // 进入监控阶段
            startMonitoringPhase();
        } else {
            log.warn("[{}] 当前状态为{}，无法执行恢复...", instanceId, status.get());
        }
    }

    /**
     * 提取订阅逻辑，不涉及业务逻辑
     */
    private List<SubscribeRequest> collectSubscriptions(String counterParty, String exchId) {
        List<SubscribeRequest> subscribeRequests = new ArrayList<>();

        if (config.getSymbolTimeSlices() != null) {
            for (SymbolTimeSlice slice : config.getSymbolTimeSlices()) {
                // 提取币对
                if (slice.getSymbol() == null) continue;

                // 订阅请求
                SubscribeRequest req = new SubscribeRequest();
                req.setSymbol(slice.getSymbol());
                if (BaseConstants.DOMESTIC_TYPE_INNER.equals(slice.getDomesticType())) {
                    req.setCounterParty(BaseConstants.SERVICE_NAME_DIMPLE);
                    req.setExchId(BaseConstants.SERVICE_NAME_DIMPLE);
                } else {
                    // 境外合约转换
                    req.setCounterParty(counterParty);
                    req.setExchId(exchId);
                }
                subscribeRequests.add(req);
            }
        }
        // 汇率的额外订阅
        SubscribeRequest req = new SubscribeRequest();
        req.setSymbol(config.getSymbol());
        req.setCounterParty(counterParty);
        req.setExchId(exchId);
        subscribeRequests.add(req);

        return subscribeRequests;
    }

    // ================= 核心状态机逻辑 (衍生监控端) =================

    /**
     * 阶段一：自动监控平盘
=======
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
>>>>>>> 701de37 (feat: 定时器调度与线程池架构重构汇总)
     */
    private void startMonitoringPhase() {
        StrategyStatus currentStatus = status.get();
        if (!isRunning()) {
            log.warn("[{}] 策略已经停止，拦截切换到 MONITOR 状态!", instanceId);
            return;
        }
        if (!attemptTransition(currentStatus, StrategyStatus.MONITOR)) {
            log.error("[{}] 平盘监控状态切换失败，状态不匹配,当前状态:{}", instanceId, this.status);
            return;
        }
        log.info("[{}] Switch to MONITOR phase.", instanceId);
        // 开始平盘监控的逻辑，平盘监控
        updateStatusAsync("进入平盘监控阶段");
        stopTask(executionTaskHandle); // 确保互斥
        stopTask(chaseTaskHandle);
        runMonitoringLogic();

    }

    /**
     * 阶段二：自动平盘执行
     */
    private void switchToExecution() {
        if (!attemptTransition(StrategyStatus.MONITOR, StrategyStatus.HEDGE)) {
            log.error("[{}] 切换平盘执行失败，状态不匹配,当前状态:{}", instanceId, this.status);
            return;
        }

        updateStatusAsync("进入平盘执行阶段");
        stopTask(strategyMonitorTaskHandle); // 确保互斥
        log.info("[{}] 进入平盘执行阶段.", instanceId);
        // 重置平盘执行状态,如超时时间,报价等
        this.hedgingStartTime = System.currentTimeMillis();
        // 清空第一笔报价
        this.firstQuotePrice = null;

        // 执行平盘逻辑，每秒检查一遍是否平盘完
        this.executionTaskHandle = schedule(this::runExecutionLogic,
                config.getOpIntervalSeconds().multiply(BigDecimal.valueOf(1000)).longValue());

        // 如果触发马上平盘则取消循环执行直接调用
        if (isRunning()) {
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

        this.chaseTaskHandle = schedule(this::runChaseLogic, config.getOrderIntervalSec().multiply(BigDecimal.valueOf(1000)).longValue());

        // 双重检查防并发泄露
        if (!isRunning()) {
            stopTask(this.chaseTaskHandle);
        }
    }

    public void onPositionUpdateEvent() {
        if (status.get() == StrategyStatus.MONITOR) {
            runMonitoringLogic();
        }
    }

    // 平盘监控
    private void runMonitoringLogic() {
        if (status.get() != StrategyStatus.MONITOR) {
            log.warn("[{}] 执行监控任务跳过：当前状态不是 MONITOR (Actual: {})", instanceId, status.get());
            return;
        }

        try {
            // 1. 获取持仓数据（通过 SDK context)
            HedgePositionSummary positionSummary = getHedgePositionSummary();
            this.positionSnapshot = positionSummary;
            if (positionSummary == null) {
                reportExceptionAndStop(3, "持仓数据为空，停止策略！");
                return;
            }
            BigDecimal mgapClientPos = positionSummary.getMgapClientPosition();
            BigDecimal mgapHedgedPos = positionSummary.getMgapHedgedPosition();
            BigDecimal hedgedPos = positionSummary.getHedgedPosition();

            SymbolTimeSlice activeSymbolSlice = getOrRefreshActiveSlice();
            // 当前激活币对为空，不再轮询，停止
            if (activeSymbolSlice == null) {
                stop("没找到有效的可平盘合约,停止策略！");
                return;
            }

            // 2. 计算触发 (直接使用 config 中的阈值)
            boolean signal = triggerEvaluator.evaluate(mgapClientPos, mgapHedgedPos, hedgedPos, activeSymbolSlice);

            if (signal) {
                log.info("[{}] 平盘监控信号触发. (mgap客户头寸:{}, mgap已平头寸:{}, 策略已平头寸:{}, 触发阈值:{}, 触发差额:{})",
                        instanceId, mgapClientPos, mgapHedgedPos, hedgedPos, activeSymbolSlice.getTriggerLongPosition(),
                        mgapClientPos.subtract(mgapHedgedPos).abs());
                // 进入平盘执行阶段
                switchToExecution();
            }
        } catch (Exception e) {
            reportExceptionAndStop(3, "平盘监控异常！", e);
        }
    }

    private void runExecutionLogic() {
<<<<<<< HEAD
        try {
            if (status.get() != StrategyStatus.HEDGE) {
                log.warn("[{}] 执行平盘逻辑，当前状态不是 HEDGE (Actual: {}).", instanceId, status.get());
                if (!isRunning()) {
                    stopTask(this.executionTaskHandle);
                }
                return;
            }

            // 1. 获取当前时间片配置
            SymbolTimeSlice currentSlice = this.activeTimeSlice;
            if (currentSlice == null) {
                stop("没找到有效的可平盘合约,停止策略！");
                return;
            }

            // 2. 超时判断检查
            long timeOutMs = config.getHedgeMaxTime().multiply(BigDecimal.valueOf(1000)).longValue();

            HedgePositionSummary positionSummary = getHedgePositionSummary();
            this.positionSnapshot = positionSummary;
            if (positionSummary == null) {
                reportExceptionAndStop(1, "持仓数据为空，停止策略！");
                return;
            }

            BigDecimal clientPos = positionSummary.getMgapClientPosition();
            BigDecimal mgapHedgedPos = positionSummary.getMgapHedgedPosition();
            BigDecimal openPos = positionSummary.getFrozenNetPosition();
            BigDecimal hedgedPos = positionSummary.getHedgedPosition();
            BigDecimal netPos = clientPos.add(hedgedPos).add(mgapHedgedPos);      // 头寸偏离敞口头寸

            long timeCost = System.currentTimeMillis() - hedgingStartTime;
            // 如果平盘量大于0，并且超时
            if (isGapSafe(netPos, currentSlice)) {
                reportException(1, "敞口低于平盘容差，回到平盘监控。敞口:{}", netPos);
                // 撤单并且回到平盘监控，并更新状态
                if (attemptTransition(StrategyStatus.HEDGE, StrategyStatus.MONITOR)) {
                    // 回到平盘监控，清空持仓
                    cancelAllOrders(); // 撤销未成交的订单
                    startMonitoringPhase(); // 重新启动监控
                }
            } else if (timeCost > timeOutMs) {
                // 超时撤单，然后看当前状态进入追单
                cancelAllOrders();
                reportException(1, "平盘执行超时({}ms > {}ms)，准备切换追单模式...", timeCost, timeOutMs);
                switchToChase();
            } else {
                // // 执行下单
                handleHedgingExecution(currentSlice, clientPos.add(mgapHedgedPos), hedgedPos, openPos, false);
            }
        } catch (Exception e) {
            reportExceptionAndStop(3, "平盘下发执行系统异常！", e);
        }
    }

    private void runChaseLogic() {
        try {
            if (status.get() != StrategyStatus.CHASE) {
                log.warn("[{}] 执行追单逻辑，当前状态不是 CHASE (Actual: {}).", instanceId, status.get());
                if (!isRunning()) {
                    stopTask(this.chaseTaskHandle);
                }
                return;
            }

            // 1. 获取当前时间片配置
            SymbolTimeSlice currentSlice = this.activeTimeSlice;
            if (currentSlice == null) {
                log,warn("没找到有效的可平盘合约！");  // 停止策略 or 切换状态？
                return;
            }

            // 2. 超时判断检查
            long timeOutMs = config.getChaseMaxDuration().multiply(BigDecimal.valueOf(1000)).longValue();

            HedgePositionSummary positionSummary = getHedgePositionSummary();
            this.positionSnapshot = positionSummary;
            if (positionSummary == null) {
                reportExceptionAndStop(1, "持仓数据为空，停止策略！");
                return;
            }

            BigDecimal clientPos = positionSummary.getMgapClientPosition();
            BigDecimal mgapHedgedPos = positionSummary.getMgapHedgedPosition();
            BigDecimal openPos = positionSummary.getFrozenNetPosition();
            BigDecimal hedgedPos = positionSummary.getHedgedPosition();

            BigDecimal netPos = clientPos.add(hedgedPos).add(mgapHedgedPos);      // 头寸偏离敞口头寸
            long timeCost = System.currentTimeMillis() - chaseStartTime;

            if (isGapSafe(netPos, currentSlice)) {
                reportException(1, "追单阶段敞口头寸低于容差，敞口:{}", netPos);
                if (attemptTransition(StrategyStatus.CHASE, StrategyStatus.MONITOR)) {
                    // 回到平盘监控，清空持仓
                    cancelAllOrders(); // 撤销未成交的订单
                    reportException(1, "追单完毕完成，回到监控。 ({})", instanceId, netPos);
                    startMonitoringPhase(); // 重新启动监控
                }
            } else if (timeCost > timeOutMs) {
                // 追单超时，撤单继续追单
                reportException(1, "追单执行超时({}ms > {}ms)，撤单重试追单...", timeCost, timeOutMs);
                strategyContext.getGoldHedgeStrategyWebSocketService().sendChaseTimeout(config.getUserId(), instanceId, timeCost);
                cancelAllOrders(); // 撤销未成交的订单
                chaseStartTime = System.currentTimeMillis();
            } else if (config.getChaseNumber() <= chaseNumber) {
                // 追单次数超限,发微信或者短信通知，同时停止策略（通知，不停止策略）
                reportExceptionAndStop(1, "追单执行次数超限({}次 >= {}次)，停止策略！", chaseNumber, config.getChaseNumber());
                // stop("追单执行次数超过配置的最多次数："+ chaseNumber +", 达到最大次数限制停止.");
                strategyContext.getGoldHedgeStrategyWebSocketService().sendHedgeStrategyStatus(config.getUserId(), this.instanceId, StrategyStatus.STOPPED.getCode());
            } else {
                handleHedgingExecution(currentSlice, clientPos.add(mgapHedgedPos), hedgedPos, openPos, true);
                // 统计追单次数
                chaseNumber += 1;
            }
        } catch (Exception e) {
            reportExceptionAndStop(3, "追单下发执行系统异常！", e);
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

    // ============================================

    // 1. 下发快速撤单 (只要没撤单，下发都会拒绝)
    private void handleHedgingExecution(SymbolTimeSlice symbolSlice, BigDecimal mgapPos,
                                        BigDecimal hedgedPos, BigDecimal openPos, boolean isChase) {

        // 在途订单敞口头寸 = 净头寸(加法)+在途敞口
        BigDecimal netPos = mgapPos.add(hedgedPos).add(openPos);

        // 获取买卖方向
        String side;
        BigDecimal orderWeight;
        // 小于0 卖出 大于0买入
        BigDecimal maxOrderQty = BigDecimal.ZERO;
        BigDecimal limitPrice = BigDecimal.ZERO;

        if (netPos.compareTo(BigDecimal.ZERO) < 0) {
            orderWeight = netPos.subtract(symbolSlice.getEndLongPosition()); // 单位为g
            side = Side.SELL;
        } else {
            orderWeight = symbolSlice.getEndShortPosition().subtract(netPos); // 单位为g
            side = Side.BUY;
        }

        boolean isAbroad = BaseConstants.DOMESTIC_TYPE_OUTER.equalsIgnoreCase(symbolSlice.getDomesticType());
        // 判断合约境内外
        
        // 境内合约计算下单量限制
        if (!isAbroad) {
            if (BusinessConstant.SM_FUTURES_EXCHANGE.equals(symbolSlice.getExchCode())) {
                maxOrderQty = isChase ? config.getChaseMaxOrderQty() : config.getFutureMaxOrderQty();
            } else {
                maxOrderQty = isChase ? config.getSpotChaseMaxOrderQty() : config.getSpotMaxOrderQty();
            }

            BigDecimal unit = symbolSlice.getUnit();
            // 计算下单量，按 maxOrderQty(克)向下取整
            if (orderWeight.compareTo(maxOrderQty) >= 0) { // 下单量不能超过配置中单笔最大下单量
                orderWeight = unitConvertUp(maxOrderWeight, unit);
            } else {
                orderWeight = unitConvertUp(orderWeight, unit);
            }

            orderWeight = orderWeight.min(maxOrderQty);
            // 2. 校验 是否有在途订单未撤单？如果有（1个） 这次不执行任何操作
            // if (maxOrderQty.compareTo(unit) < 0) {
            //    reportExceptionAndStop(1, "单笔最大下单量太小导致无法拆单，orderWeight:{}, unit:{}, symbol:{}", orderWeight, unit, symbolSlice.getSymbol());
            //    return;
            // }

            // 3. 校验 是否有未完成订单
            List<StrategyOrder> pendingOrders = this.getPendingOrder();
            BigDecimal pendingWeight = pendingOrders.stream().map(StrategyOrder::getLeavesQty).reduce(BigDecimal.ZERO, BigDecimal::add);

            if (pendingWeight.compareTo(BigDecimal.ZERO) > 0) {
                // 如果未完成订单的量(加和)与本次需下单的量相差不大(在偏差范围内) 或者 大于本次需要的量，则不作处理。
                reportException(2, "策略实例存在未完成的订单,无法下发执行!", pendingOrders.size());
                log.info("[{}] 仍有未结订单(合并总数:{})，跳过下发逻辑，等待订单完成... symbol:{} pendingWeight:{} orderWeight:{} limitPrice:{}",
                        instanceId, pendingOrders.size(),
                        symbolSlice.getSymbol(), pendingWeight, orderWeight, limitPrice);
                return;
            }
        } else {
            // 境外合约计算
            String exchCode = BusinessConstant.OUNCE_GRAM;
            maxOrderQty = isChase ? config.getChaseMaxOrderQty() : config.getFxMaxOrderQty();
            BigDecimal orderQty = unitConvertUp(orderWeight, symbolSlice.getUnit()).setScale(4, BigDecimal.ROUND_DOWN); // 保留四位小数
            orderWeight = orderQty.min(maxOrderQty);
        }

        // 最大平盘量限制检查
        BigDecimal maxVolume = config.getMaxVolume();
        if (maxVolume != null && maxVolume.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalCumWeight = getTotalCumWeight();
            if (totalCumWeight.add(orderWeight).compareTo(maxVolume) > 0) {
                reportExceptionAndStop(1, "策略实例累积成交重量超过最大平盘量上限, 停止策略!", totalCumWeight);
                log.info("[{}] 策略实例累积成交重量超过最大平盘量上限, 停止策略! totalCumWeight:{} orderWeight:{} maxVolume:{}",
                        instanceId, totalCumWeight, orderWeight, maxVolume);
                return;
            }
        }

        // 组装参数
        StrategyOrder strategyOrder = buildStrategyOrder(orderWeight, side, symbolSlice, isAbroad, isChase, netPos);
        if (strategyOrder == null) {
            return;
        }

        log.info("[{}] 执行策略下发逻辑", instanceId, strategyOrder);
        // 执行下单逻辑
        try {
            if (strategyOrderRes.getSuccess().equals(StrategyOrderRes.SUCCESS.getSuccess())) { // 这边有问题，代码缺失了发单过程，但看意思应该是这样
                strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(config.getUserId(),
                        this.instanceId, status.get().getCode());
            }
        } catch (Exception e) {
            reportExceptionAndStop(3, "策略实例订单下发系统异常！", e);
        }
    }

    private StrategyOrder buildStrategyOrder(BigDecimal orderQty, String side, SymbolTimeSlice symbolSlice, boolean isAbroad, boolean isChase, BigDecimal netPos) {
        StrategyOrder strategyOrder = new StrategyOrder();
        strategyOrder.setSide(side);
        strategyOrder.setNetPosition(netPosition(netPos));
        strategyOrder.setSymbol(symbolSlice.getSymbol());
        strategyOrder.setTimeout(isChase ? config.getChaseOrderTimeout() : config.getOrderTimeoutSec());
        strategyOrder.setExchId(config.getExchId());
        strategyOrder.setDomesticType(symbolSlice.getDomesticType());
        strategyOrder.setQty(orderQty);
        strategyOrder.setStrategyId(config.getId());
        strategyOrder.setName(config.getMapName());
        strategyOrder.setParentId(config.getInstanceId());
        strategyOrder.setTraderNo(config.getTraderNo());
        strategyOrder.setOrderType(BusinessConstant.ORDER_TAG_TYPE_MKAPHEDGE);
        strategyOrder.setOrderTime(""); // Todo 回测未来用
        // 境内交易参数补充
        if (!isAbroad) {
            // 4. 获取当前报价
            BigDecimal price = calculateQuotePrice(symbolSlice.getSymbol());
            if (price == null) {
                reportException(2, "未获取到境内市场合约{}报价,不予执行下发!", symbolSlice.getSymbol());
                strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(config.getUserId(),
                        this.instanceId, this.status.get().getCode());
                return null;
            }

            // 5. 校验价格容差
            BigDecimal calculateQuotePrice = calculateQuotePrice(price, side, config.getPriceBaseType(), symbolSlice.getExchCode(), isChase);
            strategyOrder.setPrice(calculateQuotePrice);
            if (calculateQuotePrice == null) {
                reportException(2, "计算的报价容差为空,不予执行下发!", symbolSlice.getSymbol());
                strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(config.getUserId(),
                        this.instanceId, this.status.get().getCode());
                return null;
            }

            // 6. 提单价格校验
            KsdStaticQuoteInfo ksdStaticQuoteInfo = strategyContext.getKsdStaticQuoteCacheService().getByInstrumentId(symbolSlice.getSymbol());
            if (ksdStaticQuoteInfo == null) {
                reportException(2, "非白盘价格涨跌停为空,停止下发!!", symbolSlice.getSymbol());
                strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(config.getUserId(),
                        this.instanceId, this.status.get().getCode());
                return null;
            }

            BigDecimal upLimitBuffer;
            BigDecimal downLimitBuffer;
            if ("T+D".equals(symbolSlice.getContractType())) {
                downLimitBuffer = config.getTndLimitBuffer().divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_DOWN);
                upLimitBuffer = BigDecimal.ONE.subtract(config.getTndLimitBuffer().divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_DOWN));
            } else {
                downLimitBuffer = config.getSpotLimitBuffer().divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_DOWN);
                upLimitBuffer = BigDecimal.ONE.subtract(config.getSpotLimitBuffer().divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_DOWN));
            }
            // 涨跌停容忍度校验
            if (price.compareTo(ksdStaticQuoteInfo.getLowerLimitPrice().multiply(downLimitBuffer)) <= 0
                    || price.compareTo(ksdStaticQuoteInfo.getUpperLimitPrice().multiply(upLimitBuffer)) >= 0) {
                reportExceptionAndStop(1, "最新报价{}，跌停价{}，涨停价{}，不满足价格容差策略，停止策略!",
                        price, ksdStaticQuoteInfo.getLowerLimitPrice(), ksdStaticQuoteInfo.getUpperLimitPrice());
                strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(config.getUserId(),
                        this.instanceId, this.status.get().getCode());
                return null;
            }

            // 7. 首笔差价校验 (仅首次平盘有效)
            if (this.firstQuotePrice == null) {
                this.firstQuotePrice = price;
            }
            // if (price.compareTo(this.firstQuotePrice) != 0) {
            //     if (config.getChaseOrderDeviation() != null) {
            //         BigDecimal priceDeviation = price.subtract(this.firstQuotePrice).divide(this.firstQuotePrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            //         log.info("[{}] 平盘下单价格偏移:{}, 容许偏差:{}", instanceId, priceDeviation, config.getChaseOrderDeviation());
            //         if (priceDeviation.compareTo(config.getChaseOrderDeviation()) > 0) {
            //             reportExceptionAndStop(1, "首笔差价超出最大偏移容差配置，停止策略！");
            //             log.info("[{}] 首笔差价超出最大偏移容差配置，停止策略！ price:{}, firstQuotePrice:{}, config.getChaseOrderDeviation:{}",
            //                     instanceId, price, this.firstQuotePrice, config.getChaseOrderDeviation());
            //             return null;
            //         }
            //     }
            // }

            strategyOrder.setPrice(price);
            ClientMemberInfo clientMemberInfo = config.getClientMemberInfo().get(symbolSlice.getExchCode());
            strategyOrder.setCounterParty(clientMemberInfo.getMemberId());
            strategyOrder.setClientId(clientMemberInfo.getClientId());

        } else {
            PlayPrices depth = getOffshorePlayPrice(symbolSlice.getSymbol(), config.getExchId(), config.getCounterParty());
            if (depth == null) {
                reportException(2, "境外{}报价不在线!", symbolSlice.getSymbol());
                strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(config.getUserId(),
                        this.instanceId, this.status.get().getCode());
                return null;
            }
            BigDecimal spread = depth.getBestSpread();
            if (spread.compareTo(config.getMaxSpread()) > 0) {
                reportException(2, "当前点差已发生变动，超出配置阈值{}, 停止下发!", symbolSlice.getSymbol(), config.getMaxSpread());
                strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(config.getUserId(),
                        this.instanceId, this.status.get().getCode());

                return null;
            }
            strategyOrder.setExchCode(config.getExchId());
            strategyOrder.setCounterParty(config.getCounterParty());
        }
        return strategyOrder;
    }

    /**
     * 【境内】 合约计算报价
     * 针对境内合约根据基准价格类型和交易方向来计算挂单价格
     */
    private BigDecimal calculateQuotePrice(BigDecimal playPrices, String side, String priceBaseType,
                                           String exchCode, boolean isChase) {

        BigDecimal basePrice = null;
        log.info("[{}] 合约挂单配置价格策略计算", instanceId);

        // 交易方向为卖出并且配置按买入价挂单，否则按照卖出价
        // 境内合约没有深度报价，所以拿最新价
        if ("1".equalsIgnoreCase(priceBaseType)) {
            basePrice = playPrices;
        } else if ("2".equalsIgnoreCase(priceBaseType)) {
            basePrice = playPrices;
        } else if ("3".equalsIgnoreCase(priceBaseType)) {
            basePrice = playPrices;
        }

        if (basePrice == null) return null;

        // 3. 盘口点差 (spread)
        // 买入价 = 基准价 + 点差
        // 卖出价 = 基准价 - 点差
        BigDecimal finalPrice;
        BigDecimal spread = BigDecimal.ZERO;
        if (Side.BUY.equals(side)) {
            if (BusinessConstant.SM_FUTURES_EXCHANGE.equals(exchCode)) {
                spread = isChase ? config.getFutureBuyChaseSpread() : config.getFutureBidSpread();
            } else {
                spread = isChase ? config.getSpotBuyChaseSpread() : config.getSpotBidSpread();
            }
        } else {
            if (BusinessConstant.SM_FUTURES_EXCHANGE.equals(exchCode)) {
                spread = isChase ? config.getFutureSellChaseSpread() : config.getFutureOfSpread();
            } else {
                spread = isChase ? config.getSpotSellChaseSpread() : config.getSpotOfrSpread();
            }
        }

        finalPrice = basePrice.add(spread);
        return finalPrice;
    }

    public void handleSymbolChange() {
        cancelAllOrders();
        this.firstQuotePrice = null;
    }

    /**
     * 状态切换函数 (基于原子操作)
     *
     * @param expectedStatus 期望的当前状态
     * @param targetStatus   目标状态
     * @return 是否成功切换
     */
    private boolean attemptTransition(StrategyStatus expectedStatus, StrategyStatus targetStatus) {
        if (status.compareAndSet(expectedStatus, targetStatus)) {
            log.info("[{}] 状态成功从 {} -> {}", instanceId, expectedStatus, targetStatus);
            return true;
        } else {
            log.warn("[{}] 状态切换失败，预期状态: {}, 当前状态: {}", instanceId, expectedStatus, status.get());
        }
    }

    /**
     * 注册当前实例的平盘合约时间事件
     */
    private void registerTimeSlices() {
        com.cmbc.strategy.engine.core.timer.StrategyTimerService timerService = strategyContext.getStrategyTimerService();
        if (config.getSymbolTimeSlices() == null) {
            return;
        }

        timerService.clearTimeTasks(instanceId);

        for (SymbolTimeSlice slice : config.getSymbolTimeSlices()) {
            if (slice.getStartTime() == null || slice.getEndTime() == null) {
                continue;
            }

            // 每日循环调度 START
            timerService.scheduleDailyEvent(instanceId, slice.getStartTime(), new HedgeTimeSliceEvent(instanceId, slice, HedgeTimeSliceEvent.EventType.START), this);
            // 每日循环调度 PRE_CLOSE
            timerService.scheduleDailyEvent(instanceId, slice.getEndTime().minusSeconds(15), new HedgeTimeSliceEvent(instanceId, slice, HedgeTimeSliceEvent.EventType.PRE_CLOSE), this);
        }
    }

    /**
     * 时间片刷新逻辑（入口）
     */
    @Override
    public void onTimeEvent(HedgeTimeSliceEvent event) {
        // 瞬间切入异步分片线程池，绝不阻塞底层定时器线程
        if (strategyContext.getGoldHedgeEventPool() != null) {
            strategyContext.getGoldHedgeEventPool().execute(instanceId, () -> processTimeEvent(event));
        } else {
            processTimeEvent(event);
        }
    }

    /**
     * 实际处理时间事件的业务逻辑（在单线程池中安全执行）
     */
    private void processTimeEvent(HedgeTimeSliceEvent event) {
        log.info("[{}] 收到时间事件: {} for {}", instanceId, event.getType(), event.getSlice().getSymbol());
        switch (event.getType()) {
            case START:
                if (this.activeTimeSlice == null || !event.getSlice().getId().equals(this.activeTimeSlice.getId())) {
                    log.info("[{}] symbolSlice change to {}", instanceId, event.getSlice());
                    cancelAllOrders();
                    this.firstQuotePrice = null;
                }
                this.activeTimeSlice = event.getSlice();
                break;
            case PRE_CLOSE:
                this.activeTimeSlice = null; // 缓冲期内置空，阻断发单
                cancelAllOrders();
                break;
        }
    }

    // ================== 辅助方法 ==================

    private BigDecimal unitConvertUp(BigDecimal gap, BigDecimal unit) {
        return gap.divide(unit, 0, RoundingMode.UP).multiply(unit);
    }

    private BigDecimal unitConvertDown(BigDecimal gap, BigDecimal unit) {
        return gap.divide(unit, 0, RoundingMode.DOWN).multiply(unit);
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
     * 判断敞口是否在容差范围内，是的话回到监控状态
     */
    private boolean isGapSafe(BigDecimal gap, SymbolTimeSlice slice) {
        if (gap.compareTo(BigDecimal.ZERO) > 0) {
            return gap.compareTo(slice.getStartLongPosition()) <= 0;
        } else {
            return gap.abs().compareTo(slice.getEndShortPosition().abs()) <= 0;
        }
    }

    // SDK 事务钩子
    @Override
    public void onMatch(ExecutionReport executionReport) {
        log.info("[{}] 收到成交回报: {}", instanceId, executionReport);
        if (this.orderEventExecutor == null || this.status.get() == StrategyStatus.STOPPED) {
            log.warn("[{}] 已收到成交回报，但策略已停止: {}", instanceId, executionReport);
        }

        try {
            // 解析更新统计信息
            StrategyStatSummary statSummary = calMatchSummary(executionReport);
            HEDGE_STRATEGY_MAP.put(statSummary.getSymbol(), statSummary);
        } catch (Exception e) {
            reportException(2, "解析成交回报异常！", e);
        }
    }

    private StrategyStatSummary calMatchSummary(ExecutionReport executionReport) {
        // 更新统计信息到缓存中
        StrategyStatSummary statSummary = HEDGE_STRATEGY_MAP.get(executionReport.getSymbol());
        if (statSummary == null) {
            statSummary = new StrategyStatSummary(config.getUserId(), this.instanceId, executionReport.getSymbol());
            statSummary.setSide(executionReport.getSide()); // 这里买卖方向
            statSummary.setSymbol(executionReport.getSymbol());
            statSummary.setExchId(executionReport.getExchId());
            statSummary.setAvgPrice(executionReport.getAvgPx());
            statSummary.setCumWeight(executionReport.getCumQty());
            statSummary.setDomesticType(config.getDomesticType());
            if (BaseConstants.DOMESTIC_TYPE_INNER.equals(config.getDomesticType())) {
                if (BusinessConstant.SM_FUTURES_EXCHANGE.equals(executionReport.getExchId())) {
                    statSummary.setCumAmount(executionReport.getCumQty().multiply(executionReport.getAvgPx()));
                } else {
                    statSummary.setCumAmount(executionReport.getCumQty().multiply(executionReport.getAvgPx()).multiply(
                            BaseConstant.OUNCE_GRAM_UNIT));
                }
                statSummary.setAmountPrice(statSummary.getCumAmount().divide(statSummary.getCumWeight(), 4, RoundingMode.HALF_UP));
            } else {
                BigDecimal cumAmount = statSummary.getCumAmount().add(executionReport.getCumQty().multiply(executionReport.getAvgPx()));
                statSummary.setCumAmount(cumAmount);
                statSummary.setAvgPrice(statSummary.getCumAmount().divide(statSummary.getCumWeight(), 4, RoundingMode.HALF_UP));
            }
        } else {
            BigDecimal cumWeight = statSummary.getCumWeight().add(executionReport.getCumQty()); // 累计成交量
            statSummary.setCumWeight(cumWeight);
            if (BaseConstants.DOMESTIC_TYPE_INNER.equals(statSummary.getDomesticType())) {
                if (BusinessConstant.SM_FUTURES_EXCHANGE.equals(executionReport.getExchId())) {
                    statSummary.setCumAmount(statSummary.getCumAmount().add(executionReport.getCumQty().multiply(executionReport.getAvgPx())));
                } else {
                    statSummary.setCumAmount(statSummary.getCumAmount().add(executionReport.getCumQty().multiply(executionReport.getAvgPx()).multiply(
                            BaseConstant.OUNCE_GRAM_UNIT)));
                }
                statSummary.setAvgPrice(statSummary.getCumAmount().divide(statSummary.getCumWeight(), 4, RoundingMode.HALF_UP));
            } else {
                BigDecimal cumAmount = statSummary.getCumAmount().add(executionReport.getCumQty().multiply(executionReport.getAvgPx()));
                statSummary.setCumAmount(cumAmount);
                statSummary.setAvgPrice(statSummary.getCumAmount().divide(statSummary.getCumWeight(), 4, RoundingMode.HALF_UP));
            }
        }
        return statSummary;
    }

    private BigDecimal getTotalCumWeight() {
        BigDecimal totalCumWeight = BigDecimal.ZERO;
        for (StrategyStatSummary statSummary : HEDGE_STRATEGY_MAP.values()) {
            if (statSummary.getCumWeight() != null) {
                totalCumWeight = totalCumWeight.add(statSummary.getCumWeight());
            }
        }
        return totalCumWeight;
    }


    @Override
    public void onOrderCancel(ExecutionReport executionReport) {
    }

    @Override
    public void onOrderRejected(ExecutionReport executionReport) {
    }

    @Override
    public void onOrderEvent(ExecutionReport executionReport) {
    }

    public Map<String, StrategyStatSummary> getHedgeStrategyMap() { return HEDGE_STRATEGY_MAP; }

    public HedgeGoldStrategyBean getHedgeStrategyInstanceInfo() {
        PlayPrices playPrices = null;
        if (BaseConstants.DOMESTIC_TYPE_INNER.equals(activeTimeSlice.getDomesticType())) {
            playPrices = getOnshorePlayPrice(activeTimeSlice.getSymbol()); // 境内
        } else {
            playPrices = getOffshorePlayPrice(activeTimeSlice.getSymbol(),
                    config.getExchId(), config.getCounterParty()); // 境外
        }

        HedgeGoldStrategyBean goldHedgeStrategyBean = new HedgeGoldStrategyBean(config,
                this.instanceId, HEDGE_STRATEGY_MAP, this.positionSnapshot, activeTimeSlice,
                this.getPendingOrder(), playPrices, chaseNumber);
        
        goldHedgeStrategyBean.setInstanceId(instanceId);
        goldHedgeStrategyBean.setStatusCode(this.status.get().getCode());
        goldHedgeStrategyBean.setStatusDesc(this.status.get().getDescription());
        return goldHedgeStrategyBean;
    }

    public void updateStatusAsync(Integer level, String message) {
        strategyContext.getExecutorService().execute(() -> {
            strategyContext.getGoldHedgeStrategyNotificationService().pushExceptionInfo(instanceId, level, message);
        });
    }

    public void updateStatusAsync(String reason) {
        strategyContext.getExecutorService().execute(() -> {
            strategyContext.getGoldHedgeStrategyInstanceService().updateStrategyInstanceStatus(config.getUserId(),
                    instanceId, this.status.get().getCode(), reason);
        });
    }

    private void reportException(Integer level, String message, Object... args) {
        String msg = MessageFormatter.arrayFormat(message, args).getMessage();
        Throwable throwable = MessageFormatter.getThrowableCandidate(args);

        if (throwable != null) {
            log.error("[{}] {}", this.instanceId, msg, throwable);
        } else {
            if (level == 3) {
                log.error("[{}] {}", this.instanceId, msg);
            } else if (level == 2) {
                log.warn("[{}] {}", this.instanceId, msg);
            } else {
                log.info("[{}] {}", this.instanceId, msg);
            }
        }
        updateStatusAsync(level, msg);
    }

    private void reportExceptionAndStop(Integer level, String message, Object... args) {
        reportException(level, message, args);
        this.stop(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    public void pushExceptionInfo(Integer level, String message){
        if (strategyContext.getGoldHedgeIoPool() != null) {
            strategyContext.getGoldHedgeIoPool().execute(instanceId, () -> {
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
        if (strategyContext.getGoldHedgeIoPool() != null) {
            strategyContext.getGoldHedgeIoPool().execute(instanceId, () -> {
                strategyContext.getHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(
                        config.getUserId(), instanceId, this.status.get().getCode(), null);
            });
        } else {
            strategyContext.getHedgeStrategyPushService().pushStrategyStatus(
                    config.getUserId(), instanceId, String.valueOf(latestStatus.getCode()), null);
        }
    }
    /**
     * 获取策略实例自启动以来的总成交重量（克）
     */
    private BigDecimal getTotalCumWeight() {
        BigDecimal total = BigDecimal.ZERO;
        for (StrategyStatSummary summary : HEDGE_STRATEGY_MAP.values()) {
            if (summary.getCumWeight() != null) {
                total = total.add(summary.getCumWeight());
            }
        }
        return total;
    }


}
