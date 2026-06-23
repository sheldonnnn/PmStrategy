package com.cmbc.mds.forex.subscription;

import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext.SubscriberType;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic;
import com.cmbc.mds.forex.subscription.service.InitSubscriptionService;
import com.cmbc.mds.forex.subscription.service.StrategySubscriptionService;
import com.cmbc.mds.forex.subscription.service.TraderSubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不启动 Spring Test 容器的订阅集成测试。
 *
 * <p>计划测试点：
 * 1. 验证策略、交易员、系统初始化三类订阅入口的编排逻辑。
 * 2. 通过 SubscriptionCoreService 内部索引、行情通道队列、
 *    QuoteDistributionService 路由表验证资源复用与清理。
 * 3. 通过修改 fake 报价源连接状态，验证校验器的阻断行为。
 *
 * <p>Mock/fake 对象设置：
 * SubscriptionTestRuntime 手工装配真实业务服务，只替换不稳定的外部协作者。
 * FakeForeignBankConnectionService 默认返回已连接，
 * FakeFxProviderConfigService 默认返回有效，
 * TestEventPublisher 同步触发生产环境中由 Spring 事件触发的同一批监听器。
 */
public class SubscriptionIntegrationTest {

    private SubscriptionTestRuntime runtime;
    private StrategySubscriptionService strategyService;
    private TraderSubscriptionService traderService;
    private InitSubscriptionService initSubscriptionService;
    private TestSubscriptionInspector inspector;

    @BeforeEach
    void setUp() {
        // 每个用例都创建新的手工运行时，避免依赖 SpringTest 的上下文缓存。
        // fake 报价源服务默认是“已连接/有效”，等价于旧容器化测试里的 mock 默认设置。
        runtime = SubscriptionTestRuntime.create();
        strategyService = runtime.strategyService;
        traderService = runtime.traderService;
        initSubscriptionService = runtime.initSubscriptionService;
        inspector = runtime.inspector;
        runtime.resetExternalServices();
    }

    @AfterEach
    void tearDown() {
        // 关闭运行时会清理反射读取到的内部状态，并停止 worker 与
        // QuoteDistributionService 启动的虚拟线程执行器。
        if (runtime != null) {
            runtime.close();
        }
    }

