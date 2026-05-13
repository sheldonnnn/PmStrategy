package com.cmbc.strategy.service.instance;

import com.cmbc.oms.constant.BusinessConstant;
import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.domain.exposure.dto.StrategyPosition;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.strategy.constant.*;
import com.cmbc.strategy.domain.entity.HedgeStrategyInstanceEntity;
import com.cmbc.strategy.domain.model.StrategyStatSummary;
import com.cmbc.strategy.domain.model.market.Depth;
import com.cmbc.strategy.domain.model.market.PloyPrices;
import com.cmbc.strategy.domain.model.order.NewOrder;
import com.cmbc.strategy.domain.model.order.OrderReport;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.service.StrategyContext;
import com.cmbc.strategy.service.hedge.HedgeTrigger;
import com.cmbc.strategy.service.OrderAlgoService;
import com.cmbc.strategy.util.OrderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


    /**
     * 套利策略实例实现类
     */
@Slf4j
public class HedgeStrategyInstance extends BaseStrategy<HedgeStrategyConfig> {

        // === 内部变量与组件 ===
        private final Map<String, StrategyStatSummary> HEDGE_STRATEGY_MAP = new ConcurrentHashMap<>();
        private final HedgeTrigger triggerEvaluator;
        protected final AtomicReference<StrategyStatus> status = new AtomicReference<>(StrategyStatus.CREATED);
        private ExecutorService orderEventExecutor;

        private volatile SymbolTimeSlice activeTimeSlice;
        private long hedgingStartTime;          // 平盘开始时间
        private long chaseStartTime;            // 追单开始时间
        private Integer chaseNumber = 0;

        // === 定时任务句柄 ===
        private ScheduledFuture<?> monitoringTaskHandle;
        private ScheduledFuture<?> executionTaskHandle;
        private ScheduledFuture<?> chaseTaskHandle;
        private ScheduledFuture<?> strategyMonitorPushTask;

        public HedgeStrategyInstance(HedgeStrategyConfig config, String instanceId, HedgeTrigger triggerEvaluator, StrategyContext strategyContext) {
            super(config, instanceId, strategyContext);
            this.triggerEvaluator = triggerEvaluator;
            this.status.set(StrategyStatus.CREATED);
        }

        // ============================== 生命周期实现 ==============================

