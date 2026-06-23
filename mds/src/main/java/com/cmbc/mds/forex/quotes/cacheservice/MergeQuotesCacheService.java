package com.cmbc.mds.forex.quotes.cacheservice;

import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.subscription.core.model.topic.MergeDataTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MergeQuotesCacheService {

    private static final Logger log = LoggerFactory.getLogger(MergeQuotesCacheService.class);

    // 纯读缓存，不再处理复杂的原子聚合，只接收 Consumer 算好的结果
    private final Map<String, PloyPrices> cache = new ConcurrentHashMap<>();
    
    // [新增] 用于系统初始化阶段的二级快查索引 (Symbol -> TopicKey)
    private final Map<String, String> systemInitKeyMap = new ConcurrentHashMap<>();

    /**
     * 更新缓存 (由 MergeQueueConsumer 调用)
     * 
     * @param key        策略ID
     * @param ployPrices 聚合后的新对象
     */
    public void cachePolyPrices(String key, PloyPrices ployPrices) {
        if (key != null && ployPrices != null) {
            cache.put(key, ployPrices);
        }
    }

    public PloyPrices getPolyPrices(String key) {
        return cache.get(key);
    }

    public PloyPrices getPolyPrices(List<String> sources, List<String> providers, String symbol) {
        return cache.get(MergeDataTopic.buildKey(sources, providers, symbol));
    }

    /**
     * 注册系统初始化阶段订阅的专属映射 (Symbol -> TopicKey)
     */
    public void registerSystemInitKey(String symbol, String topicKey) {
        if (symbol != null && topicKey != null) {
            systemInitKeyMap.put(symbol, topicKey);
        }
    }

    /**
     * 根据 symbol 查询 SYSTEM_INIT 订阅对应的完整 sourceKey（即 MergeDataTopic.getTopicKey()）
     * <p>
     * 供 {@link com.cmbc.mds.forex.distribution.channel.impl.StrategyExecutionChannel}
     * 在 registerBySymbol 注册时做 Key 翻译使用。
     *
     * @return sourceKey，如 "MD:MERGE:[FXALL.JPMC,UBS.UBS]:EUR/USD"；未找到返回 null
     */
    public String getSystemInitSourceKey(String symbol) {
        return systemInitKeyMap.get(symbol);
    }

    public PloyPrices getSystemInitPloyPriceBySymbol(String symbol) {

        if (symbol == null) {
            return null;
        }
        String topicKey = systemInitKeyMap.get(symbol);
        return topicKey != null ? cache.get(topicKey) : null;
    }

    public void removePolyPrices(String key) {
        cache.remove(key);
    }

    public Map<String, PloyPrices> getAllPolyPricesSnapshot() {
        return new ConcurrentHashMap<>(cache);
    }

    @Scheduled(cron = "0 0 3 * * ?")   // 每天 03:00
    @Scheduled(cron = "0 0 16 * * ?")  // 每天 16:00
    public void clearCache() {
        int size = cache.size();
        cache.clear();
        log.info("[无锁聚合行情缓存] 定时清空完成，共清除 {} 条记录", size);
    }
}