    @Test
    @DisplayName("TC-01 system init default subscriptions")
    void testSystemInitDefaultSubscription() throws Exception {
        /*
         * 测试点：系统初始化应按配置订阅外资行、Dimple 与 CMDS 行情主题。
         * Mock 设置：runtime 注入的 InitSubscriptionProperties 与旧测试属性配置
         * 中的配置一致；SYSTEM_INIT 按业务设计会跳过报价源连接校验。
         */
        initSubscriptionService.run();

        Map<String, Set<SubscriberContext>> topicSubs = inspector.getTopicSubscribers();
        Set<String> sysInitTopics = inspector.getSubscriberTopics().get("SYSTEM_INIT");
        // SYSTEM_INIT 共持有 43 个 topic key：外资行配置生成 2 * (9 个 clean + 1 个 merge + 1 个 dist)，
        // Dimple 配置生成 6 * (1 个 clean + 1 个 merge + 1 个 dist)，CMDS 配置生成 1 * 3。
        assertThat(sysInitTopics).hasSize(43);

        String foreignMergeKey = "MD:MERGE:[FXALL.BNPP,FXALL.BOA.,FXALL.GS.,FXALL.JPMC,FXALL.SG,FXALL.UBS.,GS.GS,HSBC.HSBC,UBS.UBS]:XAU/USD";
        // 抽查外资行代表性 topic，证明全市场订阅链路中的 clean、merge、internal dist 都已注册。
        assertThat(topicSubs).containsKeys(
                "MD:CLEAN:FXALL.JPMC:XAU/USD",
                "MD:CLEAN:UBS.UBS:XAU/USD",
                foreignMergeKey,
                "DIST:INTERNAL:SYSTEM_INIT:" + foreignMergeKey
        );

        String dimpleMergeKey = "MD:MERGE:[DIMPLE.DIMPLE]:Au(T+D)";
        // 抽查 Dimple 代表性 topic，证明第二段初始化配置分支也已生效。
        assertThat(topicSubs).containsKeys(
                "MD:CLEAN:DIMPLE.DIMPLE:Au(T+D)",
                dimpleMergeKey,
                "DIST:INTERNAL:SYSTEM_INIT:" + dimpleMergeKey
        );

        String cmdsMergeKey = "MD:MERGE:[CMDS.CMDS]:USD/CNY";
        assertThat(topicSubs).containsKeys(
                "MD:CLEAN:CMDS.CMDS:USD/CNY",
                cmdsMergeKey,
                "DIST:INTERNAL:SYSTEM_INIT:" + cmdsMergeKey
        );

        // TopicActiveEvent 由手工 TestEventPublisher 发布；如果监听链路正确，
        // 每个激活的 clean/merge topic 都应有对应的引擎队列。
        assertThat(inspector.getCleanQueueKeys())
                .hasSize(25)
                .contains("MD:CLEAN:FXALL.JPMC:XAU/USD", "MD:CLEAN:DIMPLE.DIMPLE:Au(T+D)", "MD:CLEAN:CMDS.CMDS:USD/CNY");
        assertThat(inspector.getMergeQueueKeys()).hasSize(9).contains(foreignMergeKey, dimpleMergeKey, cmdsMergeKey);

        Map<String, Set<DistributionDataTopic>> distMap = inspector.getSourceToDistMap();
        // 路由表由 QuoteDistributionService 的事件监听器维护；每个 SYSTEM_INIT merge topic
        // 有 1 条路由，说明分发事件被正确触发。
        assertThat(distMap.get(foreignMergeKey)).hasSize(1);
        assertThat(distMap.get(dimpleMergeKey)).hasSize(1);
        assertThat(distMap.get(cmdsMergeKey)).hasSize(1);
    }

    @Test
    @DisplayName("TC-02 single strategy single source")
    void testSingleStrategySingleSource() {
        /*
         * 测试点：单个策略订阅单一行情源时，应创建 1 个 clean topic、1 个 merge topic、
         * 1 个 internal dist topic，以及对应的引擎队列。
         * Mock 设置：fake 报价源连接/配置服务默认返回成功。
         */
        String strategyId = "STG_TC02";
        strategyService.addStrategySubscription(List.of("UBS"), List.of("UBS"), "USD/JPY", "T001", strategyId);

        String cleanKey = "MD:CLEAN:UBS.UBS:USD/JPY";
        String mergeKey = "MD:MERGE:[UBS.UBS]:USD/JPY";
        String distKey = "DIST:INTERNAL:" + strategyId + ":" + mergeKey;

        // topicSubscribers 中存在三个 key，说明编排逻辑写入了三类逻辑订阅；
        // activeTopics 中存在 clean/merge，说明物理 topic 实例被保留。
        assertThat(inspector.getTopicSubscribers()).containsKeys(cleanKey, mergeKey, distKey);
        assertThat(inspector.getActiveTopics()).containsKeys(cleanKey, mergeKey);
        assertThat(inspector.getActiveTopics().get(cleanKey).getTopicKey()).isEqualTo(cleanKey);

        var byTypeMap = inspector.getTopicSubscribersByType().get(cleanKey);
        // CleanService 热路径依赖按类型索引，因此这里必须能且只能查到 1 个策略 clean 订阅者。
        assertThat(byTypeMap.get(SubscriberType.MD_CLEAN_STRATEGY)).hasSize(1);

        Set<SubscriberContext> cleanCtxs = inspector.getTopicSubscribers().get(cleanKey);
        assertThat(cleanCtxs).hasSize(1);
        SubscriberContext cleanCtx = cleanCtxs.iterator().next();
        // distributeId 指向预期 merge 队列，证明策略 clean 数据的内部流转目标构建正确。
        assertThat(cleanCtx.getSubscriberId()).isEqualTo(strategyId);
        assertThat(cleanCtx.getType()).isEqualTo(SubscriberType.MD_CLEAN_STRATEGY);
        assertThat(cleanCtx.getDistributeId()).isEqualTo(mergeKey);

        // 队列是 TopicActiveEvent 的副作用；队列存在说明事件监听器装配正确。
        assertThat(inspector.getCleanQueueKeys()).contains(cleanKey);
        assertThat(inspector.getMergeQueueKeys()).contains(mergeKey);
    }