        @Override
        public void start() {
            // 1. 初始化
            HedgeStrategyInstanceEntity entity = new HedgeStrategyInstanceEntity();
            strategyContext.getGoldHedgeStrategyInstanceService().insertStrategyInstanceStatus(entity);
            log.info("[{}] HedgeStrategy is starting...", instanceId);

            // 初始化事件执行器
            this.orderEventExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Order-event-executor-" + instanceId);
                t.setDaemon(true);
                return t;
            });

            // 初始化全局变量缓存
            this.HEDGE_STRATEGY_MAP.clear();
            List<SubscribeRequest> subReqs = collectSubscriptions(config.getCounterParty(), config.getExchId());
            this.subscribe(subReqs, config.getUserId());

            // 2. 启动“监控模式”
            startMonitoringPhase();

            // 3. 策略行情信息定时推送
            this.strategyMonitorPushTask = schedule(this::pushStrategyMonitorInfo, 1000L);
        }

        @Override
        public void stop() {
            log.info("[{}] HedgeStrategy is Stopping...", instanceId);
            status.set(StrategyStatus.STOPPED);

            // 1. 停止所有内部定时任务
            stopTask(monitoringTaskHandle);
            stopTask(executionTaskHandle);
            stopTask(chaseTaskHandle);
            stopTask(strategyMonitorPushTask);

            // 2. 撤销策略关联的所有有效订单
            cancelAllOrders();

            // 3. 停止订单处理执行器
            if (this.orderEventExecutor != null && !this.orderEventExecutor.isShutdown()) {
                this.orderEventExecutor.shutdown();
                try {
                    if (!this.orderEventExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                        this.orderEventExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    this.orderEventExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // 更新状态到持久化服务
            strategyContext.getGoldHedgeStrategyInstanceService().updateStrategyInstanceStatus(
                    config.getUserId(), instanceId, String.valueOf(StrategyStatus.STOPPED.getCode()));
        }

        @Override
        public void pause() {
            if (isRunning()) {
                if (status.compareAndSet(status.get(), StrategyStatus.PAUSED)) {
                    stopTask(monitoringTaskHandle);
                    stopTask(executionTaskHandle);
                    strategyContext.getGoldHedgeStrategyInstanceService().updateStrategyInstanceStatus(
                            config.getUserId(), instanceId, String.valueOf(StrategyStatus.PAUSED.getCode()));
                }
            } else {
                log.warn("[{}] 当前状态为 {}, 无法执行暂停。", instanceId, status.get());
            }
        }

        @Override
        public void resume() {
            if (status.get() == StrategyStatus.PAUSED) {
                log.info("[{}] 收到管理端恢复指令，正在恢复...", instanceId);
                startMonitoringPhase();
            } else {
                log.warn("[{}] 当前状态为 {}, 无法执行恢复。", instanceId, status.get());
            }
        }

        // ============================== 核心逻辑模块 ==============================

        /**
         * 阶段一：启动监控任务
         */
        private void startMonitoringPhase() {
            if (!attemptTransition(status.get(), StrategyStatus.MONITOR)) {
                log.error("[{}] 平盘监控切换失败：状态不满足，当前状态：{}", instanceId, this.status.get());
                return;
            }
            log.info("[{}] Switch to MONITOR phase.", instanceId);
            strategyContext.getGoldHedgeStrategyInstanceService().updateStrategyInstanceStatus(
                    config.getUserId(), instanceId, String.valueOf(StrategyStatus.MONITOR.getCode()));

            stopTask(executionTaskHandle);
            stopTask(chaseTaskHandle);

            this.monitoringTaskHandle = schedule(this::runMonitoringLogic, 1000L);
        }

        /**
         * 监控逻辑：检查是否触发平盘条件
         */
        private void runMonitoringLogic() {
            if (status.get() != StrategyStatus.MONITOR) {
                log.warn("[{}] 执行监控任务跳过：当前状态不是 MONITOR (Actual: {})", instanceId, status.get());
                return;
            }
            try {
                // 获取最新持仓
                StrategyPosition positionSummary = getClientPosition();
                BigDecimal clientPos = positionSummary.getMgapNetPosition();
                BigDecimal hedgedPos = positionSummary.getFrozenNetPosition();
                BigDecimal openPos = positionSummary.getHedgedNetPosition();

                SymbolTimeSlice activeSymbolSlice = getOrRefreshActiveSlice();
                if (activeSymbolSlice == null) {
                    stop();
                    return;
                }

                // 触发器校验
                boolean signal = triggerEvaluator.evaluate(clientPos, hedgedPos, openPos, activeSymbolSlice);

                if (signal) {
                    log.info("[{}] trigger hedging, current clientPos:{}, hedgedPos:{}, triggerLimit:{}",
                            instanceId, clientPos, hedgedPos, activeSymbolSlice);
                    switchToExecution();
                }
            } catch (Exception e) {
                log.error("Monitoring error: ", e);
            }
        }

        /**
         * 阶段二：启动平盘逻辑
         */
        private void switchToExecution() {
            if (!attemptTransition(StrategyStatus.MONITOR, StrategyStatus.HEDGE)) {
                log.error("[{}] 拒绝平盘触发：状态不满足，当前状态：{}", instanceId, this.status.get());
                return;
            }
            stopTask(monitoringTaskHandle);
            log.info("[{}] Switch to EXECUTION phase.", instanceId);
            this.hedgingStartTime = System.currentTimeMillis();

            // 按照配置的时间间隔执行平盘逻辑
            this.executionTaskHandle = schedule(this::runExecutionLogic,
                    config.getOrderIntervalSec().multiply(BigDecimal.valueOf(1000)).longValue());
        }

        private void runExecutionLogic() {
            if (status.get() != StrategyStatus.HEDGE) {
                log.warn("[{}] Start Hedging failed! Current status is not HEDGING (Actual: {})", instanceId, status.get());
                return;
            }

            // 1. 获取当前时间片配置与持仓
            SymbolTimeSlice currentSlice = getOrRefreshActiveSlice();
            if (currentSlice == null) return;

            long timeOutMs = config.getHedgingMaxTime().multiply(BigDecimal.valueOf(1000)).longValue();
            StrategyPosition positionSummary = getClientPosition();
            BigDecimal clientPos = positionSummary.getMgapNetPosition();
            BigDecimal netPos = clientPos.add(positionSummary.getHedgedNetPosition());

            if (isGapSafe(netPos, currentSlice)) {
                log.info("[{}] Position is safe. NetPos: {}", instanceId, netPos);
                // 尝试转回监控或执行收尾
                if (attemptTransition(StrategyStatus.HEDGE, StrategyStatus.MONITOR)) {
                    cancelAllOrders();
                    startMonitoringPhase();
                }
            } else if (System.currentTimeMillis() - hedgingStartTime > timeOutMs) {
                // 超时处理：切换到追单阶段
                log.info("[{}] 平盘执行超时，触发追单!", instanceId);
                switchToChase();
            } else {
                // 继续执行平盘逻辑（下单逻辑在此封装）
                handleHedgingExecution(currentSlice, positionSummary);
            }
        }

        /**
         * 阶段三：启动追单处理
         */
        private void switchToChase() {
            if (!attemptTransition(StrategyStatus.HEDGE, StrategyStatus.CHASE)) {
                log.error("[{}] 拒绝追单：状态不满足，当前状态：{}", instanceId, this.status.get());
                return;
            }
            log.info("[{}] Switch to Chase phase.", instanceId);
            stopTask(executionTaskHandle);

            // 触发追单提醒发送到Web端
            strategyContext.getGoldHedgeStrategyWebSocketService().sendChasingRequest(this.instanceId, config.getUserId());

            this.chaseStartTime = System.currentTimeMillis();
            this.chaseTaskHandle = schedule(this::runChaseLogic,
                    config.getOrderIntervalSec().multiply(BigDecimal.valueOf(1000)).longValue());
        }

        // ============================== 辅助工具方法 ==============================

        private List<SubscribeRequest> collectSubscriptions(String counterParty, String exchId) {
            List<SubscribeRequest> subscribeRequests = new ArrayList<>();
            if (config.getSymbolTimeSlices() == null) return subscribeRequests;

            for (SymbolTimeSlice slice : config.getSymbolTimeSlices()) {
                if (slice.getSymbol() == null) continue;

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
            return subscribeRequests;
        }

        private void pushStrategyMonitorInfo() {
            try {
                StrategyPosition positionSummary = getClientPosition();
                // 拼装监控信息并通过 WebSocket 推送
                strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyMap(
                        config.getUserId(), this.instanceId, HEDGE_STRATEGY_MAP, positionSummary);
            } catch (Exception e) {
                log.error("sendGoldHedgeStrategyInfo error", e);
            }
        }

        private boolean attemptTransition(StrategyStatus from, StrategyStatus to) {
            return status.compareAndSet(from, to);
        }

            /**
             * 追单逻辑执行
             */
            private void runChaseLogic() {
                try {
                    BigDecimal netPos = clientPos.add(hedgedPos); // 不包含挂单头寸
                    // === 分支 A：敞口安全，平盘结束 ===
                    if (isGapSafe(netPos, currentSlice)) {
                        log.info("[{}] Position is safe, NetPos: {}", instanceId, netPos);

                        // 尝试流转：HEDGE -> MONITOR
                        if (attemptTransition(StrategyStatus.HEDGE, StrategyStatus.MONITOR)) {
                            // 只有流转成功才执行收尾动作
                            cancelAllOrders(); // 撤销剩余挂单
                            startMonitoringPhase(); // 重启监控任务
                        }
                    } else if (System.currentTimeMillis() - this.chaseStartTime > timeOutMs || chaseNumber > config.getChaseNumber()) {
                        // 分支 B：判断是否超时或追单次数超限
                        log.info("[{}] 追单执行超时或追单次数超出阈值 (已耗时: {}ms, 阈值: {}ms; 追单次数: {}, 阈值: {}), 触发告警!",
                                instanceId, System.currentTimeMillis() - hedgingStartTime, timeOutMs, chaseNumber, config.getChaseNumber());
                        stop();
                        // todo: 告警处理
                    } else {
                        // 分支 C：执行下单逻辑
                        handleHedgingExecution(currentSlice, clientPos, hedgedPos, openPos, true);
                        // 统计追单次数
                        chaseNumber += 1;
                    }
                } catch (Exception e) {
                    log.error("[{}] 追单处理异常!!!", this.instanceId, e);
                }
            }

            // ============================================================
            // 3. 下单处理逻辑（分流与报价）
            // ============================================================

            private void handleHedgingExecution(SymbolTimeSlice symbolSlice, BigDecimal clientPos, BigDecimal hedgedPos, BigDecimal openPos, boolean isChase) {
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

                // 境内合约最大下单量控制
                if (!isAbroad) {
                    if ("2".equals(symbolSlice.getContractType())) { // 期货合约
                        maxOrderQty = isChase ? config.getChaseFutureMaxOrderQty() : config.getFutureMaxOrderQty();
                    } else {
                        maxOrderQty = isChase ? config.getChaseSpotMaxOrderQty() : config.getSpotMaxOrderQty();
                    }
                    orderWeight = orderWeight.min(maxOrderQty); // 下单量不超过配置中单笔最大下单量
                    // 计算下单量
                    orderQty = unitConvert(orderWeight, symbolSlice.getUnit());
                } else {
                    maxOrderQty = isChase ? config.getChaseXauMaxOrderQty() : config.getXauMaxOrderQty();
                    orderQty = unitConvert(orderWeight, BusinessConstant.OUNCE_GRAM).min(maxOrderQty);
                }

                // 2. 校验：敞口是否满足最小下单单位（1手）
                if (orderQty.compareTo(BigDecimal.ONE) < 0) {
                    log.warn("[{}] 敞口不足1手，忽略下单。orderWeight: {}, Unit: {}, symbol: {}",
                            instanceId, orderWeight, symbolSlice.getUnit(), symbolSlice.getSymbol());
                    strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(
                            config.getUserId(), this.instanceId, String.valueOf(this.status.get().getCode()));
                    return;
                }

                if (!status.get().canTrade()) {
                    log.warn("[{}] Strategy not running, reject order.", instanceId);
                    return;
                }

                // 组装名单
                StrategyOrder strategyOrder = buildStrategyOrder(orderQty, side, symbolSlice, isAbroad, isChase);
                if (strategyOrder == null) {
                    log.warn("[{}] 构建名单失败。忽略下单。orderWeight: {}, symbol: {}", instanceId, orderWeight, symbolSlice.getSymbol());
                    strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(
                            config.getUserId(), this.instanceId, String.valueOf(this.status.get().getCode()));
                    return;
                }

                sendStrategyOrder(strategyOrder);
                log.info("[{}] 发送策略订单: {}", instanceId, strategyOrder);
                strategyContext.getGoldHedgeStrategyWebSocketService().sendGoldHedgeStrategyStatus(
                        config.getUserId(), this.instanceId, String.valueOf(this.status.get().getCode()));
            }

            private StrategyOrder buildStrategyOrder(BigDecimal orderQty, Side side, SymbolTimeSlice symbolSlice, boolean isAbroad, boolean isChase) {
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
                strategyOrder.setBusinessType(BusinessConstant.ORDER_TAG_TYPE_MGAPHEDGE); // todo 业务类型

                if (!isAbroad) {
                    // A. 获取行情快照
                    PloyPrices depth = getOnshorePloyPrice(symbolSlice.getSymbol());
                    if (depth == null) {
                        log.error("[{}] 行情缺失，无法下单: {}", instanceId, symbolSlice.getSymbol());
                        return null;
                    }

                    // B. 计算价格
                    BigDecimal price = calculateQuotePrice(depth, side, config.getGetPriceBaseType(), symbolSlice.getContractType(), isChase);
                    if (price == null) {
                        log.warn("[{}] 计算报价失败!!", instanceId);
                        return null;
                    }

                    KsdStaticQuoteInfo ksdStaticQuoteInfo = strategyContext.getKsdStaticQuoteCacheService().getByInstrumentId(symbolSlice.getSymbol());
                    if (ksdStaticQuoteInfo == null) {
                        log.warn("[{}] 涨跌停价格为空，禁止下单!!", instanceId);
                        return null;
                    }

                    if (price.compareTo(ksdStaticQuoteInfo.getLowerLimitPrice()) < 0 || price.compareTo(ksdStaticQuoteInfo.getUpperLimitPrice()) > 0) {
                        log.warn("[{}] 报价未在涨跌停缓冲区范围内，禁止下单!!", instanceId);
                        return null;
                    }

                    strategyOrder.setPrice(price);
                    strategyOrder.setClientMemberInfo(config.getClientMemberInfo().get(symbolSlice.getExchCode()));
                    strategyOrder.setMemberId(config.getMemberId());
                    strategyOrder.setClientId(config.getClientId());
                } else {
                    PloyPrices depth = getOffshorePloyPrice(symbolSlice.getSymbol(), config.getExchId(), config.getCounterParty());
                    if (depth == null) {
                        log.error("[{}] 行情缺失，无法下单: {}", instanceId, symbolSlice.getSymbol());
                        return null;
                    }
                    // 境外逻辑... (图片截断)
                }
                return strategyOrder;
            }

            /**
             * [细化] 价格计算引擎
             */
            private BigDecimal calculateQuotePrice(PloyPrices ployPrices, Side side, String priceBaseType, String contractType, boolean isChase) {
                BigDecimal basePrice = null;

                // 1. 获取基准价 (Base Price)
                // 对于对手最优：吃对手价（买入看卖一，卖出看买一）
                if (isChase || "1".equals(priceBaseType)) {
                    basePrice = (side == Side.BUY) ? ployPrices.getBestAskPx() : ployPrices.getBestBidPx(); // 直接取对手最优位
                } else if ("2".equals(priceBaseType)) {
                    // N (Neutral/中性)：中间价
                    basePrice = ployPrices.getMidPx();
                }

                if (basePrice == null) return null;

                // 2. 叠加点差 (Spread)
                BigDecimal spread;
                if (side == Side.BUY) {
                    if ("2".equals(contractType)) {
                        spread = isChase ? config.getFutureBuyChaseSpread() : config.getFutureBidSpread();
                    } else {
                        spread = isChase ? config.getSpotBuyChaseSpread() : config.getSpotBidSpread();
                    }
                } else {
                    if ("2".equals(contractType)) {
                        spread = isChase ? config.getFutureOfrSpread() : BigDecimal.ZERO;
                    } else {
                        spread = isChase ? config.getSpotOfrSpread() : BigDecimal.ZERO;
                    }
                }

                return basePrice.add(spread);
            }

            /**
             * 时间片刷新逻辑
             */
            private SymbolTimeSlice getOrRefreshActiveSlice() {
                LocalTime now = LocalTime.now();
                // Step 1: 快速检查 (Fast Path)
                if (activeTimeSlice != null && now.isBefore(this.activeTimeSlice.getEndTime().minusSeconds(15)) && now.isAfter(activeTimeSlice.getStartTime())) {
                    return activeTimeSlice;
                }
                // Step 2: 进入缓冲期或重新查找
                return refreshActiveTimeSlice(now);
            }

            private synchronized SymbolTimeSlice refreshActiveTimeSlice(LocalTime now) {
                // 从 Config List 查找当前时间片并更新缓存
                SymbolTimeSlice newSlice = config.findSlice(now);
                if (newSlice != null) {
                    if (this.activeTimeSlice == null || !newSlice.getId().equals(this.activeTimeSlice.getSliceId())) {
                        log.info("[{}] symbolSlice change to {}", instanceId, newSlice);
                        // 关键：如果场合发生了变化，需要执行相关业务操作，如撤单
                        handleSymbolChange(activeTimeSlice, newSlice);
                    }
                    this.activeTimeSlice = newSlice;
                }
                return activeTimeSlice;
            }
        }

        // ---------------- 定时任务与状态管理 ----------------

        private synchronized SymbolTimeSlice refreshActiveTimeSlice(LocalTime now) {
            // ... (省略部分逻辑)
            // 更新缓存
            this.activeTimeSlice = newSlice;
            // ...
            return this.activeTimeSlice;
        }

        public void handleSymbolChange() {
            // 1. 撤单
            cancelAllOrders();
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
            return gap.divide(unit, BigDecimal.ROUND_UP); // 向上取整，最多平超一手
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
            if (this.orderEventExecutor == null || this.status.get() == StrategyStatus.STOPPED) {
                log.warn("[{}] 收到成交事件，但策略已停止，丢弃事件。", instanceId);
            }

            this.orderEventExecutor.execute(() -> {
                try {
                    // 获取并更新合约汇总信息
                    StrategyStatSummary statSummary = calMatchSummary(executionReport);
                    HEDGE_STRATEGY_MAP.put(statSummary.getSymbol() + ":" + statSummary.getSide(), statSummary);
                } catch (Exception e) {
                    log.error("[{}] 处理成交事件异常！event: {}", instanceId, executionReport, e);
                }
            });
        }

        /**
         * 合约信息统一计算内部调用
         *
         * @param executionReport
         */
        private StrategyStatSummary calMatchSummary(ExecutionReport executionReport) {
            // 获取缓存合约详细信息
            StrategyStatSummary statSummary = HEDGE_STRATEGY_MAP.get(executionReport.getSymbol() + ":" + executionReport.getSide());

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

            if (BusinessConstant.DOMESTIC_TYPE_INNER.equals(statSummary.getDomesticType())) {
                // 境内
                statSummary.setCumWeight(statSummary.convertToWeight(statSummary.getCumQty(), executionReport.getUnit())); // 统一单位换算
                statSummary.setMktPrice(getOnshorePloyPrice(executionReport.getSymbol()).getMidPx());
            } else {
                // 境外
                statSummary.setCumWeight(statSummary.convertToWeight(statSummary.getCumQty(), BaseConstants.OUNCE_GRAM_UNIT)); // 统一单位换算
                statSummary.setFxRate(getOffshorePloyPrice("USD/CNH", config.getExchId(), config.getCounterParty()).getMidPx()); // todo 汇率获取
                statSummary.setMktPrice(getOffshorePloyPrice(executionReport.getSymbol(), config.getExchId(), config.getCounterParty()).getMidPx());
            }

            statSummary.setAvgPrice(statSummary.getCumAmount().divide(statSummary.getCumWeight(), 3, BigDecimal.ROUND_HALF_UP));
            statSummary.setCumPendingWeight(statSummary.convertToWeight(statSummary.getCumPendingQty(), executionReport.getUnit()));

            return statSummary;
        }

        @Override
        public void onRtnOrder(ExecutionReport executionReport) {
            log.info("[{}] receive order confirm: {}", instanceId, executionReport);
            StrategyStatSummary orderQtyResult = calOrderRtnSummary(executionReport);
            HEDGE_STRATEGY_MAP.put(executionReport.getSymbol() + ":" + executionReport.getSide(), orderQtyResult);
        }

        @Override
        public void onOrderRejected(ExecutionReport executionReport) {
            // ...
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
            if (BusinessConstant.DOMESTIC_TYPE_INNER.equals(activeTimeSlice.getDomesticType())) {
                // 境内
                onshorePloyPrice = getOnshorePloyPrice(activeTimeSlice.getSymbol());
            } else {
                onshorePloyPrice = getOffshorePloyPrice(activeTimeSlice.getSymbol(), config.getExchId(), config.getCounterParty());
            }

            GoldStrategyBean goldHedgeStrategyInstanceInfo = strategyContext.getGoldHedgeStrategyInstanceService().getGoldHedgeStrategyInstanceInfo(config.getUserId(), instanceId);
            if (null == goldHedgeStrategyInstanceInfo) {
                goldHedgeStrategyInstanceInfo = new GoldStrategyBean();
            }
            goldHedgeStrategyInstanceInfo.setInstanceId(instanceId);
            goldHedgeStrategyInstanceInfo.setStatus(StrategyStatus.fromStatusCode(status.get().getCode()));
            goldHedgeStrategyInstanceInfo.setMessage(StrategyStatus.fromStatusCode(status.get().getCode()).getFinDescription());

            return goldHedgeStrategyInstanceInfo;
        }



}