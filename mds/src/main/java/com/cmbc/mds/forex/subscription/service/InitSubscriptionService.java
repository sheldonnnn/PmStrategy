package com.cmbc.mds.forex.subscription.service;

import com.cmbc.mds.forex.subscription.config.InitSubscriptionProperties;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.MergeDataTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统初始化订阅服务
 * 职责：负责系统启动时默认指定币种的全市场行情（或特殊渠道）订阅，保证底层通道开启
 * 特点：使用系统专用身份，无外部推送分发
 */
@Service
public class InitSubscriptionService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitSubscriptionService.class);

    // 定义系统初始化的专用订阅者ID
    public static final String STRATEGY_ID_SYSTEM_INIT = "SYSTEM_INIT";

    @Autowired
    private SubscriptionCoreService subscriptionCoreService;

    @Autowired
    private InitSubscriptionProperties subscriptionProperties;

    // 引入缓存服务供注册使用
    @Autowired
    private com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesCacheService mergeQuotesCacheService;

    @Autowired
    private com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesLatchedCacheService mergeQuotesLatchedCacheService;

    @Override
    public void run(String... args) throws Exception {
        log.info("[SystemInit] ================= 开始执行系统初始化全市场行情订阅 =================");

        // 1. 初始化外资行订阅配置
        InitSubscriptionProperties.SubscriptionConfig foreignConfig = subscriptionProperties.getForeign();
        if (isValidConfig(foreignConfig)) {
            log.info("[SystemInit] 加载外资行配置，配置Symbols: {}", foreignConfig.getSymbols());
            for (String symbol : foreignConfig.getSymbols()) {
                doSubscription(symbol, foreignConfig.getSources(), foreignConfig.getProviders());
            }
        } else {
            log.info("[SystemInit] 外资行初始化订阅配置为空或无效，跳过执行。");
        }

        // 2. 初始化Dimple订阅配置
        InitSubscriptionProperties.SubscriptionConfig dimpleConfig = subscriptionProperties.getDimple();
        if (isValidConfig(dimpleConfig)) {
            log.info("[SystemInit] 加载Dimple配置，配置Symbols: {}", dimpleConfig.getSymbols());
            for (String symbol : dimpleConfig.getSymbols()) {
                doSubscription(symbol, dimpleConfig.getSources(), dimpleConfig.getProviders());
            }
        } else {
            log.info("[SystemInit] Dimple初始化订阅配置为空或无效，跳过执行。");
        }

        // 3. 初始化CMDS订阅配置
        InitSubscriptionProperties.SubscriptionConfig cmdsConfig = subscriptionProperties.getCmds();
        if (isValidConfig(cmdsConfig)) {
            log.info("[SystemInit] 加载CMDS配置，配置Symbols: {}", cmdsConfig.getSymbols());
            for (String symbol : cmdsConfig.getSymbols()) {
                doSubscription(symbol, cmdsConfig.getSources(), cmdsConfig.getProviders());
            }
        } else {
            log.info("[SystemInit] CMDS初始化订阅配置为空或无效，跳过执行。");
        }

        log.info("[SystemInit] ================= 系统初始化全市场行情订阅完成 =================");
    }

    /**
     * 校验配置合法性
     */
    private boolean isValidConfig(InitSubscriptionProperties.SubscriptionConfig config) {
        return config != null
                && config.getSymbols() != null && !config.getSymbols().isEmpty()
                && config.getSources() != null && !config.getSources().isEmpty()
                && config.getProviders() != null && !config.getProviders().isEmpty()
                && config.getSources().size() == config.getProviders().size();
    }

    /**
     * 核心订阅逻辑 (无对外分发推送)
     *
     * <p>symbol 以配置文件传入为准，不做任何转换。
     * 建议配置文件中外资行 symbol 使用斜杠格式 (例如: "USD/CNY")，
     * 与行情接收侧 BaseMQTranserAdapter 保持一致。
     */
    private void doSubscription(String symbol, List<String> sources, List<String> providers) {
        try {
            // 1. 构建聚合主题 (MERGE Data)
            MergeDataTopic mergeDataTopic = new MergeDataTopic(sources, providers, symbol);
            SubscriberContext mergeDataCtx = new SubscriberContext(STRATEGY_ID_SYSTEM_INIT,
                    SubscriberContext.SubscriberType.MD_MERGE, null);

            // 2. [编排] 确保MarketData订阅, 确保MergeData订阅
            for (int i = 0; i < sources.size(); i++) {
                String currentSource = sources.get(i);
                String currentProvider = providers.get(i);

                MarketDataTopic currentMarketDataTopic = new MarketDataTopic(currentSource, currentProvider, symbol);
                SubscriberContext currentMarketDataCtx = new SubscriberContext(STRATEGY_ID_SYSTEM_INIT,
                        SubscriberContext.SubscriberType.MD_CLEAN_STRATEGY, mergeDataTopic.getTopicKey());

                // 功能1：订阅触发 TopicActiveEvent(MarketDataTopic) -> MarketDataResourceListener 开启对应通道，初始化待清洗行情queue和thread
                // 功能2：维护MarketDataTopic 与 策略id 的订阅关系，用于清洗后行情提交到对应的待聚合行情队列
                subscriptionCoreService.subscribe(currentMarketDataTopic, currentMarketDataCtx);
            }

            // MergeData 订阅
            // 订阅触发 TopicActiveEvent(MergeDataTopic) -> MarketDataResourceListener 开启对应通道，初始化待聚合行情queue和thread
            subscriptionCoreService.subscribe(mergeDataTopic, mergeDataCtx);

            // 4. [编排] 建立 INTERNAL 分发通道
            // 目的：将 SYSTEM_INIT 的 mergeKey 写入 sourceToDistMap，
            //       使 MergeService 分发时能路由到 StrategyExecutionChannel，
            //       进而触发通过 registerBySymbol() 注册的回调函数。
            // targetId 固定为 STRATEGY_ID_SYSTEM_INIT，与 StrategyExecutionChannel.buildDistKey() 对齐
            DistributionDataTopic distTopic = new DistributionDataTopic(mergeDataTopic, DistributionDataTopic.Protocol.INTERNAL, STRATEGY_ID_SYSTEM_INIT);
            SubscriberContext distCtx = new SubscriberContext(STRATEGY_ID_SYSTEM_INIT, SubscriberContext.SubscriberType.DIST_STRATEGY, STRATEGY_ID_SYSTEM_INIT);
            subscriptionCoreService.subscribe(distTopic, distCtx);

            // 5. 注册 symbol -> mergeKey 快查索引（供 StrategyExecutionChannel.registerBySymbol() 使用）
            mergeQuotesCacheService.registerSystemInitKey(symbol, mergeDataTopic.getTopicKey());
            // 同步注册到锁存缓存
            mergeQuotesLatchedCacheService.registerSystemInitKey(symbol, mergeDataTopic.getTopicKey());

            log.info("[SystemInit] 成功订阅聚合行情: Symbol={}, Sources={}", symbol, sources);

            // 注意: 系统初始化订阅仅用于开启系统底层内部运算，不进行 DistExternal/DistInternal 等分发操作

        } catch (Exception e) {
            log.error("[SystemInit] 系统初始化订阅发生异常: Symbol={}, Sources={}", symbol, sources, e);
        }
    }
}