    @Test
    @DisplayName("TC-03 multiple strategies same single source")
    void testMultipleStrategiesSameSingleSource() {
        /*
         * 测试点：两个策略使用同一行情源时，应复用 clean/merge 资源，
         * 同时保留两个订阅上下文与两个下游分发路由。
         */
        String strategyA = "STG_TC03_A";
        String strategyB = "STG_TC03_B";

        strategyService.addStrategySubscription(List.of("UBS"), List.of("UBS"), "GBP/USD", "T001", strategyA);
        strategyService.addStrategySubscription(List.of("UBS"), List.of("UBS"), "GBP/USD", "T002", strategyB);

        String cleanKey = "MD:CLEAN:UBS.UBS:GBP/USD";
        String mergeKey = "MD:MERGE:[UBS.UBS]:GBP/USD";

        // 同一个 clean topic 下出现两个 subscriberId，说明物理资源复用，但逻辑订阅彼此独立。
        assertThat(inspector.getTopicSubscribers().get(cleanKey))
                .hasSize(2)
                .extracting(SubscriberContext::getSubscriberId)
                .containsExactlyInAnyOrder(strategyA, strategyB);
        // 同一个 merge source 下有两个 dist topic，说明两个策略共享 merge 队列但都能收到聚合价格。
        assertThat(inspector.getSourceToDistMap().get(mergeKey)).hasSize(2);
    }

    @Test
    @DisplayName("TC-04 single strategy multiple sources")
    void testSingleStrategyMultipleSources() {
        /*
         * 测试点：单个策略订阅多个行情源时，应创建每个 clean 通道，
         * 并生成按 source/provider 排序后的确定性 merge key。
         */
        String strategyId = "STG_TC04";
        strategyService.addStrategySubscription(
                List.of("FXALL", "UBS", "GS"),
                List.of("JPMC", "UBS", "GS"),
                "EUR/GBP",
                "T001",
                strategyId);

        String expectedMergeKey = "MD:MERGE:[FXALL.JPMC,GS.GS,UBS.UBS]:EUR/GBP";
        // MergeDataTopic 会归一化顺序，因此无论调用方传入顺序如何，merge key 都应稳定一致。
        assertThat(inspector.getTopicSubscribers()).containsKey(expectedMergeKey);
        // 每个 source/provider 组合都必须有独立 clean 队列，后续聚合才有数据入口。
        assertThat(inspector.getCleanQueueKeys()).contains(
                "MD:CLEAN:FXALL.JPMC:EUR/GBP",
                "MD:CLEAN:UBS.UBS:EUR/GBP",
                "MD:CLEAN:GS.GS:EUR/GBP"
        );
    }

    @Test
    @DisplayName("TC-05 same multi-source subscription reuses resources")
    void testMultipleStrategiesSameMultipleSourcesOutOrder() {
        /*
         * 测试点：source/provider 集合相同但入参顺序不同时，应映射到同一个 merge key，
         * 并复用同一个 merge 订阅。
         */
        String strategyA = "STG_TC05_A";
        String strategyB = "STG_TC05_B";

        strategyService.addStrategySubscription(List.of("FXALL", "UBS"), List.of("JPMC", "UBS"), "AUD/JPY", "T001", strategyA);
        strategyService.addStrategySubscription(List.of("UBS", "FXALL"), List.of("UBS", "JPMC"), "AUD/JPY", "T002", strategyB);

        String expectedMergeKey = "MD:MERGE:[FXALL.JPMC,UBS.UBS]:AUD/JPY";
        // 两个策略挂在同一个归一化后的 merge key 下，证明资源复用不受入参顺序影响，
        // 也不会创建重复的物理 merge 队列。
        assertThat(inspector.getTopicSubscribers().get(expectedMergeKey))
                .hasSize(2)
                .extracting(SubscriberContext::getSubscriberId)
                .containsExactlyInAnyOrder(strategyA, strategyB);
    }

