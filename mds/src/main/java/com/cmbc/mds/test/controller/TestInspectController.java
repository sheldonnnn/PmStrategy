package com.cmbc.mds.test.controller;

import com.cmbc.mds.forex.provider.service.ForeignBankConnectionService;
import com.cmbc.mds.forex.provider.service.SourceConfigService;
import com.cmbc.mds.forex.quotes.cacheservice.CleanQuotesCacheService;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesCacheService;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesLatchedCacheService;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.LatchedQuoteWrapper;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import com.cmbc.mds.forex.subscription.core.model.topic.MergeDataTopic;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * [测试专用] 行情状态检查控制器
 * <p>
 * ⚠️ 仅用于开发/测试阶段的结果验证，生产部署时应移除或通过 profile 屏蔽。
 * <p>
 * 提供以下查询能力：
 * 1. 清洗行情缓存查询（Clean Cache）
 * 2. 聚合行情缓存查询（Merge Cache）
 * 3. 订阅 Topic 状态查询
 * 4. 价格源连接状态查询
 * 5. 全量缓存快照（用于 Flow 测试断言）
 */
@RestController
@RequestMapping("/api/test/inspect")
public class TestInspectController {

    @Autowired
    private CleanQuotesCacheService cleanQuotesCacheService;

    @Autowired
    private MergeQuotesCacheService mergeQuotesCacheService;

    @Autowired
    private MergeQuotesLatchedCacheService mergeQuotesLatchedCacheService;

    @Autowired
    private SubscriptionCoreService subscriptionCoreService;

    @Autowired
    private ForeignBankConnectionService foreignBankConnectionService;

    // =========================================================================
    // 1. 清洗行情查询（验证行情是否通过清洗通道）
    // =========================================================================

    /**
     * 按 source/provider/symbol 查询清洗后行情（精确查询）
     * <p>
     * 对应 topic key 格式：MD:CLEAN:{source}.{provider}:{symbol}
     * <p>
     * 例如查询 GS 的 USD/JPY：source=GS, provider=GS, symbol=USD/JPY
     */
    @GetMapping("/clean/depth")
    public Object getCleanDepth(
            @RequestParam String source,
            @RequestParam String provider,
            @RequestParam String symbol) {
        Depth depth = cleanQuotesCacheService.getDepth(source, provider, symbol);
        if (depth == null) {
            return notFound("CleanCache", MarketDataTopic.buildTopicKey(source, provider, symbol));
        }
        return depth;
    }

    /**
     * 查询所有清洗行情缓存快照
     * <p>
     * 用于验证行情报文推送后，哪些 CleanChannel 有数据流入
     */
    @GetMapping("/clean/all")
    public Map<String, Object> getAllCleanDepths() {
        List<Depth> all = cleanQuotesCacheService.getAllQuotes();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", all.size());
        result.put("data", all);
        return result;
    }

    // =========================================================================
    // 2. 聚合行情查询（验证行情是否进入聚合通道并计算完成）
    // =========================================================================

    /**
     * 按 sources/providers/symbol 查询聚合行情（精确查询）
     * <p>
     * 对应 topic key 格式：MD:MERGE:[{s1.p1},{s2.p2}]:{symbol}
     * <p>
     * 示例：POST body
     * <pre>
     * {
     *   "sources":   ["GS", "HSBC"],
     *   "providers": ["GS", "HSBC"],
     *   "symbol":    "EUR/USD"
     * }
     * </pre>
     */
    @PostMapping("/merge/price")
    public Object getMergePrice(@RequestBody MergePriceQueryReq req) {
        PloyPrices prices = mergeQuotesCacheService.getPolyPrices(
                req.getSources(), req.getProviders(), req.getSymbol());
        if (prices == null) {
            String key = MergeDataTopic.buildKey(req.getSources(), req.getProviders(), req.getSymbol());
            return notFound("MergeCache", key);
        }
        return buildMergePriceSummary(prices);
    }

