package com.cmbc.mds.forex.quotes;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.constants.InterConstants;
import com.cmbc.mds.forex.distribution.service.QuoteDistributionService;
import com.cmbc.mds.forex.engine.port.MarketDataQueueGateway;
import com.cmbc.mds.forex.provider.service.ForeignBankConnectionService;
import com.cmbc.mds.forex.provider.service.SourceConfigService;
import com.cmbc.mds.forex.quotes.adapter.QuoteAdapterRouter;
import com.cmbc.mds.forex.quotes.adapter.impl.BaseMQTranserAdapter;
import com.cmbc.mds.forex.quotes.cacheservice.CleanQuotesCacheService;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesCacheService;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesLatchedCacheService;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.MQTranserBean;
import com.cmbc.mds.forex.quotes.dto.MergeMessage;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.quotes.dto.PriceVolumeInfo;
import com.cmbc.mds.forex.quotes.receiver.IbmMqQuoteMessageProcessor;
import com.cmbc.mds.forex.quotes.receiver.QuoteReceiver;
import com.cmbc.mds.forex.quotes.service.CleanService;
import com.cmbc.mds.forex.quotes.service.MergeService;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext.SubscriberType;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.MergeDataTopic;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataCorrectnessBaselineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 测试语义：验证外资行 MQTranserBean SPOT 报文从接收器进入路由，再由通用 MQ Adapter 转成 Depth 并写入 clean 队列。
     * 关注重点：订阅前置校验使用规范化后的 clean topic key；路由 payload 保持 MQTranserBean；Adapter 输出的价格、数量、quoteId 和 cleanId 不被性能优化改变。
     */
    @Test
    @DisplayName("MQ-01 外资行 SPOT 报文应路由到对应 clean 队列")
    void ibmSpotQuoteRoutesToCleanQueue() throws Exception {
        TestQuoteReceiver receiver = new TestQuoteReceiver();
        CapturingRouter router = new CapturingRouter();
        CapturingSubscriptionService subscriptionService = new CapturingSubscriptionService(true);
        CapturingConnectionService connectionService = new CapturingConnectionService(true);

        inject(receiver, "objectMapper", OBJECT_MAPPER);
        inject(receiver, "quoteAdapterRouter", router);
        inject(receiver, "subscriptionCoreService", subscriptionService);
        inject(receiver, "foreignBankConnectionService", connectionService);

        receiver.dispatch(OBJECT_MAPPER.readValue("""
                {
                  "MESSAGE_TYPE": "MQTranserBean",
                  "tpfg": "SPOT",
                  "serviceId": "UBS-FIX",
                  "exnm": "EURUSD",
                  "nameid": "UBS-SPOT-001",
                  "gradsPriceList": [
                    {
                      "bid": "1.1000",
                      "bidSize": "1000000",
                      "ask": "1.1002",
                      "askSize": "2000000",
                      "bidSq": "BID-001",
                      "askSq": "ASK-001"
                    }
                  ]
                }
                """, MQTranserBean.class), "UBS", "UBS", "EUR/USD");

        assertThat(subscriptionService.topicKey)
                .isEqualTo(MarketDataTopic.buildTopicKey("UBS", "UBS", "EUR/USD"));
        assertThat(connectionService.refreshedSource).isEqualTo("UBS");
        assertThat(connectionService.connectedFlag).isTrue();
        assertThat(router.payload).isInstanceOf(MQTranserBean.class);
        assertThat(router.source).isEqualTo("UBS");
        assertThat(router.provider).isEqualTo("UBS");

        CapturingQueueGateway queueGateway = new CapturingQueueGateway();
        TestableMQAdapter adapter = new TestableMQAdapter();
        inject(adapter, "queueGateway", queueGateway);

        adapter.adaptAndHandle((MQTranserBean) router.payload, router.source, router.provider);

        assertThat(queueGateway.cleanId)
                .isEqualTo(MarketDataTopic.buildTopicKey("UBS", "UBS", "EUR/USD"));
        assertThat(queueGateway.cleanDepth.getSource()).isEqualTo("UBS");
        assertThat(queueGateway.cleanDepth.getProvider()).isEqualTo("UBS");
        assertThat(queueGateway.cleanDepth.getSymbol()).isEqualTo("EUR/USD");
        assertThat(queueGateway.cleanDepth.getQuoteId()).isEqualTo("BID-001");
        assertThat(queueGateway.cleanDepth.getBidPrices()).containsExactly(new BigDecimal("1.1000"));
        assertThat(queueGateway.cleanDepth.getBidQuantities()).containsExactly(new BigDecimal("1000000"));
        assertThat(queueGateway.cleanDepth.getAskPrices()).containsExactly(new BigDecimal("1.1002"));
        assertThat(queueGateway.cleanDepth.getAskQuantities()).containsExactly(new BigDecimal("2000000"));
    }

    /**
     * 测试语义：验证接收端在没有任何 clean/merge 下游订阅时会在 Adapter 之前丢弃行情。
     * 关注重点：仍刷新来源活跃状态，但不调用 QuoteAdapterRouter，避免无订阅行情继续反序列化、转换和入队造成 CPU/GC 浪费。
     */
    @Test
    @DisplayName("MQ-02 无订阅行情应在 Adapter 前置过滤阶段丢弃")
    void unsubscribedQuoteIsDroppedBeforeAdapterRoute() throws Exception {
        TestQuoteReceiver receiver = new TestQuoteReceiver();
        CapturingRouter router = new CapturingRouter();
        CapturingSubscriptionService subscriptionService = new CapturingSubscriptionService(false);
        CapturingConnectionService connectionService = new CapturingConnectionService(true);

        inject(receiver, "objectMapper", OBJECT_MAPPER);
        inject(receiver, "quoteAdapterRouter", router);
        inject(receiver, "subscriptionCoreService", subscriptionService);
        inject(receiver, "foreignBankConnectionService", connectionService);

        receiver.dispatch(OBJECT_MAPPER.readValue("""
                {
                  "MESSAGE_TYPE": "MQTranserBean",
                  "tpfg": "SPOT",
                  "serviceId": "UBS-FIX",
                  "exnm": "EURUSD",
                  "gradsPriceList": [
                    {
                      "bid": "1.1000",
                      "bidSize": "1000000",
                      "ask": "1.1002",
                      "askSize": "2000000"
                    }
                  ]
                }
                """, MQTranserBean.class), "UBS", "UBS", "EUR/USD");

        assertThat(subscriptionService.topicKey)
                .isEqualTo(MarketDataTopic.buildTopicKey("UBS", "UBS", "EUR/USD"));
        assertThat(connectionService.refreshedSource).isEqualTo("UBS");
        assertThat(connectionService.connectedFlag).isTrue();
        assertThat(router.payload).isNull();
        assertThat(router.source).isNull();
        assertThat(router.provider).isNull();
    }

    /**
     * 测试语义：验证 HTTP 手工推送入口复用 IBM MQ 文本处理器，而不是维护一条独立行情解析链路。
     * 关注重点：测试环境手工传入的行情应尽可能模拟 IBM MQ 通路，最终仍以 MQTranserBean 进入 Adapter 路由。
     */
    @Test
    @DisplayName("MQ-03 HTTP 手工推送应复用 IBM MQ 文本处理通路")
    void httpReplayUsesIbmMqTextProcessor() {
        TestQuoteReceiver receiver = new TestQuoteReceiver();
        CapturingRouter router = new CapturingRouter();
        CapturingSubscriptionService subscriptionService = new CapturingSubscriptionService(true);
        CapturingConnectionService connectionService = new CapturingConnectionService(true);

        inject(receiver, "quoteAdapterRouter", router);
        inject(receiver, "subscriptionCoreService", subscriptionService);
        inject(receiver, "foreignBankConnectionService", connectionService);

        IbmMqQuoteMessageProcessor processor = new IbmMqQuoteMessageProcessor(
                OBJECT_MAPPER, new com.cmbc.mds.monitor.QuotePerformanceService());
        processor.process("HTTP_IBMMQ_REPLAY", """
                {
                  "MESSAGE_TYPE": "MQTranserBean",
                  "tpfg": "SPOT",
                  "serviceId": "UBS-FIX",
                  "exnm": "EURUSD",
                  "gradsPriceList": [
                    {
                      "bid": "1.1000",
                      "bidSize": "1000000",
                      "ask": "1.1002",
                      "askSize": "2000000"
                    }
                  ]
                }
                """, receiver, null);

        assertThat(subscriptionService.topicKey)
                .isEqualTo(MarketDataTopic.buildTopicKey("UBS", "UBS", "EUR/USD"));
        assertThat(router.payload).isInstanceOf(MQTranserBean.class);
        assertThat(router.source).isEqualTo("UBS");
        assertThat(router.provider).isEqualTo("UBS");
    }

    /**
     * 测试语义：验证 CleanService 对合法 Depth 完成清洗、缓存、策略 merge 投递和 clean 侧分发。
     * 关注重点：clean cache key 与清洗后的 source/provider/symbol 一致；策略订阅通过 distributeId 投递 merge；分发侧拿到同一份清洗结果。
     */
    @Test
    @DisplayName("CLEAN-01 Clean 应缓存清洗结果、投递 merge 并分发 clean")
    void cleanServiceCachesRoutesAndDistributesCleanDepth() {
        CleanService cleanService = new CleanService();
        CleanQuotesCacheService cleanCache = new CleanQuotesCacheService();
        CapturingQueueGateway queueGateway = new CapturingQueueGateway();
        CapturingDistributionService distributionService = new CapturingDistributionService();

        String cleanKey = MarketDataTopic.buildTopicKey("DIMPLE", "DIMPLE", "Au(T+D)");
        String mergeKey = MergeDataTopic.buildKey(List.of("DIMPLE"), List.of("DIMPLE"), "Au(T+D)");
        CapturingSubscriptionService subscriptionService = new CapturingSubscriptionService(true);
        subscriptionService.strategySubscribers = List.of(
                new SubscriberContext("STG-CLEAN-01", SubscriberType.MD_CLEAN_STRATEGY, mergeKey));

        inject(cleanService, "subscriptionCoreService", subscriptionService);
        inject(cleanService, "queueGateway", queueGateway);
        inject(cleanService, "distributionService", distributionService);
        inject(cleanService, "cleanQuotesCacheService", cleanCache);
        inject(cleanService, "foreignBankConnectionService", new CapturingConnectionService(true));
        inject(cleanService, "sourceConfigService", new EmptySourceConfigService());

        cleanService.doCleanandRoute(cleanKey, depth(
                "DIMPLE-001",
                "DIMPLE",
                "DIMPLE",
                "Au(T+D)",
                List.of(new BigDecimal("480.10")),
                List.of(new BigDecimal("10")),
                List.of(new BigDecimal("480.20")),
                List.of(new BigDecimal("12"))));

        Depth cachedDepth = cleanCache.getDepth(cleanKey);
        assertThat(cachedDepth).isNotNull();
        assertThat(cachedDepth.getSymbol()).isEqualTo("Au(T+D)");
        assertThat(cachedDepth.getExtraParams())
                .containsEntry(InterConstants.EXTRA_KEY_VALUE_PROVIDER, "DIMPLE")
                .containsEntry(InterConstants.EXTRA_KEY_VALUE_TRADE_MODE, BaseConstants.TRADE_MODE_ODM);

        assertThat(queueGateway.mergeId).isEqualTo(mergeKey);
        assertThat(queueGateway.mergeMessage.getProvider()).isEqualTo("DIMPLE");
        assertThat(queueGateway.mergeMessage.getSymbol()).isEqualTo("Au(T+D)");
        assertThat(queueGateway.mergeMessage.getData()).isSameAs(cachedDepth);
        assertThat(distributionService.sourceKeys).containsExactly(cleanKey);
        assertThat(distributionService.payloads).containsExactly(cachedDepth);
    }

    /**
     * 测试语义：验证 CleanService 在存在 clean 订阅但没有策略订阅时，只缓存并分发 clean 行情，不投递 merge 队列。
     * 关注重点：交易员/监控订阅不应触发无意义的 merge 事件，避免后续队列、聚合状态和对象分配开销。
     */
    @Test
    @DisplayName("CLEAN-02 无策略订阅时 Clean 不应投递 merge 队列")
    void cleanServiceDoesNotPushMergeWithoutStrategySubscribers() {
        CleanService cleanService = new CleanService();
        CleanQuotesCacheService cleanCache = new CleanQuotesCacheService();
        CapturingQueueGateway queueGateway = new CapturingQueueGateway();
        CapturingDistributionService distributionService = new CapturingDistributionService();

        String cleanKey = MarketDataTopic.buildTopicKey("DIMPLE", "DIMPLE", "Au(T+D)");
        CapturingSubscriptionService subscriptionService = new CapturingSubscriptionService(true);

        inject(cleanService, "subscriptionCoreService", subscriptionService);
        inject(cleanService, "queueGateway", queueGateway);
        inject(cleanService, "distributionService", distributionService);
        inject(cleanService, "cleanQuotesCacheService", cleanCache);
        inject(cleanService, "foreignBankConnectionService", new CapturingConnectionService(true));
        inject(cleanService, "sourceConfigService", new EmptySourceConfigService());

        cleanService.doCleanandRoute(cleanKey, depth(
                "DIMPLE-002",
                "DIMPLE",
                "DIMPLE",
                "Au(T+D)",
                List.of(new BigDecimal("480.10")),
                List.of(new BigDecimal("10")),
                List.of(new BigDecimal("480.20")),
                List.of(new BigDecimal("12"))));

        assertThat(cleanCache.getDepth(cleanKey)).isNotNull();
        assertThat(queueGateway.mergeId).isNull();
        assertThat(queueGateway.mergeMessage).isNull();
        assertThat(distributionService.sourceKeys).containsExactly(cleanKey);
        assertThat(distributionService.payloads).containsExactly(cleanCache.getDepth(cleanKey));
    }

    /**
     * 测试语义：验证单一 source 的清洗行情进入 MergeService 后生成聚合快照，并同步写入普通缓存、锁存缓存和分发通道。
     * 关注重点：最优/次优买卖价、中间价和 provider key 计算稳定；对外发布的是深拷贝快照而不是内部可变聚合状态。
     */
    @Test
    @DisplayName("MERGE-01 单一 source 聚合后应生成缓存、锁存缓存和分发快照")
    void mergeServiceBuildsSingleSourceSnapshot() {
        MergeFixture fixture = createMergeFixture();
        String mergeKey = MergeDataTopic.buildKey(List.of("UBS"), List.of("UBS"), "EUR/USD");

        fixture.mergeService.handleMergeEvent(mergeKey, new MergeMessage(
                "UBS",
                "EUR/USD",
                mergeDepth("UBS-001", "UBS", "UBS", "EUR/USD",
                        List.of(new BigDecimal("1.1000"), new BigDecimal("1.0990")),
                        List.of(new BigDecimal("1000000"), new BigDecimal("2000000")),
                        List.of(new BigDecimal("1.1003"), new BigDecimal("1.1005")),
                        List.of(new BigDecimal("1000000"), new BigDecimal("2000000")))));

        PloyPrices snapshot = fixture.mergeCache.getPolyPrices(mergeKey);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getBestBidPx()).isEqualByComparingTo("1.1000");
        assertThat(snapshot.getSecondBestBidPx()).isEqualByComparingTo("1.0990");
        assertThat(snapshot.getBestAskPx()).isEqualByComparingTo("1.1003");
        assertThat(snapshot.getSecondBestAskPx()).isEqualByComparingTo("1.1005");
        assertThat(snapshot.getMidPx()).isEqualByComparingTo("1.10015");
        PriceVolumeInfo bestBidVolume = snapshot.getBestBidVolumeInfo();
        PriceVolumeInfo secondBestBidVolume = snapshot.getSecondBestBidVolumeInfo();
        PriceVolumeInfo bestAskVolume = snapshot.getBestAskVolumeInfo();
        PriceVolumeInfo secondBestAskVolume = snapshot.getSecondBestAskVolumeInfo();
        assertThat(bestBidVolume.getPrice()).isEqualByComparingTo("1.1000");
        assertThat(bestBidVolume.getTotalVolume()).isEqualByComparingTo("1000000");
        assertThat(secondBestBidVolume.getPrice()).isEqualByComparingTo("1.0990");
        assertThat(secondBestBidVolume.getTotalVolume()).isEqualByComparingTo("2000000");
        assertThat(bestAskVolume.getPrice()).isEqualByComparingTo("1.1003");
        assertThat(bestAskVolume.getTotalVolume()).isEqualByComparingTo("1000000");
        assertThat(secondBestAskVolume.getPrice()).isEqualByComparingTo("1.1005");
        assertThat(secondBestAskVolume.getTotalVolume()).isEqualByComparingTo("2000000");
        assertThat(snapshot.getFdBid().get(new BigDecimal("1.1000"))).containsKey("UBS");
        assertThat(snapshot.getFdAsk().get(new BigDecimal("1.1003"))).containsKey("UBS");
        assertThat(fixture.latchedCache.getLatchedPrices(mergeKey, null)).isNotNull();
        assertThat(fixture.distributionService.sourceKeys).containsExactly(mergeKey);
        assertThat(fixture.distributionService.payloads).containsExactly(snapshot);
    }

    /**
     * 测试语义：验证 source 断线事件进入 merge 队列后，只清理断线 source 的 bid/ask 报价。
     * 关注重点：断线清理必须串行作用于对应 mergeId 的状态，保留其他 source 的报价，并重新发布清理后的快照。
     */
    @Test
    @DisplayName("MERGE-02 断线事件应只清理指定 source 报价")
    void mergeServiceDisconnectEventRemovesOnlyDisconnectedSource() {
        MergeFixture fixture = createMergeFixture();
        String mergeKey = MergeDataTopic.buildKey(List.of("UBS", "GS"), List.of("UBS", "GS"), "EUR/USD");

        fixture.mergeService.handleMergeEvent(mergeKey, new MergeMessage(
                "UBS",
                "EUR/USD",
                mergeDepth("UBS-001", "UBS", "UBS", "EUR/USD",
                        List.of(new BigDecimal("1.1000")),
                        List.of(new BigDecimal("1000000")),
                        List.of(new BigDecimal("1.1003")),
                        List.of(new BigDecimal("1000000")))));
        fixture.mergeService.handleMergeEvent(mergeKey, new MergeMessage(
                "GS",
                "EUR/USD",
                mergeDepth("GS-001", "GS", "GS", "EUR/USD",
                        List.of(new BigDecimal("1.0995")),
                        List.of(new BigDecimal("1500000")),
                        List.of(new BigDecimal("1.1004")),
                        List.of(new BigDecimal("1500000")))));

        MergeMessage disconnect = new MergeMessage();
        disconnect.setDisconnectEvent(true);
        disconnect.setDisconnectedSource("UBS");
        fixture.mergeService.handleMergeEvent(mergeKey, disconnect);

        PloyPrices snapshot = fixture.mergeCache.getPolyPrices(mergeKey);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getBestBidPx()).isEqualByComparingTo("1.0995");
        assertThat(snapshot.getBestAskPx()).isEqualByComparingTo("1.1004");
        assertThat(snapshot.getFdBid()).doesNotContainKey(new BigDecimal("1.1000"));
        assertThat(snapshot.getFdAsk()).doesNotContainKey(new BigDecimal("1.1003"));
        assertThat(snapshot.getFdBid().get(new BigDecimal("1.0995"))).containsKey("GS");
        assertThat(snapshot.getFdAsk().get(new BigDecimal("1.1004"))).containsKey("GS");
        assertThat(fixture.distributionService.sourceKeys).last().isEqualTo(mergeKey);
    }

    /**
     * 测试语义：验证缓存中已经发布的 merge 快照不会被后续同一 source 的新报价污染。
     * 关注重点：后续行情会替换 provider 在内部状态中的报价，但历史快照的 fdBid/fdAsk 引用和最优价应保持独立。
     */
    @Test
    @DisplayName("MERGE-03 缓存快照应与后续聚合状态保持隔离")
    void mergeSnapshotIsDetachedFromLaterUpdates() {
        MergeFixture fixture = createMergeFixture();
        String mergeKey = MergeDataTopic.buildKey(List.of("UBS"), List.of("UBS"), "EUR/USD");

        fixture.mergeService.handleMergeEvent(mergeKey, new MergeMessage(
                "UBS",
                "EUR/USD",
                mergeDepth("UBS-001", "UBS", "UBS", "EUR/USD",
                        List.of(new BigDecimal("1.1000")),
                        List.of(new BigDecimal("1000000")),
                        List.of(new BigDecimal("1.1003")),
                        List.of(new BigDecimal("1000000")))));

        PloyPrices firstSnapshot = fixture.mergeCache.getPolyPrices(mergeKey);

        fixture.mergeService.handleMergeEvent(mergeKey, new MergeMessage(
                "UBS",
                "EUR/USD",
                mergeDepth("UBS-002", "UBS", "UBS", "EUR/USD",
                        List.of(new BigDecimal("1.1010")),
                        List.of(new BigDecimal("2000000")),
                        List.of(new BigDecimal("1.1013")),
                        List.of(new BigDecimal("2000000")))));

        PloyPrices secondSnapshot = fixture.mergeCache.getPolyPrices(mergeKey);
        assertThat(firstSnapshot).isNotNull();
        assertThat(secondSnapshot).isNotNull();
        assertThat(secondSnapshot).isNotSameAs(firstSnapshot);
        assertThat(firstSnapshot.getBestBidPx()).isEqualByComparingTo("1.1000");
        assertThat(firstSnapshot.getBestAskPx()).isEqualByComparingTo("1.1003");
        assertThat(secondSnapshot.getBestBidPx()).isEqualByComparingTo("1.1010");
        assertThat(secondSnapshot.getBestAskPx()).isEqualByComparingTo("1.1013");
        assertThat(firstSnapshot.getFdBid()).isNotSameAs(secondSnapshot.getFdBid());
        assertThat(firstSnapshot.getFdAsk()).isNotSameAs(secondSnapshot.getFdAsk());
    }

    /**
     * 测试语义：验证聚合异常不会污染已发布状态，也不会把旧快照伪装成新行情再次分发。
     * 关注重点：异常发生在工作副本上，真实状态、普通缓存和分发通道都保持上一笔成功结果。
     */
    @Test
    @DisplayName("MERGE-04 聚合异常时应保留旧状态且不缓存不分发")
    void mergeFailureDoesNotPublishOrMutateCurrentState() {
        MergeFixture fixture = createMergeFixture();
        String mergeKey = MergeDataTopic.buildKey(List.of("UBS"), List.of("UBS"), "EUR/USD");

        fixture.mergeService.handleMergeEvent(mergeKey, new MergeMessage(
                "UBS",
                "EUR/USD",
                mergeDepth("UBS-001", "UBS", "UBS", "EUR/USD",
                        List.of(new BigDecimal("1.1000")),
                        List.of(new BigDecimal("1000000")),
                        List.of(new BigDecimal("1.1003")),
                        List.of(new BigDecimal("1000000")))));

        PloyPrices firstSnapshot = fixture.mergeCache.getPolyPrices(mergeKey);
        assertThat(firstSnapshot).isNotNull();
        assertThat(fixture.distributionService.sourceKeys).hasSize(1);

        Depth brokenDepth = mergeDepth("UBS-BAD", "UBS", "UBS", "EUR/USD",
                List.of(new BigDecimal("1.1010")),
                List.of(new BigDecimal("2000000")),
                List.of(new BigDecimal("1.1013")),
                List.of(new BigDecimal("2000000")));
        brokenDepth.setBidPrices(null);

        fixture.mergeService.handleMergeEvent(mergeKey, new MergeMessage(
                "UBS",
                "EUR/USD",
                brokenDepth));

        assertThat(fixture.mergeCache.getPolyPrices(mergeKey)).isSameAs(firstSnapshot);
        assertThat(firstSnapshot.getBestBidPx()).isEqualByComparingTo("1.1000");
        assertThat(firstSnapshot.getBestAskPx()).isEqualByComparingTo("1.1003");
        assertThat(fixture.distributionService.sourceKeys).hasSize(1);
    }

    private static MergeFixture createMergeFixture() {
        MergeService mergeService = new MergeService();
        MergeQuotesCacheService mergeCache = new MergeQuotesCacheService();
        MergeQuotesLatchedCacheService latchedCache = new MergeQuotesLatchedCacheService();
        CapturingDistributionService distributionService = new CapturingDistributionService();

        inject(mergeService, "mergeQuotesCacheService", mergeCache);
        inject(mergeService, "mergeQuotesLatchedCacheService", latchedCache);
        inject(mergeService, "distributionService", distributionService);
        inject(mergeService, "queueGateway", new CapturingQueueGateway());

        return new MergeFixture(mergeService, mergeCache, latchedCache, distributionService);
    }

    private static Depth depth(String quoteId,
            String source,
            String provider,
            String symbol,
            List<BigDecimal> bidPrices,
            List<BigDecimal> bidQuantities,
            List<BigDecimal> askPrices,
            List<BigDecimal> askQuantities) {
        Depth depth = new Depth();
        depth.setQuoteId(quoteId);
        depth.setSource(source);
        depth.setProvider(provider);
        depth.setSymbol(symbol);
        depth.setCreateTime(System.currentTimeMillis());
        depth.setBidPrices(bidPrices);
        depth.setBidQuantities(bidQuantities);
        depth.setAskPrices(askPrices);
        depth.setAskQuantities(askQuantities);
        depth.setExtraParams(new HashMap<>());
        return depth;
    }

    private static Depth mergeDepth(String quoteId,
            String source,
            String provider,
            String symbol,
            List<BigDecimal> bidPrices,
            List<BigDecimal> bidQuantities,
            List<BigDecimal> askPrices,
            List<BigDecimal> askQuantities) {
        Depth depth = depth(quoteId, source, provider, symbol, bidPrices, bidQuantities, askPrices, askQuantities);
        depth.getExtraParams().put(InterConstants.EXTRA_KEY_VALUE_TRADE_MODE, BaseConstants.TRADE_MODE_ODM);
        depth.getExtraParams().put(InterConstants.EXTRA_KEY_VALUE_TIMESTAMP, "20260604 09:30:00.000");
        return depth;
    }

    private static void inject(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to inject field " + fieldName, e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static class TestQuoteReceiver extends QuoteReceiver {
        private void dispatch(MQTranserBean quote, String source, String provider, String defaultSymbol) {
            receiveAndDispatch(quote, source, provider, defaultSymbol);
        }
    }

    private static class TestableMQAdapter extends BaseMQTranserAdapter {
    }

    private static class CapturingRouter extends QuoteAdapterRouter {
        private Object payload;
        private String source;
        private String provider;

        @Override
        public void route(Object payload, String source, String provider) {
            this.payload = payload;
            this.source = source;
            this.provider = provider;
        }

        @Override
        public void route(Object payload, QuoteRoutingContext context) {
            this.payload = payload;
            this.source = context.source();
            this.provider = context.provider();
        }
    }

    private static class CapturingSubscriptionService extends SubscriptionCoreService {
        private final boolean hasSubscribers;
        private String topicKey;
        private List<SubscriberContext> strategySubscribers = List.of();

        private CapturingSubscriptionService(boolean hasSubscribers) {
            this.hasSubscribers = hasSubscribers;
        }

        @Override
        public boolean hasSubscribers(String topicKey) {
            this.topicKey = topicKey;
            return hasSubscribers;
        }

        @Override
        public List<SubscriberContext> getSubscribersByType(String topicKey, SubscriberType type) {
            this.topicKey = topicKey;
            if (type == SubscriberType.MD_CLEAN_STRATEGY) {
                return strategySubscribers;
            }
            return List.of();
        }

        @Override
        public Set<String> getDistributeIdsByType(String topicKey, SubscriberType type) {
            this.topicKey = topicKey;
            if (type != SubscriberType.MD_CLEAN_STRATEGY) {
                return Set.of();
            }
            return strategySubscribers.stream()
                    .map(SubscriberContext::getDistributeId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static class CapturingConnectionService extends ForeignBankConnectionService {
        private final boolean connected;
        private String refreshedSource;
        private boolean connectedFlag;

        private CapturingConnectionService(boolean connected) {
            this.connected = connected;
        }

        @Override
        public void refreshLastActive(String source, boolean connectedFlag) {
            this.refreshedSource = source;
            this.connectedFlag = connectedFlag;
        }

        @Override
        public boolean isBankConnected(String source) {
            return connected;
        }
    }

    private static class EmptySourceConfigService extends SourceConfigService {
        @Override
        public boolean isValidSource(String source) {
            return false;
        }

        @Override
        public boolean isSourceRejected(String source) {
            return false;
        }

        @Override
        public List<String> getAllSourceKeys() {
            return List.of();
        }
    }

    private static class CapturingQueueGateway implements MarketDataQueueGateway {
        private String cleanId;
        private Depth cleanDepth;
        private String mergeId;
        private MergeMessage mergeMessage;

        @Override
        public void pushToClean(String cleanId, Depth depth) {
            this.cleanId = cleanId;
            this.cleanDepth = depth;
        }

        @Override
        public void pushToMerge(String mergeId, MergeMessage message) {
            this.mergeId = mergeId;
            this.mergeMessage = message;
        }
    }

    private static class CapturingDistributionService extends QuoteDistributionService {
        private final List<String> sourceKeys = new ArrayList<>();
        private final List<Object> payloads = new ArrayList<>();

        @Override
        public void distribute(String sourceKey, Object data) {
            sourceKeys.add(sourceKey);
            payloads.add(data);
        }
    }

    private record MergeFixture(
            MergeService mergeService,
            MergeQuotesCacheService mergeCache,
            MergeQuotesLatchedCacheService latchedCache,
            CapturingDistributionService distributionService) {
    }
}