    @Test
    @DisplayName("TC-06 non-overlapping sources stay isolated")
    void testMultipleStrategiesNonOverlappingSources() {
        /*
         * 测试点：同一币种下使用完全不相交行情源的策略，不应共享 clean 订阅上下文。
         */
        String strategyA = "STG_TC06_A";
        String strategyB = "STG_TC06_B";

        strategyService.addStrategySubscription(List.of("FXALL"), List.of("JPMC"), "NZD/USD", "T001", strategyA);
        strategyService.addStrategySubscription(List.of("GS"), List.of("GS"), "NZD/USD", "T002", strategyB);

        // 每个 clean key 下只有对应策略上下文，证明不同行情源之间没有误串订阅关系。
        assertThat(inspector.getTopicSubscribers().get("MD:CLEAN:FXALL.JPMC:NZD/USD"))
                .hasSize(1)
                .first()
                .extracting(SubscriberContext::getSubscriberId)
                .isEqualTo(strategyA);
        assertThat(inspector.getTopicSubscribers().get("MD:CLEAN:GS.GS:NZD/USD"))
                .hasSize(1)
                .first()
                .extracting(SubscriberContext::getSubscriberId)
                .isEqualTo(strategyB);
    }

    @Test
    @DisplayName("TC-07 overlapping sources partially reuse clean topics")
    void testMultipleStrategiesOverlappingSources() {
        /*
         * 测试点：行情源集合部分重叠时，只应复用公共 clean 源；
         * 不同源组合仍应保留各自独立的 merge topic。
         */
        String strategyA = "STG_TC07_A";
        String strategyB = "STG_TC07_B";

        strategyService.addStrategySubscription(List.of("FXALL", "UBS"), List.of("JPMC", "UBS"), "USD/CHF", "T001", strategyA);
        strategyService.addStrategySubscription(List.of("UBS", "GS"), List.of("UBS", "GS"), "USD/CHF", "T002", strategyB);

        // UBS 为公共源，因此有两个上下文；两个独占 clean topic 各只有一个上下文，
        // 这正是“部分复用”的预期形态。
        assertThat(inspector.getTopicSubscribers().get("MD:CLEAN:UBS.UBS:USD/CHF")).hasSize(2);
        assertThat(inspector.getTopicSubscribers().get("MD:CLEAN:FXALL.JPMC:USD/CHF")).hasSize(1);
        assertThat(inspector.getTopicSubscribers().get("MD:CLEAN:GS.GS:USD/CHF")).hasSize(1);
        // 即使存在公共 clean 源，不同源组合也必须生成独立 merge topic，
        // 否则下游聚合状态会被混在一起。
        assertThat(inspector.getTopicSubscribers()).containsKeys(
                "MD:MERGE:[FXALL.JPMC,UBS.UBS]:USD/CHF",
                "MD:MERGE:[GS.GS,UBS.UBS]:USD/CHF"
        );
    }

    @Test
    @DisplayName("TC-08 trader subscription creates clean and websocket dist topics")
    void testTraderSubscription() {
        /*
         * 测试点：交易员订阅是 clean 到 WebSocket 的直连链路，不应创建 merge topic。
         */
        String traderId = "TRD_TC08";
        traderService.addTraderSubscription("UBS", "UBS", "AUD/USD", traderId);

        String cleanKey = "MD:CLEAN:UBS.UBS:AUD/USD";
        String distKey = "DIST:WEBSOCKET:" + traderId + ":" + cleanKey;
        String mergeKey = "MD:MERGE:[UBS.UBS]:AUD/USD";

        // clean topic 与 WebSocket dist topic 同时存在，说明交易员链路已注册。
        assertThat(inspector.getTopicSubscribers()).containsKeys(cleanKey, distKey);
        // 交易员直接消费 clean 数据，因此不存在 merge topic 才符合预期。
        assertThat(inspector.getTopicSubscribers()).doesNotContainKey(mergeKey);
        assertThat(inspector.getTopicSubscribers().get(cleanKey)).hasSize(1).first().satisfies(ctx -> {
            // 交易员 clean 数据通过 dist topic 分发，而不是进入策略 merge 队列，
            // 因此 distributeId 应为空。
            assertThat(ctx.getType()).isEqualTo(SubscriberType.MD_CLEAN_TREADER);
            assertThat(ctx.getDistributeId()).isNull();
        });
    }