    /**
     * 按 symbol 快速查询系统初始化订阅（SYSTEM_INIT）对应的聚合行情
     * <p>
     * 适用于: 行情推送后快速验证系统初始化链路是否完整
     */
    @GetMapping("/merge/by-symbol")
    public Object getMergeBySymbol(@RequestParam String symbol) {
        PloyPrices prices = mergeQuotesCacheService.getSystemInitPloyPriceBySymbol(symbol);
        if (prices == null) {
            return notFound("MergeCache(SystemInit)", symbol);
        }
        return buildMergePriceSummary(prices);
    }

    /**
     * 查询所有聚合行情缓存快照（含价格摘要）
     * <p>
     * 用于 Flow 流程测试，一次性查看所有聚合行情结果
     */
    @GetMapping("/merge/all")
    public Map<String, Object> getAllMergePrices() {
        Map<String, PloyPrices> snapshot = mergeQuotesCacheService.getAllPolyPricesSnapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", snapshot.size());

        List<Map<String, Object>> summaries = snapshot.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("topicKey", e.getKey());
                    item.put("summary", buildMergePriceSummary(e.getValue()));
                    return item;
                })
                .collect(Collectors.toList());
        result.put("data", summaries);
        return result;
    }

    // =========================================================================
    // 3. 订阅 Topic 状态查询（验证订阅是否生效）
    // =========================================================================

    /**
     * 检查指定 topic key 是否有活跃订阅者（Fail-Fast 前置过滤的依据）
     * <p>
     * 直接反应行情是否会被处理还是被过滤
     * <p>
     * 示例：GET /api/test/inspect/topic/has-subscriber?topicKey=MD:CLEAN:GS.GS:USD/JPY
     */
    @GetMapping("/topic/has-subscriber")
    public Map<String, Object> hasSubscriber(@RequestParam String topicKey) {
        boolean has = subscriptionCoreService.hasSubscribers(topicKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicKey", topicKey);
        result.put("hasSubscriber", has);
        result.put("message", has ? "✓ 已订阅，行情将被处理" : "✗ 未订阅，行情将被前置过滤丢弃");
        return result;
    }

    /**
     * 批量检查多个 Clean Topic 是否有订阅者
     * <p>
     * 用于 TC-09（无订阅过滤）的验证
     * <p>
     * 请求体：
     * <pre>
     * {
     *   "topicKeys": [
     *     "MD:CLEAN:GS.GS:USD/JPY",
     *     "MD:CLEAN:HSBC.HSBC:EUR/USD"
     *   ]
     * }
     * </pre>
     */
    @PostMapping("/topic/batch-check")
    public List<Map<String, Object>> batchCheckSubscriber(@RequestBody TopicBatchCheckReq req) {
        return req.getTopicKeys().stream().map(key -> {
            boolean has = subscriptionCoreService.hasSubscribers(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("topicKey", key);
            item.put("hasSubscriber", has);
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 快速构建并检查 CleanTopic 是否有订阅者（免手写 topicKey 格式）
     * <p>
     * 等同于行情推送前的 Fail-Fast 检查逻辑
     */
    @GetMapping("/topic/check-clean")
    public Map<String, Object> checkCleanTopicSubscriber(
            @RequestParam String source,
            @RequestParam String provider,
            @RequestParam String symbol) {
        String topicKey = MarketDataTopic.buildTopicKey(source, provider, symbol);
        boolean has = subscriptionCoreService.hasSubscribers(topicKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", source);
        result.put("provider", provider);
        result.put("symbol", symbol);
        result.put("topicKey", topicKey);
        result.put("hasSubscriber", has);
        result.put("message", has ? "✓ 已订阅，推送此报文后行情将进入清洗流程" : "✗ 未订阅，推送此报文将被丢弃");
        return result;
    }

    // =========================================================================
    // 4. 价格源连接状态查询（验证心跳处理结果）
    // =========================================================================

    /**
     * 查询指定价格源的连接状态
     * <p>
     * 用于 TC-01（心跳连接）、TC-02（心跳断开）的验证
     * <p>
     * 示例：GET /api/test/inspect/provider/state?provider=GS
     */
    @GetMapping("/provider/state")
    public Map<String, Object> getProviderState(@RequestParam String provider) {
        boolean connected = foreignBankConnectionService.isBankConnected(provider);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", provider);
        result.put("connected", connected);
        result.put("message", connected ? "✓ 价格源已连接" : "✗ 价格源未连接或Heart超时");
        return result;
    }

    /**
     * 批量查询多个价格源连接状态
     */
    @GetMapping("/provider/state/all")
    public List<Map<String, Object>> getAllProviderStates(
            @RequestParam(defaultValue = "GS,HSBC,UBS,FXALL,JPMC,COBA") String providers) {
        return Arrays.stream(providers.split(","))
                .map(p -> {
                    boolean connected = foreignBankConnectionService.isBankConnected(p.trim());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("provider", p.trim());
                    item.put("connected", connected);
                    return item;
                }).collect(Collectors.toList());
    }

    // =========================================================================
    // 5. SystemInit Key 映射查询（验证系统初始化订阅注册）
    // =========================================================================

    /**
     * 查询系统初始化阶段注册的 symbol -> mergeKey 映射
     * <p>
     * 用于验证 InitSubscriptionService 初始化是否正确
     */
    @GetMapping("/system-init/merge-key")
    public Map<String, Object> getSystemInitMergeKey(@RequestParam String symbol) {
        String mergeKey = mergeQuotesCacheService.getSystemInitSourceKey(symbol);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol);
        result.put("mergeKey", mergeKey);
        result.put("registered", mergeKey != null);
        return result;
    }

    // =========================================================================
    // 6. 锁存行情查询（Latched Cache Test）
    // =========================================================================

    /**
     * 按 symbol 快速查询系统初始化订阅对应的锁存行情，支持传入过期时间
     */
    @GetMapping("/latched/by-symbol")
    public Object getLatchedBySymbol(@RequestParam String symbol, @RequestParam(required = false) Long expireThresholdMillis) {
        LatchedQuoteWrapper wrapper = mergeQuotesLatchedCacheService.getSystemInitLatchedPriceBySymbol(symbol, expireThresholdMillis);
        if (wrapper == null) {
            return notFound("LatchedCache(SystemInit)", symbol);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expired", wrapper.isExpired());
        result.put("lastUpdateTime", wrapper.getLastUpdateTime());
        result.put("summary", buildMergePriceSummary(wrapper.getData()));
        return result;
    }

    /**
     * 查询所有锁存行情的全量快照
     */
    @GetMapping("/latched/all")
    public Map<String, Object> getAllLatchedPrices() {
        Map<String, LatchedQuoteWrapper> snapshot = mergeQuotesLatchedCacheService.getAllLatchedPricesSnapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", snapshot.size());

        List<Map<String, Object>> summaries = snapshot.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("topicKey", e.getKey());
                    item.put("expired", e.getValue().isExpired());
                    item.put("lastUpdateTime", e.getValue().getLastUpdateTime());
                    item.put("summary", buildMergePriceSummary(e.getValue().getData()));
                    return item;
                })
                .collect(Collectors.toList());
        result.put("data", summaries);
        return result;
    }

    /**
     * 清理单条锁存记录
     */
    @DeleteMapping("/latched/remove")
    public Map<String, Object> removeLatchedPrices(@RequestParam String key) {
        mergeQuotesLatchedCacheService.removeLatchedPrices(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("message", "已调用清理");
        return result;
    }

    /**
     * 清空全部锁存记录
     */
    @DeleteMapping("/latched/clear-all")
    public Map<String, Object> clearAllLatchedPrices() {
        mergeQuotesLatchedCacheService.clearAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已调用全量清理");
        return result;
    }

    // =========================================================================
    // 7. 全局订阅路由树转储 (Global Subscription Tree Dump)
    // =========================================================================

    /**
     * 转储全局订阅路由树 (反射读取，不侵入正式代码)
     * <p>
     * 返回结构：SubscriberId -> List<TopicKey>
     * <p>
     * 示例：GET /api/test/inspect/subscription/tree-dump
     */
    @GetMapping("/subscription/tree-dump")
    public Map<String, Object> dumpSubscriptionTree() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            java.lang.reflect.Field field = SubscriptionCoreService.class.getDeclaredField("subscriberTopics");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Set<String>> subscriberTopics = (Map<String, Set<String>>) field.get(subscriptionCoreService);

            result.put("totalSubscribers", subscriberTopics.size());
            result.put("tree", subscriberTopics);
            result.put("message", "成功拉取全局订阅拓扑");
        } catch (Exception e) {
            result.put("error", "Failed to dump subscription tree: " + e.getMessage());
        }
        return result;
    }

    // =========================================================================
    // 8. 引擎队列状态查询 (Engine Queues Status)
    // =========================================================================

    @Autowired
    private com.cmbc.mds.forex.engine.MarketDataChannelRegistry channelRegistry;

    /**
     * 查询当前活跃的 Clean 和 Merge 通道。
     */
    @GetMapping("/engine/queues")
    public Map<String, Object> getEngineQueuesStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            java.lang.reflect.Field cleanField = com.cmbc.mds.forex.engine.MarketDataChannelRegistry.class.getDeclaredField("cleanQueues");
            cleanField.setAccessible(true);
            Map<?, ?> cleanQueues = (Map<?, ?>) cleanField.get(channelRegistry);

            java.lang.reflect.Field mergeField = com.cmbc.mds.forex.engine.MarketDataChannelRegistry.class.getDeclaredField("mergeQueues");
            mergeField.setAccessible(true);
            Map<?, ?> mergeQueues = (Map<?, ?>) mergeField.get(channelRegistry);

            result.put("activeCleanChannels", cleanQueues.size());
            result.put("cleanChannelIds", cleanQueues.keySet());
            result.put("activeMergeChannels", mergeQueues.size());
            result.put("mergeChannelIds", mergeQueues.keySet());
            result.put("message", "✓ 成功获取引擎通道拓扑");
        } catch (Exception e) {
            result.put("error", "Failed to get engine queues: " + e.getMessage());
        }
        return result;
    }

    // =========================================================================
    // 9. 价格源白名单与配置转储 (Provider Config Inspector)
    // =========================================================================

    @Autowired
    private SourceConfigService sourceConfigService;

    /**
     * 转储内存中加载的所有合法 Provider 及其规则配置
     */
    @GetMapping("/provider/config-dump")
    public Map<String, Object> getProviderConfigDump() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            java.lang.reflect.Field metaField = SourceConfigService.class.getDeclaredField("metadataCache");
            metaField.setAccessible(true);
            Map<?, ?> metadataCache = (Map<?, ?>) metaField.get(sourceConfigService);

            result.put("totalProviders", metadataCache.size());
            result.put("configs", metadataCache);
            result.put("message", "✓ 成功拉取数据源静态配置");
        } catch (Exception e) {
            result.put("error", "Failed to dump provider config: " + e.getMessage());
        }
        return result;
    }

    // =========================================================================
    // 内部工具方法
    // =========================================================================

    /**
     * 构建聚合行情摘要（方便测试查看关键价格字段，不暴露全部深度数据）
     */
    private Object buildMergePriceSummary(PloyPrices prices) {
        return prices;
    }

    private Map<String, Object> notFound(String cacheName, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", false);
        result.put("cache", cacheName);
        result.put("key", key);
        result.put("message", "缓存中无此数据，可能原因：①未订阅 ②行情未推送 ③行情被前置过滤丢弃");
        return result;
    }

    // =========================================================================
    // 内部请求 DTO
    // =========================================================================

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class MergePriceQueryReq {
        private List<String> sources;
        private List<String> providers;
        private String symbol;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class TopicBatchCheckReq {
        private List<String> topicKeys;
    }
}
