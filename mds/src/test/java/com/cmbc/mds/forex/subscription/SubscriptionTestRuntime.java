package com.cmbc.mds.forex.subscription;

import com.cmbc.mds.forex.distribution.service.QuoteDistributionService;
import com.cmbc.mds.forex.engine.MarketDataChannelRegistry;
import com.cmbc.mds.forex.engine.MarketDataEngine;
import com.cmbc.mds.forex.engine.MarketDataWorkerService;
import com.cmbc.mds.forex.provider.service.ForeignBankConnectionService;
import com.cmbc.mds.forex.provider.service.SourceConfigService;
import com.cmbc.mds.forex.quotes.cacheservice.CleanQuotesCacheService;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesCacheService;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesLatchedCacheService;
import com.cmbc.mds.forex.quotes.service.CleanService;
import com.cmbc.mds.forex.quotes.service.MergeService;
import com.cmbc.mds.forex.subscription.config.InitSubscriptionProperties;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.cmbc.mds.forex.subscription.core.event.TopicActiveEvent;
import com.cmbc.mds.forex.subscription.core.event.TopicInactiveEvent;
import com.cmbc.mds.forex.subscription.listener.MarketDataResourceListener;
import com.cmbc.mds.forex.subscription.service.InitSubscriptionService;
import com.cmbc.mds.forex.subscription.service.StrategySubscriptionService;
import com.cmbc.mds.forex.subscription.service.TraderSubscriptionService;
import com.cmbc.mds.forex.subscription.validator.MarketDataValidator;
import com.cmbc.mds.forex.subscription.validator.SubscriptionValidator;
import com.cmbc.mds.monitor.QuotePerformanceService;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JUnit-only test harness for subscription integration tests.
 *
 * <p>It recreates the small part of the Spring container that these tests need:
 * field injection, application events, test properties, and fake external services.
 */
class SubscriptionTestRuntime implements AutoCloseable {

    final StrategySubscriptionService strategyService;
    final TraderSubscriptionService traderService;
    final SubscriptionCoreService coreService;
    final TestSubscriptionInspector inspector;
    final InitSubscriptionService initSubscriptionService;
    final FakeForeignBankConnectionService connectionService;
    final FakeSourceConfigService configService;

    private final MarketDataEngine marketDataEngine;
    private final MarketDataWorkerService workerService;
    private final MarketDataChannelRegistry channelRegistry;
    private final QuoteDistributionService distributionService;
    private final QuotePerformanceService quotePerformanceService;

    private SubscriptionTestRuntime() {
        this.coreService = new SubscriptionCoreService();
        this.distributionService = new QuoteDistributionService();
        this.quotePerformanceService = new QuotePerformanceService();
        this.channelRegistry = new MarketDataChannelRegistry();

        CleanQuotesCacheService cleanQuotesCacheService = new CleanQuotesCacheService();
        MergeQuotesCacheService mergeQuotesCacheService = new MergeQuotesCacheService();
        MergeQuotesLatchedCacheService mergeQuotesLatchedCacheService = new MergeQuotesLatchedCacheService();
        MergeService mergeService = new MergeService();
        CleanService cleanService = new CleanService();
        this.workerService = new MarketDataWorkerService(
                channelRegistry, cleanService, mergeService, quotePerformanceService);
        this.marketDataEngine = new MarketDataEngine(channelRegistry, workerService);

        this.connectionService = new FakeForeignBankConnectionService();
        this.configService = new FakeSourceConfigService();

        MarketDataValidator validator = new MarketDataValidator();
        inject(validator, "connectionService", connectionService);
        inject(validator, "configService", configService);

        MarketDataResourceListener resourceListener = new MarketDataResourceListener();
        inject(resourceListener, "marketDataEngine", marketDataEngine);

        inject(distributionService, "subscriptionCoreService", coreService);
        inject(distributionService, "allChannels", Collections.emptyList());
        inject(distributionService, "shutdownTimeoutSeconds", 1L);
        distributionService.init();

        TestEventPublisher eventPublisher = new TestEventPublisher(resourceListener, distributionService);
        inject(coreService, "validators", List.<SubscriptionValidator>of(validator));
        inject(coreService, "eventPublisher", eventPublisher);

        inject(mergeService, "mergeQuotesCacheService", mergeQuotesCacheService);
        inject(mergeService, "mergeQuotesLatchedCacheService", mergeQuotesLatchedCacheService);
        inject(mergeService, "distributionService", distributionService);
        inject(mergeService, "queueGateway", channelRegistry);

        inject(cleanService, "subscriptionCoreService", coreService);
        inject(cleanService, "queueGateway", channelRegistry);
        inject(cleanService, "distributionService", distributionService);
        inject(cleanService, "cleanQuotesCacheService", cleanQuotesCacheService);
        inject(cleanService, "foreignBankConnectionService", connectionService);
        inject(cleanService, "sourceConfigService", configService);
        inject(cleanService, "providerExporedTime", 10000L);

        this.strategyService = new StrategySubscriptionService();
        inject(strategyService, "subscriptionCoreService", coreService);
        inject(strategyService, "queueGateway", channelRegistry);
        inject(strategyService, "cleanQuotesCacheService", cleanQuotesCacheService);

        this.traderService = new TraderSubscriptionService();
        inject(traderService, "subscriptionCoreService", coreService);

        this.initSubscriptionService = new InitSubscriptionService();
        inject(initSubscriptionService, "subscriptionCoreService", coreService);
        inject(initSubscriptionService, "subscriptionProperties", initProperties());
        inject(initSubscriptionService, "mergeQuotesCacheService", mergeQuotesCacheService);
        inject(initSubscriptionService, "mergeQuotesLatchedCacheService", mergeQuotesLatchedCacheService);

        this.inspector = new TestSubscriptionInspector();
        inject(inspector, "coreService", coreService);
        inject(inspector, "channelRegistry", channelRegistry);
        inject(inspector, "distributionService", distributionService);
    }