    @Test
    @DisplayName("TC-09 strategy and trader share same clean source")
    void testStrategyAndTraderCrossSubscription() {
        /*
         * 测试点：交易员与策略可以订阅同一 clean 源，且不会互相覆盖上下文或分发路由。
         */
        String strategyId = "STG_TC09";
        String traderId = "TRD_TC09";
        String symbol = "EUR/CAD";

        traderService.addTraderSubscription("UBS", "UBS", symbol, traderId);
        strategyService.addStrategySubscription(List.of("UBS"), List.of("UBS"), symbol, "T001", strategyId);

        String cleanKey = "MD:CLEAN:UBS.UBS:EUR/CAD";
        // 同一 clean key 下存在两种 subscriber type，说明物理 clean 资源共享，
        // 但逻辑消费方仍然分离。
        assertThat(inspector.getTopicSubscribers().get(cleanKey)).hasSize(2);
        assertThat(inspector.getTopicSubscribers().get(cleanKey))
                .extracting(SubscriberContext::getType)
                .containsExactlyInAnyOrder(SubscriberType.MD_CLEAN_TREADER, SubscriberType.MD_CLEAN_STRATEGY);
        // clean 源有 1 条 WebSocket 路由，merge 源有 1 条策略 internal 路由；
        // 这个拆分符合交易员与策略的不同分发模型。
        assertThat(inspector.getSourceToDistMap().get(cleanKey)).hasSize(1);
        assertThat(inspector.getSourceToDistMap().get("MD:MERGE:[UBS.UBS]:EUR/CAD")).hasSize(1);
    }

    @Test
    @DisplayName("TC-10 unsubscribe cleans resources")
    void testUnsubscribeAndResourceCleanup() {
        /*
         * 测试点：唯一策略退订后，逻辑 topic 索引与引擎队列都应通过 TopicInactiveEvent 清理。
         */
        String strategyId = "STG_TC10";
        String symbol = "USD/HKD";

        strategyService.addStrategySubscription(List.of("UBS"), List.of("UBS"), symbol, "T001", strategyId);
        String cleanKey = "MD:CLEAN:UBS.UBS:USD/HKD";
        String mergeKey = "MD:MERGE:[UBS.UBS]:USD/HKD";

        assertThat(inspector.getCleanQueueKeys()).contains(cleanKey);
        strategyService.removeStrategySubscription(List.of("UBS"), List.of("UBS"), symbol, strategyId);

        // topicSubscribers 与反向 subscriber 索引中都不存在对应 key，
        // 说明核心引用计数归零并完成清理。
        assertThat(inspector.getTopicSubscribers()).doesNotContainKey(cleanKey);
        assertThat(inspector.getTopicSubscribers()).doesNotContainKey(mergeKey);
        assertThat(inspector.getSubscriberTopics()).doesNotContainKey(strategyId);
    }

