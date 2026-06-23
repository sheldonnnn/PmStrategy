package com.cmbc.mds.forex.quotes.cacheservice;

import com.cmbc.mds.forex.quotes.dto.LatchedQuoteWrapper;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.subscription.core.model.topic.MergeDataTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聚合行情锁存缓存服务
 * 
 * 作用：长久保存最后一次更新的聚合行情快照，不会被普通断线逻辑或无行情逻辑清理，
 *       仅支持手动清理或通过阈值参数动态判断数据是否过期。
 */
@Service
public class MergeQuotesLatchedCacheService {

    private static final Logger log = LoggerFactory.getLogger(MergeQuotesLatchedCacheService.class);

    // 主缓存：TopicKey -> LatchedQuoteWrapper
    private final Map<String, LatchedQuoteWrapper> latchedCache = new ConcurrentHashMap<>();

    // 二级索引：Symbol -> TopicKey (针对系统初始化的全市场聚合)
    private final Map<String, String> systemInitKeyMap = new ConcurrentHashMap<>();

    /**
     * 更新锁存缓存
     * 
     * @param key        策略ID/TopicKey
     * @param ployPrices 聚合后的新对象(通常为Snapshot)
     */
    public void updateLatchedPrices(String key, PloyPrices ployPrices) {
        if (key != null && ployPrices != null) {
            LatchedQuoteWrapper wrapper = new LatchedQuoteWrapper();
            wrapper.setData(ployPrices);
            wrapper.setLastUpdateTime(System.currentTimeMillis());
            wrapper.setExpired(false);
            latchedCache.put(key, wrapper);
        }
    }

    /**
     * 注册系统初始化阶段订阅的专属映射 (Symbol -> TopicKey)
     * 同步支持仅根据 Symbol 获取全市场聚合行情的功能。
     */
    public void registerSystemInitKey(String symbol, String topicKey) {
        if (symbol != null && topicKey != null) {
            systemInitKeyMap.put(symbol, topicKey);
        }
    }

    /**
     * 根据 TopicKey 获取锁存行情
     * 
     * @param key                   缓存Key (TopicKey)
     * @param expireThresholdMillis 过期阈值(毫秒)；如果传 null，则返回的 expired 始终为 false
     * @return 包含行情与是否过期标志的 Wrapper 对象；若不存在则返回 null
     */
    public LatchedQuoteWrapper getLatchedPrices(String key, Long expireThresholdMillis) {
        if (key == null) {
            return null;
        }
        LatchedQuoteWrapper wrapper = latchedCache.get(key);
        return evaluateExpiration(wrapper, expireThresholdMillis);
    }

    /**
     * 重载：根据 sources, providers, symbol 构建 key 后获取锁存行情
     */
    public LatchedQuoteWrapper getLatchedPrices(List<String> sources, List<String> providers, String symbol, Long expireThresholdMillis) {
        String key = MergeDataTopic.buildKey(sources, providers, symbol);
        return getLatchedPrices(key, expireThresholdMillis);
    }

    /**
     * 根据 Symbol 获取系统初始化阶段对应的全市场聚合行情
     * 
     * @param symbol                货币对
     * @param expireThresholdMillis 过期阈值(毫秒)；若为 null 则不过期
     * @return 包含行情与是否过期标志的 Wrapper 对象；若不存在则返回 null
     */
    public LatchedQuoteWrapper getSystemInitLatchedPriceBySymbol(String symbol, Long expireThresholdMillis) {
        if (symbol == null) {
            return null;
        }
        String topicKey = systemInitKeyMap.get(symbol);
        if (topicKey == null) {
            return null;
        }
        return getLatchedPrices(topicKey, expireThresholdMillis);
    }

    /**
     * 手动清理指定 Key 的缓存
     */
    public void removeLatchedPrices(String key) {
        if (key != null) {
            latchedCache.remove(key);
        }
    }

    /**
     * 手动/定时全量清空缓存
     */
    @Scheduled(cron = "0 0 3 * * ?")   // 每天 03:00
    @Scheduled(cron = "0 0 16 * * ?")  // 每天 16:00
    public void clearAll() {
        int size = latchedCache.size();
        latchedCache.clear();
        log.info("[锁存缓存] 定时清空完成，共清除 {} 条锁存行情记录", size);
    }

    /**
     * 获取全量快照，供盘后批量业务使用 (仅返回 Map 副本，不做过期计算)
     */
    public Map<String, LatchedQuoteWrapper> getAllLatchedPricesSnapshot() {
        return new ConcurrentHashMap<>(latchedCache);
    }

    /**
     * 内部方法：计算当前数据是否过期，并将结果写入副本后返回。
     * 若 wrapper 为空或 thresholdMillis 为空，则认为未过期 (false)。
     *
     * <p><b>【BUG-4 修复】防御性拷贝，避免直接修改缓存原对象</b><br>
     * latchedCache.get() 返回的是缓存中对象的直接引用。若在此处直接调用
     * wrapper.setExpired()，则会修改缓存内部的对象状态。在多线程场景下，
     * 两个并发读取方会对同一个 wrapper 实例的 expired 字段产生写-写竞争，
     * 导致其中一方拿到被另一方覆盖的错误过期状态（Race Condition）。
     *
     * <p><b>【为何浅拷贝已足够，无需深拷贝】</b><br>
     * LatchedQuoteWrapper 仅包含三个字段：<br>
     * - {@code long lastUpdateTime}：基本类型（值语义），拷贝后完全独立，无需深拷贝；<br>
     * - {@code boolean expired}：基本类型（值语义），拷贝后完全独立，无需深拷贝；<br>
     * - {@code PloyPrices data}：对象引用，拷贝引用即可——调用方仅读取行情快照，
     *   不会通过此接口修改 PloyPrices 内容（行情更新由 updateLatchedPrices 整体替换）。<br>
     * 因此，对三个字段做赋值级别的浅拷贝，即可使每个调用方持有独立的 copy 对象，
     * 彻底消除对缓存原对象的副作用。
     */
    private LatchedQuoteWrapper evaluateExpiration(LatchedQuoteWrapper wrapper, Long thresholdMillis) {
        if (wrapper == null) {
            return null;
        }
        // 创建浅拷贝，仅对 expired（基本类型）赋新值，不污染缓存中的原始对象
        LatchedQuoteWrapper copy = new LatchedQuoteWrapper();
        copy.setData(wrapper.getData());                     // PloyPrices 引用共享，只读，无需深拷贝
        copy.setLastUpdateTime(wrapper.getLastUpdateTime()); // long 基本类型，值语义，天然独立
        if (thresholdMillis != null) {
            long current = System.currentTimeMillis();
            copy.setExpired((current - wrapper.getLastUpdateTime()) > thresholdMillis);
        } else {
            copy.setExpired(false);
        }
        return copy;
    }
}