    static SubscriptionTestRuntime create() {
        return new SubscriptionTestRuntime();
    }

    void resetExternalServices() {
        connectionService.reset();
        configService.reset();
    }

    @Override
    public void close() {
        try {
            inspector.clearAllState();
        } finally {
            workerService.shutdown();
            distributionService.shutdown();
            quotePerformanceService.destroy();
        }
    }

    private static InitSubscriptionProperties initProperties() {
        InitSubscriptionProperties properties = new InitSubscriptionProperties();

        InitSubscriptionProperties.SubscriptionConfig foreign =
                new InitSubscriptionProperties.SubscriptionConfig();
        foreign.setSources(List.of("GS", "HSBC", "UBS", "FXALL", "FXALL", "FXALL", "FXALL", "FXALL", "FXALL"));
        foreign.setProviders(List.of("GS", "HSBC", "UBS", "BNPP", "BOA.", "GS.", "JPMC", "SG", "UBS."));
        foreign.setSymbols(List.of("XAU/USD", "USD/CNH"));
        properties.setForeign(foreign);

        InitSubscriptionProperties.SubscriptionConfig dimple =
                new InitSubscriptionProperties.SubscriptionConfig();
        dimple.setSources(List.of("DIMPLE"));
        dimple.setProviders(List.of("DIMPLE"));
        dimple.setSymbols(List.of("Au(T+D)", "Au(T+N1)", "Au(T+N2)", "Au99.95", "Au99.99", "au2606"));
        properties.setDimple(dimple);

        InitSubscriptionProperties.SubscriptionConfig cmds =
                new InitSubscriptionProperties.SubscriptionConfig();
        cmds.setSources(List.of("CMDS"));
        cmds.setProviders(List.of("CMDS"));
        cmds.setSymbols(List.of("USD/CNY"));
        properties.setCmds(cmds);

        return properties;
    }

    private static void inject(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to inject field " + fieldName + " into " + target.getClass(), e);
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

    static class FakeForeignBankConnectionService extends ForeignBankConnectionService {
        private final Map<String, Boolean> states = new ConcurrentHashMap<>();
        private boolean defaultConnected = true;

        @Override
        public boolean isBankConnected(String source) {
            return states.getOrDefault(source, defaultConnected);
        }

        void setConnected(String source, boolean connected) {
            states.put(source, connected);
        }

        void reset() {
            states.clear();
            defaultConnected = true;
        }
    }

    static class FakeSourceConfigService extends SourceConfigService {
        private final Map<String, Boolean> states = new ConcurrentHashMap<>();
        private boolean defaultValid = true;

        @Override
        public boolean isValidSource(String source) {
            return states.getOrDefault(source, defaultValid);
        }

        void reset() {
            states.clear();
            defaultValid = true;
        }
    }

    private static class TestEventPublisher implements ApplicationEventPublisher {
        private final MarketDataResourceListener resourceListener;
        private final QuoteDistributionService distributionService;

        private TestEventPublisher(MarketDataResourceListener resourceListener,
                                   QuoteDistributionService distributionService) {
            this.resourceListener = resourceListener;
            this.distributionService = distributionService;
        }

        @Override
        public void publishEvent(Object event) {
            if (event instanceof TopicActiveEvent activeEvent) {
                resourceListener.onTopicActive(activeEvent);
                distributionService.onTopicActive(activeEvent);
            } else if (event instanceof TopicInactiveEvent inactiveEvent) {
                resourceListener.onTopicInactive(inactiveEvent);
                distributionService.onTopicInactive(inactiveEvent);
            }
        }
    }
}