    @Test
    @DisplayName("TC-11 validator currently does not reject disconnected provider")
    void testValidatorAllowsDisconnectedProviderWhenCheckDisabled() {
        /*
         * 测试点：当前 MarketDataValidator 中报价源配置/连接校验仍处于关闭状态。
         * 即使 fake 报价源标记为断开，订阅编排仍应按当前生产代码语义正常注册资源。
         */
        runtime.connectionService.setConnected("BAD_BANK", false);

        String strategyId = "STG_TC11";
        // 当前 validator 不阻断订阅，因此这里应正常完成资源注册。
        strategyService.addStrategySubscription(List.of("BAD_BANK"), List.of("BAD_BANK"), "USD/CNY", "T001", strategyId);

        // 校验关闭时，clean/merge/dist 三类资源都应完成注册。
        String cleanKey = "MD:CLEAN:BAD_BANK.BAD_BANK:USD/CNY";
        String mergeKey = "MD:MERGE:[BAD_BANK.BAD_BANK]:USD/CNY";
        String distKey = "DIST:INTERNAL:" + strategyId + ":" + mergeKey;
        assertThat(inspector.getTopicSubscribers()).containsKeys(cleanKey, mergeKey, distKey);
        assertThat(inspector.getSubscriberTopics()).containsKey(strategyId);
    }

