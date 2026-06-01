# HedgeStrategyInstance 核心类深度解析 (Code Review 讲稿)

在向团队评审 `HedgeStrategyInstance.java` 这个包含 900 多行核心业务逻辑的“大心脏”时，切忌按行号顺序顺延。建议按照**“生命周期管理 ➔ 核心状态机 ➔ 发单与风控 ➔ 异步回调”**这四大模块进行系统性展开。

以下是为您准备的每个方法的展开介绍思路及话术：

---

## 一、 生命周期与状态流转管控 (Lifecycle & State)

这是最外层，决定了策略如何启停和流转。

*   **`start()`**：
    *   **职责**：策略初始化的入口。
    *   **评审亮点**：强调我们在启动时做了前置风控（`getOrRefreshActiveSlice()`），如果当前时间段没有可用的平盘合约，会自动拦截并触发停机（并推送异常告警）。随后启动监控定时任务。
*   **`stop(String reason)`**：
    *   **职责**：安全停机。
    *   **评审亮点 (重点)**：不要一句话带过。要强调这里设计了**“优雅停机机制”**——首先切断所有定时任务（停止产生新订单），然后调用 `cancelAllOrders()`，接着开启了一个自旋线程等待最多 5 秒，确保底层未完成的撤单或撮合事件彻底消化完，最后才销毁线程池。防止内存泄漏或幽灵订单。
*   **`pause() / resume()`**：
    *   **职责**：响应前端交易员的手工暂停与恢复指令。简单启停相关的 Task 定时器。
*   **`attemptTransition(StrategyStatus expected, StrategyStatus target)`**：
    *   **职责**：状态流转的原子操作。
    *   **评审亮点**：基于底层 `AtomicReference<StrategyStatus>.compareAndSet` 实现的无锁状态机流转。保障了当定时器和外部干预（如手动停止）并发时，策略状态不会发生“脏写”。
*   **`switchToExecution() / switchToChase() / startMonitoringPhase()`**：
    *   **职责**：对状态流转行为的封装。在切换时自动加载对应的定时器频率。

---

## 二、 核心状态机调度 (The 3-Phase Engine)

这部分是整个类的“业务大脑”。

*   **`runMonitoringLogic()`**：
    *   **职责**：监控态 (`MONITOR`) 循环逻辑。
    *   **评审亮点**：展示我们如何获取积存金敞口 (`clientPos`) 与已平盘敞口 (`hedgedPos`)。如果发现异常（获取为空），会触发我们新加的 3 级预警推送到前端。如果 `triggerEvaluator.evaluate` (触发器) 达标，立即调用 `switchToExecution()`。
*   **`runExecutionLogic()`**：
    *   **职责**：发单态 (`HEDGE`) 首单逻辑。
    *   **评审亮点**：首先判断敞口是否已经安全 (`isGapSafe`)，如果行情波动导致敞口缩回安全线，则直接回退到 `MONITOR`（节省手续费）。否则，判断是否在规定时间内未成，若超时则切换为追单 `switchToChase()`；若正常，则进入实质性发单方法 `handleHedgingExecution`。
*   **`runChaseLogic()`**：
    *   **职责**：追单态 (`CHASE`) 逻辑。
    *   **评审亮点**：应对极度单边行情。展示如何通过 `chaseStartTime` 和 `chaseNumber` 判断是否超时或超出最大追单次数。如果耗尽追单次数依然未平盘，触发高级别告警并停机，转交人工干预。

---

## 三、 发单引擎与极度严苛的风控 (Execution & Risk Control)

策略到底报什么价格？发多少量？这是 Review 时的焦点。

*   **`handleHedgingExecution(...)`**：
    *   **职责**：计算平盘方向（买/卖）与下单手数。根据积存金多空净头寸计算需要补足的数量。
*   **`buildStrategyOrder(...)`**：
    *   **职责**：构建最终发送到 OMS 的订单实体。
*   **`calculateQuotePrice(...)` (超级重点)**：
    *   **职责**：计算发单申报价格。这里是我们风控的密集区，必须仔细讲！
    *   **评审亮点 1 (涨跌停保护)**：通过 `ksdStaticQuoteInfo` 获取静态涨跌停。我们并没有直接使用极限价格，而是引入了 `downLimitBuffer` 和 `upLimitBuffer` 系数，如果算出的报价处于涨跌停的边缘危险区，**系统会直接拒绝发单**，防范极端穿仓风险。
    *   **评审亮点 2 (追单熔断)**：展示 `isChase` 分支。如果是首次追单，记录基准价格 `firstChasingPrice`；后续如果行情剧烈波动，追单价格与首次价格的偏差率 `currentDeviation` 超过了设定阈值，**直接熔断（触发异常告警并停机）**，有效防止在极端杀跌行情中一路追空导致巨亏。

---

## 四、 基础服务与底层支持 (Auxiliary Methods)

*   **`getOrRefreshActiveSlice()`**：
    *   **职责**：时间片轮转核心。判断当前时间应该用哪个时段的合约配置。
*   **`handleSymbolChange(...)`**：
    *   **职责**：跨时间段时的换挡操作。比如白天平盘结束转入夜盘，旧合约需要停运，新合约需要接替，这里负责处理过渡期的撤单操作。
*   **`pushStrategyMonitorInfo()`**：
    *   **职责**：负责给大屏前端喂数据。以 1000ms 的频率抓取当前策略实例快照并通过 Websocket 广播出去。
*   **`isGapSafe(gap, rule)`**：
    *   **职责**：免平缓冲计算。判断敞口绝对值是否处于配置的短/多免平区间内。

---

## 五、 订单事件异步回调处理 (Event Driven Handlers)

系统发出订单后，如何处理 OMS 底层反馈的撮合结果？

*   **`onMatch(ExecutionReport)`**：
    *   **职责**：处理成交事件（撮合成功）。
    *   **评审亮点**：重点强调这部分逻辑全部被扔进了 `this.orderEventExecutor` 单线程池中执行。这保证了同一时刻只有一个成交事件在更新内部的 `StrategyStatSummary` 状态，彻底消灭了多线程竞态条件。
*   **`calMatchSummary(...)`**：
    *   **职责**：根据最新的成交通知，累加策略内部的已成交手数、成交总额，并计算最新的开仓均价（`AvgPx`）。
*   **`onRtnOrder / onOrderCancel / onOrderRejected / onOtherEvent`**：
    *   **职责**：处理挂单、撤单、废单。
    *   **评审亮点**：如收到拒单或系统废单事件，会立即触发底层告警并尝试状态修复。

---

**一句话总结使用建议：**
“大家看着这个类很大，其实逻辑非常清晰：顶层由 `start/stop` 管控生死，中层由 `MONITOR -> HEDGE -> CHASE` 状态机接力狂奔，底层靠 `calculateQuotePrice` 死守价格风控红线，最后由 `onMatch` 单线程队列完美收尾。”
