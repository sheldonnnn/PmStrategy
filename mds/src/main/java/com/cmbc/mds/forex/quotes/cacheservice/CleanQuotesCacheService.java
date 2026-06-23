package com.cmbc.mds.forex.quotes.cacheservice;

import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 清洗后行情缓存
 * 场景：Latest Wins (最新值覆盖)，无需聚合
 * 方案：直接使用 ConcurrentHashMap
 */
@Component
public class CleanQuotesCacheService {

    private static final Logger log = LoggerFactory.getLogger(CleanQuotesCacheService.class);

    private final Map<String, Depth> cache = new ConcurrentHashMap<>();

    public void cacheDepth(String key, Depth depth) {
        if (depth == null) {
            return;
        }
        // 直接覆盖，put 是线程安全的
        cache.put(key, depth);
    }

    public Depth getDepth(String key) {
        return cache.get(key); // 无锁读
    }

    public Depth getDepth(String source, String provider, String symbol) {
        return cache.get(MarketDataTopic.buildTopicKey(source, provider, symbol)); // 无锁读
    }

    public List<Depth> getAllQuotes() {
        return new ArrayList<>(cache.values());
    }

    /**
     * 删除指定 source 的所有清洗行情缓存（断线时防止脏数据）
     * 通过 Depth.getSource() 精确匹配，避免 Key 字符串解析
     */
    public void removeBySource(String source) {
        int count = 0;
        Iterator<Map.Entry<String, Depth>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Depth> entry = it.next();
            if (source.equals(entry.getValue().getSource())) {
                it.remove();
                count++;
            }
        }
        log.info("[清洗后行情缓存清理] CleanQuotes 已清除 source={} 的条目数={}", source, count);
    }

    @Scheduled(cron = "0 0 3 * * ?")   // 每天 03:00
    @Scheduled(cron = "0 0 16 * * ?")  // 每天 16:00
    public void clearCache() {
        int size = cache.size();
        cache.clear();
        log.info("[清洗后行情缓存] 定时清空完成，共清除 {} 条记录", size);
    }
}