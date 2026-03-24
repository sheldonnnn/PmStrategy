package com.cmbc.strategy.service.instance;

import com.cmbc.strategy.constant.*;
import com.cmbc.strategy.domain.model.market.Depth;
import com.cmbc.strategy.domain.model.market.PloyPrices;
import com.cmbc.strategy.domain.model.market.PriceProviderInfo;
import com.cmbc.strategy.domain.model.order.NewOrder;
import com.cmbc.strategy.domain.model.order.OrderReport;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.service.HedgeTrigger;
import com.cmbc.strategy.service.OrderAlgoService;
import com.cmbc.strategy.util.OrderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 积存金自动平盘策略实现类
 * 继承 BaseStrategy，直接获得 SDK 的所有能力
 */
@Slf4j
public class HedgeStrategyInstance extends BaseStrategy<HedgeStrategyConfig> {

    // === 业务组件 ===
    private final HedgeTrigger triggerEvaluator;
    private final OrderAlgoService algoService;
    // 订单的单线程执行器 (事件循环队列)
    private ExecutorService orderEventExecutor;

    protected final AtomicReference<StrategyStatus> status = new AtomicReference<>(StrategyStatus.CREATED);
    @Autowired
    protected TaskScheduler taskScheduler;
    /** 当前生效的时间片配置 (缓存) */
    private volatile SymbolTimeSlice activeTimeSlice;

    private long hedgingStartTime;                // 平盘开始时间 (用于超时判断)

//    // === 内部状态 ===
//    private volatile StrategyRuntimeContext currentContext;

    // === 双定时任务句柄 ===
    private ScheduledFuture<?> monitoringTaskHandle;
    private ScheduledFuture<?> executionTaskHandle;

    public HedgeStrategyInstance(HedgeStrategyConfig config,
                                    String instanceId,
                                    HedgeTrigger triggerEvaluator,
                                    OrderAlgoService algoService) {
        super(config, instanceId); // 初始化基类
        this.triggerEvaluator = triggerEvaluator;
        this.algoService = algoService;
    }


    // ================== 生命周期钩子实现 ==================

    @Override
    public void start() {
        // 1. 初始化业务上下文
        if (status.compareAndSet(StrategyStatus.CREATED, StrategyStatus.MONITORING) ||
                status.compareAndSet(StrategyStatus.STOPPED, StrategyStatus.MONITORING)) {
            log.info("[{}] HedgeStrategy is starting...", instanceId);
            // 初始化单线程执行器，并自定义线程名称，方便生产环境排查日志
            this.orderEventExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "strategy-event-loop-" + instanceId + "-" + counter.getAndIncrement());
                    t.setDaemon(true); // 设为守护线程
                    return t;
                }
            });
//            this.subscribeMarketData(config.getSymbolList(),providerList); //todo 订阅格式待定
            // 2. 启动"监控模式" (第一个定时任务)
            startMonitoringPhase();
            return;
        }
        throw new IllegalStateException("Strategy is not in CREATED or STOPPED state.");

    }

    @Override
    public void stop() {
        log.info("[{}] HedgeStrategy is Stopping...", instanceId);
        status.set(StrategyStatus.STOPPED);

        // 1. 自动执行通用的安全动作
        cancelAllOrders();

        // 停止所有内部定时任务
        stopTask(monitoringTaskHandle);
        stopTask(executionTaskHandle);
        // BaseStrategy 会自动调用 cancelAllOrders，这里无需重复
    }

    @Override
    public void pause() {
        // 确保只有在运行状态下才能暂停
        if (isRunning()) {
            if (status.compareAndSet(status.get(), StrategyStatus.PAUSED)) {
                log.info("[{}] 收到管理端中止指令，正在暂停...", instanceId);

                // 1. 停止内部定时任务
                stopTask(monitoringTaskHandle);
                stopTask(executionTaskHandle);

                log.info("[{}] 策略已中止(PAUSED)。", instanceId);
            }
        } else {
            log.warn("[{}] 当前状态为 {}，无法执行暂停。", instanceId, status.get());
        }
    }

    @Override
    public void resume() {

    }

