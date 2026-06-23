package com.cmbc.mds.ksd.cache;

import com.cmbc.mds.forex.quotes.dto.DimpleKsdQuoteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class KsdStaticQuoteCacheService {

    // 主缓存：合约ID -> 静态行情快照
    private final Map<String, KsdStaticQuoteInfo> cache = new ConcurrentHashMap<>();

    public void updateFromQuoteEvent(DimpleKsdQuoteEvent data) {
        if (data == null || !StringUtils.hasText(data.getSymbol())) return;

        // 直接构建新对象并覆盖放入缓存。
        // ConcurrentHashMap.put 替换引用是原子的。
        // 这样读取时要么拿到完整的旧对象，要么拿到完整的新对象，绝不会出现“部分新、部分老”的中间态。
        cache.put(data.getSymbol(), buildInfo(data));
    }

    public KsdStaticQuoteInfo getByInstrumentId(String instrumentId) {
        return cache.get(instrumentId);
    }

    public Map<String, KsdStaticQuoteInfo> getAllSnapshot() {
        // 返回完整快照
        return new HashMap<>(cache);
    }

    private KsdStaticQuoteInfo buildInfo(DimpleKsdQuoteEvent data) {
        KsdStaticQuoteInfo info = new KsdStaticQuoteInfo();
        info.setInstrumentId(data.getSymbol());
        info.setOpenPrice(toDimplePrice(data.getOpenPrice()));
        info.setPreClosePrice(toDimplePrice(data.getPreClosePrice()));
        info.setUpperLimitPrice(toDimplePrice(data.getUpperLimitPrice()));
        info.setLowerLimitPrice(toDimplePrice(data.getLowerLimitPrice()));
        info.setUpdateTime(data.getUpdateTime());
        info.setCachedAt(System.currentTimeMillis());
        return info;
    }

    private BigDecimal toDimplePrice(double price) {
        return BigDecimal.valueOf(price).setScale(4, RoundingMode.DOWN);
    }

    @Scheduled(cron = "0 0 3 * * ?")   // 每天 03:00
    @Scheduled(cron = "0 0 16 * * ?")  // 每天 16:00
    public void clearCache() {
        int size = cache.size();
        cache.clear();
        log.info("[KSD静态行情缓存] 定时清空完成，共清除 {} 条记录", size);
    }
}