    @Test
    @DisplayName("TC-12 duplicate subscription is idempotent")
    void testIdempotence() {
        /*
         * 测试点：重复提交完全相同的策略订阅，不应产生重复上下文；
         * SubscriberContext 的相等性与 Set.add 负责保证幂等。
         */
        String strategyId = "STG_TC12";
        String symbol = "GBP/JPY";
        String cleanKey = "MD:CLEAN:GS.GS:GBP/JPY";

        strategyService.addStrategySubscription(List.of("GS"), List.of("GS"), symbol, "T001", strategyId);
        int beforeSize = inspector.getTopicSubscribers().get(cleanKey).size();
        strategyService.addStrategySubscription(List.of("GS"), List.of("GS"), symbol, "T001", strategyId);
        int afterSize = inspector.getTopicSubscribers().get(cleanKey).size();

        // 数量保持为 1，说明重复订阅被忽略，没有增加引用计数或重复路由状态。
        assertThat(afterSize).isEqualTo(beforeSize).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-13 partial unsubscribe keeps shared resources")
    void testPartialUnsubscribeKeepsResources() {
        /*
         * 测试点：只要仍有其他订阅者引用共享资源，资源就必须存活；
         * 只有最后一个订阅者退订后才应释放。
         */
        String strategyA = "STG_TC13_A";
        String strategyB = "STG_TC13_B";
        String symbol = "EUR/JPY";
        String cleanKey = "MD:CLEAN:GS.GS:EUR/JPY";

        strategyService.addStrategySubscription(List.of("GS"), List.of("GS"), symbol, "T001", strategyA);
        strategyService.addStrategySubscription(List.of("GS"), List.of("GS"), symbol, "T002", strategyB);
        assertThat(inspector.getTopicSubscribers().get(cleanKey)).hasSize(2);

        strategyService.removeStrategySubscription(List.of("GS"), List.of("GS"), symbol, strategyA);
        // 剩余 1 个上下文说明共享 topic 仍被使用，因此队列与 active topic 缓存都必须保留 clean key。
        assertThat(inspector.getTopicSubscribers().get(cleanKey)).hasSize(1);
        assertThat(inspector.getCleanQueueKeys()).contains(cleanKey);
        assertThat(inspector.getActiveTopics()).containsKey(cleanKey);

        strategyService.removeStrategySubscription(List.of("GS"), List.of("GS"), symbol, strategyB);
        // 最后一个订阅者离开后，clean key 应同时从逻辑索引和物理资源视图中消失。
        assertThat(inspector.getTopicSubscribers()).doesNotContainKey(cleanKey);
        assertThat(inspector.getCleanQueueKeys()).doesNotContain(cleanKey);
    }

    @Test
    @DisplayName("TC-14 batch trader subscription")
    void testBatchTraderSubscription() {
        /*
         * 测试点：交易员批量订阅接口应逐条委托到普通交易员订阅流程，
         * 并为不同 source/symbol 组合创建独立 clean topic。
         */
        var req1 = new com.cmbc.mds.forex.subscription.dto.TraderSubReq("UBS", "UBS", "AUD/USD", "TRD_BATCH_1");
        var req2 = new com.cmbc.mds.forex.subscription.dto.TraderSubReq("GS", "GS", "NZD/USD", "TRD_BATCH_1");

        traderService.addBatchTraderSubscription(List.of(req1, req2));

        // 两个 clean topic 与两个队列同时存在，证明两个批量项都被执行，而不是只处理第一条。
        assertThat(inspector.getTopicSubscribers()).containsKeys(
                "MD:CLEAN:UBS.UBS:AUD/USD",
                "MD:CLEAN:GS.GS:NZD/USD"
        );
        assertThat(inspector.getCleanQueueKeys()).hasSize(2);
    }

    @Test
    @DisplayName("TC-15 strategy fully overlaps init subscriptions")
    void testStrategyAndInitFullOverlap() throws Exception {
        /*
         * 测试点：策略订阅的源集合与 SYSTEM_INIT 完全一致时，应复用全部 clean/merge 队列，
         * 同时增加策略自己的上下文与分发路由。
         */
        initSubscriptionService.run();

        int initialCleanQueues = inspector.getCleanQueueKeys().size();
        int initialMergeQueues = inspector.getMergeQueueKeys().size();

        String strategyId = "STG_TC15";
        String symbol = "XAU/USD";
        strategyService.addStrategySubscription(
                List.of("GS", "HSBC", "UBS", "FXALL", "FXALL", "FXALL", "FXALL", "FXALL", "FXALL"),
                List.of("GS", "HSBC", "UBS", "BNPP", "BOA.", "GS.", "JPMC", "SG", "UBS."),
                symbol,
                "T001",
                strategyId);

        String cleanKeyFxall = "MD:CLEAN:FXALL.JPMC:XAU/USD";
        String mergeKey = "MD:MERGE:[FXALL.BNPP,FXALL.BOA.,FXALL.GS.,FXALL.JPMC,FXALL.SG,FXALL.UBS.,GS.GS,HSBC.HSBC,UBS.UBS]:XAU/USD";

        // 队列数量不变，说明发生的是完整物理复用，而不是重复创建 clean/merge 资源。
        assertThat(inspector.getCleanQueueKeys()).hasSize(initialCleanQueues);
        assertThat(inspector.getMergeQueueKeys()).hasSize(initialMergeQueues);
        // 上下文数量变为 2，是因为 SYSTEM_INIT 与策略都合法持有同一个 clean/merge topic。
        assertThat(inspector.getTopicSubscribers().get(cleanKeyFxall))
                .hasSize(2)
                .extracting(SubscriberContext::getSubscriberId)
                .containsExactlyInAnyOrder("SYSTEM_INIT", strategyId);
        assertThat(inspector.getTopicSubscribers().get(mergeKey))
                .hasSize(2)
                .extracting(SubscriberContext::getSubscriberId)
                .containsExactlyInAnyOrder("SYSTEM_INIT", strategyId);
        // 策略目标的分发路由不能复用 SYSTEM_INIT 的路由；策略需要在同一个 merge source 下挂自己的路由。
        assertThat(inspector.getTopicSubscribers()).containsKey("DIST:INTERNAL:" + strategyId + ":" + mergeKey);
        assertThat(inspector.getSourceToDistMap().get(mergeKey)).hasSize(2);
    }

    @Test
    @DisplayName("TC-16 strategy partially overlaps init subscriptions")
    void testStrategyAndInitPartialOverlap() throws Exception {
        /*
         * 测试点：策略与 SYSTEM_INIT 部分重叠时，只应复用公共 clean 源；
         * init 独占和策略独占的 clean topic 应保持分离，并为新的源组合创建新 merge 队列。
         */
        initSubscriptionService.run();

        String strategyId = "STG_TC16";
        String symbol = "XAU/USD";
        strategyService.addStrategySubscription(List.of("FXALL", "COBA"), List.of("JPMC", "COBA"), symbol, "T001", strategyId);

        String sharedCleanKey = "MD:CLEAN:FXALL.JPMC:XAU/USD";
        String initExclusiveCleanKey = "MD:CLEAN:UBS.UBS:XAU/USD";
        String stgExclusiveCleanKey = "MD:CLEAN:COBA.COBA:XAU/USD";
        String initMergeKey = "MD:MERGE:[FXALL.BNPP,FXALL.BOA.,FXALL.GS.,FXALL.JPMC,FXALL.SG,FXALL.UBS.,GS.GS,HSBC.HSBC,UBS.UBS]:XAU/USD";
        String stgMergeKey = "MD:MERGE:[COBA.COBA,FXALL.JPMC]:XAU/USD";

        // 公共的 FXALL.JPMC clean topic 应由 SYSTEM_INIT 与策略共同持有。
        assertThat(inspector.getTopicSubscribers().get(sharedCleanKey))
                .hasSize(2)
                .extracting(SubscriberContext::getSubscriberId)
                .contains("SYSTEM_INIT", strategyId);
        // init 独占和策略独占的 clean topic 必须彼此隔离，且各自只有 1 个持有者。
        assertThat(inspector.getTopicSubscribers().get(initExclusiveCleanKey))
                .hasSize(1)
                .first()
                .extracting(SubscriberContext::getSubscriberId)
                .isEqualTo("SYSTEM_INIT");
        assertThat(inspector.getTopicSubscribers().get(stgExclusiveCleanKey))
                .hasSize(1)
                .first()
                .extracting(SubscriberContext::getSubscriberId)
                .isEqualTo(strategyId);
        // 不同源组合必须生成不同的 merge topic 与队列。
        assertThat(inspector.getTopicSubscribers().get(initMergeKey)).hasSize(1);
        assertThat(inspector.getTopicSubscribers().get(stgMergeKey)).hasSize(1);
        assertThat(inspector.getMergeQueueKeys()).contains(initMergeKey, stgMergeKey);
    }

    @Test
    @DisplayName("TC-17 strategy unsubscribe does not remove init resources")
    void testStrategyUnsubscribeWithInitRunning() throws Exception {
        /*
         * 测试点：策略与 SYSTEM_INIT 共享资源时，退订策略只能删除策略自己的上下文/路由，
         * 不能删除 SYSTEM_INIT 仍持有的 clean/merge 队列。
         */
        initSubscriptionService.run();

        String strategyId = "STG_TC17";
        String symbol = "XAU/USD";
        List<String> sources = List.of("GS", "HSBC", "UBS", "FXALL", "FXALL", "FXALL", "FXALL", "FXALL", "FXALL");
        List<String> providers = List.of("GS", "HSBC", "UBS", "BNPP", "BOA.", "GS.", "JPMC", "SG", "UBS.");

        strategyService.addStrategySubscription(sources, providers, symbol, "T001", strategyId);

        String cleanKeyFxall = "MD:CLEAN:FXALL.JPMC:XAU/USD";
        String mergeKey = "MD:MERGE:[FXALL.BNPP,FXALL.BOA.,FXALL.GS.,FXALL.JPMC,FXALL.SG,FXALL.UBS.,GS.GS,HSBC.HSBC,UBS.UBS]:XAU/USD";

        strategyService.removeStrategySubscription(sources, providers, symbol, strategyId);

        // 策略反向索引和 dist topic 已消失，说明策略退订路径清理了策略专属状态。
        assertThat(inspector.getSubscriberTopics().get(strategyId)).isNullOrEmpty();
        assertThat(inspector.getTopicSubscribers().get("DIST:INTERNAL:" + strategyId + ":" + mergeKey)).isNull();
        // 共享 clean 与 merge topic 仍由 SYSTEM_INIT 持有，因此底层引擎队列必须继续存活。
        assertThat(inspector.getTopicSubscribers().get(cleanKeyFxall))
                .hasSize(1)
                .first()
                .extracting(SubscriberContext::getSubscriberId)
                .isEqualTo("SYSTEM_INIT");
        assertThat(inspector.getTopicSubscribers().get(mergeKey)).hasSize(1);
        assertThat(inspector.getCleanQueueKeys()).contains(cleanKeyFxall);
        assertThat(inspector.getMergeQueueKeys()).contains(mergeKey);
    }
}