//    /**
//     * 核心订阅解析逻辑
//     * 支持:
//     * 1. 简单模式: SGE, DEFAULT
//     * 2. 直连模式: UBS_DIRECT, UBS
//     * 3. 聚合模式: FXALL, "BOA,GS,JPM"
//     */
//    private Set<MarketDataRequest> collectUniqueSubscriptions() {
//        Set<MarketDataRequest> distinctRequests = new HashSet<>();
//
//        if (config.getTimeSlices() == null) return distinctRequests;
//
//        for (TimeSliceConfig slice : config.getTimeSlices()) {
//            // 基础防空
//            if (slice.getSymbol() == null || slice.getExchange() == null) continue;
//
//            String venue = slice.getExchange().trim();     // 去空格
//            String rawProviders = slice.getCounterParty(); // 获取对手方配置
//
//            // 1. 解析对手方列表
//            List<String> providers = new ArrayList<>();
//            if (rawProviders == null || rawProviders.trim().isEmpty()) {
//                providers.add("DEFAULT"); // 缺省值
//            } else {
//                // 关键: 按逗号拆分，并去除每一项的空格
//                String[] split = rawProviders.split(",");
//                for (String p : split) {
//                    if (!p.trim().isEmpty()) {
//                        providers.add(p.trim());
//                    }
//                }
//            }
//
//            // 2. 裂变生成原子订阅请求
//            for (String provider : providers) {
//                MarketDataRequest req = MarketDataRequest.builder()
//                        .symbol(slice.getSymbol())
//                        .venue(venue)       // UBS_DIRECT 或 FXALL
//                        .provider(provider) // UBS 或 BOA 或 GS
//                        .build();
//
//                // 加入 Set 进行自动去重
//                distinctRequests.add(req);
//            }
//        }
//        return distinctRequests;
//    }

    // ================== 核心状态机逻辑 (双任务切换) ==================

    /**
     * 阶段一：启动监控任务
     */
    private void startMonitoringPhase() {
        stopTask(executionTaskHandle); // 确保互斥
        log.info("[{}] Start to MONITORING phase.", instanceId);

        // 使用 SDK 的 scheduleTask 能力，每3秒执行一次
        this.monitoringTaskHandle = taskScheduler.scheduleWithFixedDelay(this::runMonitoringLogic, java.time.Duration.ofMillis(1000));
    }

    /**
     * 阶段二：启动平盘任务
     */
    private void switchToExecution() {
        if (!attemptTransition(StrategyStatus.MONITORING, StrategyStatus.HEDGING)) {
            log.error("[{}] 拒绝平盘触发: 状态不满足，当前状态：{}", instanceId, this.status);
            return;
        }
        stopTask(monitoringTaskHandle); // 确保互斥
        log.info("[{}] Switch to EXECUTION phase.", instanceId);
        // 记录平盘开始时间，用于后续超时判断
        this.hedgingStartTime = System.currentTimeMillis();
//        // 1. 立即发出第一笔母单 (通过 SDK 封装的 algoService)
//        // 注意：这里我们假设 algoService 最终也会调用 SDK 的 submitOrder
//        algoService.submitParentOrder(instanceId, signal, currentContext);

        // 2. 启动执行监控，每1秒检查一次是否平完了
        this.executionTaskHandle = taskScheduler.scheduleWithFixedDelay(this::runExecutionLogic, java.time.Duration.ofMillis(config.getOrderIntervalSec().multiply(BigDecimal.valueOf(1000)).longValue()));

    }
    
    // 阶段三：启动追单处理  todo
    private void switchToChase() {
        
    }

    //平盘监控

    private void runMonitoringLogic() {
        if (status.get() != StrategyStatus.MONITORING) {
            log.warn("[{}] 执行监控任务跳过: 当前状态不是 MONITORING (Actual: {})", instanceId, status.get());
            return;
        }
        try {
            // 1. 获取数据 (通过 SDK Context)
            BigDecimal clientPos = positionService.getClientPosition();
            BigDecimal hedgedPos = positionService.getHedgedPosition();
            BigDecimal openOrders = positionService.getActiveExposure();

            // 2. 计算触发 (直接使用 config 中的阈值)
            boolean signal = triggerEvaluator.evaluate(clientPos, hedgedPos, openOrders, activeTimeSlice);

            // 3. 触发切换
            if (signal) {
                log.info("[{}] trigger，clientPos：{}, current hedgedPos:{},openOrders:{}", instanceId,clientPos,hedgedPos,openOrders);
                switchToExecution();
            }
        } catch (Exception e) {
            log.error("Monitoring error:", e);
        }
    }

    private void runExecutionLogic() {

        try {
            if (status.get() != StrategyStatus.HEDGING) {
                log.warn("[{}] Start Hedging failed! Current status is not HEDGING (Actual: {})", instanceId, status.get());
                return;
            }
            // 1. 获取当前时间片配置
            SymbolTimeSlice currentSlice = getOrRefreshActiveSlice();
            if (currentSlice == null) {
//                forceStopExecution();    //停止策略 or 切换为监控状态？ todo
                return;
            }
            // 2. 超时检查逻辑
            long timeOutMs = config.getHedgingMaxTime().multiply(BigDecimal.valueOf(60000)).longValue();;
            BigDecimal netPos = positionService.getClientPosition().add(positionService.getHedgedPosition());
            // === 分支 A: 敞口安全，平盘结束 ===
            if (isGapSafe(netPos,currentSlice)) {
                log.info("[{}] Position is safe，NetPos：{}.", instanceId, netPos);

                // 尝试流转: HEDGING -> MONITORING
                if (attemptTransition(StrategyStatus.HEDGING, StrategyStatus.MONITORING)) {
                    // 只有流转成功才执行收尾动作
                    cancelAllOrders(); // 撤销剩余挂单
                    startMonitoringPhase(); // 重启监控任务
                }
            }else if(System.currentTimeMillis() - hedgingStartTime > timeOutMs) {             //分支B：判断是否需要进入追单
                log.info("[{}] 平盘执行超时 (已耗时 {}ms > 配置 {}ms)，触发追单!", instanceId, System.currentTimeMillis() - hedgingStartTime, timeOutMs);
                switchToChase();    //todo
                return;
            }else {               //分支C：执行下单逻辑
                handleHedgingExecution(currentSlice);
            }
        } catch (Exception e) {
            log.error("Execution error", e);
        }
    }

    // ==========================================
    // 3. 下单处理逻辑 (分流与报价)
    // ==========================================

    private void handleHedgingExecution(SymbolTimeSlice symbolSlice) {

        // 包含挂单敞口的净头寸，单位：kg
        BigDecimal netPos = positionService.getClientPosition().add(positionService.getHedgedPosition()).add(positionService.getActiveExposure());

        //1、确定买卖方向。计算下单量
        BigDecimal gap;
        Side side;
        if(netPos.compareTo(BigDecimal.ZERO) > 0) {
            gap = netPos.subtract(symbolSlice.getEndLongPosition());        //单位为kg
            side = Side.SELL;
        }else{
            gap = symbolSlice.getEndShortPosition().subtract(netPos);    //默认endShortPosition < 0
            side = Side.BUY;
        }
        gap = gap.min(config.getMaxOrderQty());          //下单量不能超过配置中单笔最大下单量
        BigDecimal unit = symbolSlice.getUnit();
        // 2. 校验：敞口是否满足最小下单单位 (1手)
        if (gap.compareTo(unit) < 0) {
            log.info("[{}] 敞口不足1手，忽略下单. Gap: {}, Unit: {}, symbol: {}",
                    instanceId, gap, unit, symbolSlice.getSymbol());
            return;
        }

        //计算下单量
        BigDecimal orderQty = unitConvert(gap,unit);

        // 2. 判断是否走 VWAP (境外)
        boolean isAbroad = "1".equalsIgnoreCase(symbolSlice.getDomesticType());

        if (isAbroad) {
            // VWAP 逻辑
            List<NewOrder> newOrderList= algoService.makeVwapOrder(side,orderQty, config, symbolSlice);
        } else {
            // 2. 单笔报价下单逻辑
            executeSingleOrder(orderQty, side, symbolSlice);
        }
    }

    /**
     * [细化] 简单报价下单逻辑
     * 流程: 获取行情 -> 计算价格(加点) -> 计算数量 -> 组装对象 -> 发单
     */
    private void executeSingleOrder(BigDecimal orderQty,Side side, SymbolTimeSlice symbolSlice) {
        // A. 获取行情快照
        PloyPrices depth = getPloyDepth(symbolSlice.getSymbol(),symbolSlice.getSources());   //todo
        if (depth == null) {
            log.error("[{}] 行情缺失，无法下单: {}", instanceId, symbolSlice.getSymbol());         //todo 告警处理？
            return;
        }

        // B. 计算价格
        BigDecimal price = calculateQuotePrice(depth, side, config.getTradeMode(), config.getPriceBaseType());
        if (price == null) {
            log.warn("[{}] Cal price failed! Depth is null", instanceId);
            return;
        }

        // C. 组装订单对象
        NewOrder req = NewOrder.builder()            //todo
                .symbol(symbolSlice.getSymbol())
                .side(side)
                .type(OrderType.LIMIT) // 限价单
                .price(price)
                .orderQty(orderQty)
                .timeInForce(TimeInForce.GTC) // 挂单直到取消
                .orderId(OrderUtil.generateOrderId(config.getTradeTag(),config.getInstanceId())) // 生成唯一ID
                .build();

        log.info("[{}] send newOrder.side: {},orderQty: {}.price: {}, mode:{}",
                instanceId, side, orderQty, price, config.getTradeMode());

        // F. 调用SDK发单
        newOrderSingle(req);
    }

    /**
     * [细化] 价格计算引擎
     * 基于配置的交易模式 从行情中提取基准价并加点
     */
    private BigDecimal calculateQuotePrice(PloyPrices ployPrices, Side side, String tradeMode, String priceBaseType) {

        BigDecimal basePrice = null;

        // 1. 获取基准价 (Base Price)
        // A (Aggressive/激进): 吃对手价 (买入看卖一，卖出看买一)
        if ("A".equalsIgnoreCase(tradeMode)) {
            basePrice = (side == Side.BUY) ? ployPrices.getBestAskPx() : ployPrices.getBestBidPx();  //todo  直接取对手最优档位，还是根据量取对应档位
        }
        // D (Defensive/防守): 挂本方价 (买入看买一，卖出看卖一)
        else if ("D".equalsIgnoreCase(tradeMode)) {
            if("1".equals(priceBaseType)){
                basePrice = (side == Side.BUY) ? ployPrices.getBestBidPx() : ployPrices.getBestAskPx();
            }else if("2".equals(priceBaseType)){
                basePrice = (side == Side.BUY) ? ployPrices.getSecondBestBidPx() : ployPrices.getSecondBestAskPx();
            }
        }
        // N (Neutral/中性): 中间价
        else {
                basePrice = ployPrices.getMidPx();
        }

        if (basePrice == null) return null;

        // 2. 叠加点差 (Spread)
        // 买入价 = 基准 + 买入点差
        // 卖出价 = 基准 - 卖出点差
        BigDecimal finalPrice;
        if (side == Side.BUY) {
            BigDecimal spread = config.getBidSpread() != null ? config.getBidSpread() : BigDecimal.ZERO;
            finalPrice = basePrice.add(spread);
        } else {
            BigDecimal spread = config.getOfrSpread() != null ? config.getOfrSpread() : BigDecimal.ZERO;
            finalPrice = basePrice.subtract(spread);
        }
        return finalPrice;
    }


    private SymbolTimeSlice getOrRefreshActiveSlice() {
        LocalTime now = LocalTime.now();

        // Step 1: 快速检查 (Fast Path)
        // 如果缓存存在，且当前时间还在缓存的结束时间之前 -> 直接返回
        if (activeTimeSlice != null && now.isBefore(activeTimeSlice.getEndTime()) && now.isAfter(activeTimeSlice.getStartTime())) {
            // 还需要检查是否在开始时间之后 ，防止跨天等极端情况
            return activeTimeSlice;
        }

        // Step 2: 缓存失效，重新查找 (Slow Path)
        // 只有在跨越时间片边界的那一毫秒才会执行一次 List 遍历
        return refreshactiveTimeSlice(now);
    }


    /**
     * 遍历 Config List 查找当前时间片并更新缓存
     */
    private synchronized SymbolTimeSlice refreshactiveTimeSlice(LocalTime now) {
        // 从静态配置中查找
        SymbolTimeSlice newSlice = config.findSlice(now);

        if (newSlice != null) {
            // 只有当时间片发生实质变化时才打印日志
            if (this.activeTimeSlice == null || !newSlice.getId().equals(this.activeTimeSlice.getId())) {
                log.info("[{}] change symbolTimeSlice ,symbol: {} -> {}, 阈值: {})",
                        instanceId,
                        activeTimeSlice.getSymbol(),
                        newSlice.getSymbol());

                // [关键] 如果合约发生了变化，需要执行相关业务操作，如撤单
                handleSymbolChange(activeTimeSlice,newSlice);
            }

            // 更新缓存
            this.activeTimeSlice = newSlice;
        } else {
            // 当前不在任何交易时间段内
            this.activeTimeSlice = null;
        }

        return this.activeTimeSlice;
    }

    public void handleSymbolChange(SymbolTimeSlice oldSlice, SymbolTimeSlice newSlice) {
        // 1. 撤单
        cancelAllOrders();
        //todo 是否需要等待，撤单处理完成后再进行后续操作
    }
    /**
     * 尝试状态流转 (原子性操作)
     * @param expectedStatus 期望的当前状态 (前置条件)
     * @param targetState    目标状态
     * @return 是否流转成功
     */
    private boolean attemptTransition(StrategyStatus expectedStatus, StrategyStatus targetState) {
        StrategyStatus current = status.get();

        //  CAS 原子更新 (防止并发修改)
        if (status.compareAndSet(expectedStatus, targetState)) {
            log.info("[{}] 状态流转成功: {} -> {}", instanceId, expectedStatus, targetState);
            return true;
        } else {
            // CAS 失败意味着在校验和更新之间状态被其他线程修改了
            log.warn("[{}] 状态流转失败: CAS并发冲突。原状态可能已变更。", instanceId);
            return false;
        }
    }

    private BigDecimal unitConvert(BigDecimal gap,BigDecimal unit) {
        return gap.divide(unit, BigDecimal.ROUND_UP);                 //向上取整，最多平超一手
    }
    // ================== 辅助方法 ==================


    private void stopTask(ScheduledFuture<?> task) {
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
    }


    public boolean isRunning() {
        return this.status.get() != StrategyStatus.STOPPED || this.status.get() != StrategyStatus.MELTDOWN;
    }

    /**
     * [调整点1] 敞口安全判定逻辑
     * 不再使用固定比例，而是直接读取 SymbolTimeSlice 中的终止线
     */
    private boolean isGapSafe(BigDecimal gap, SymbolTimeSlice rule) {
        // Gap > 0 (缺货/多头敞口): 需买入平盘，直到 Gap <= 多头终止线
        if (gap.compareTo(BigDecimal.ZERO) > 0) {
            return gap.compareTo(rule.getEndLongPosition()) <= 0;
        }
        // Gap < 0 (多货/空头敞口): 需卖出平盘，直到 Abs(Gap) <= 空头终止线
        else {
            return gap.abs().compareTo(rule.getEndShortPosition()) <= 0;
        }
    }


    // SDK 回调实现 (如果需要处理特定 Depth 逻辑)
    @Override
    public void onDepth(Depth depth) {

    }


    @Override
    public void onOrderReport(OrderReport report) {
        // BaseStrategy 可能已经处理了通用的订单状态更新
        // 这里处理积存金特有的逻辑，例如更新 hedgedPos 缓存
    }

    @Override
    public void onPositionUpdate(String accountId) {
        // 可选：如果是事件驱动模式，可以在这里立即触发一次 runMonitoringLogic
    }


}