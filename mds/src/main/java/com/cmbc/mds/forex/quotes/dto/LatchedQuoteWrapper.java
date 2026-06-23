package com.cmbc.mds.forex.quotes.dto;

import lombok.Data;

/**
 * 聚合行情锁存缓存包装类
 */
@Data
public class LatchedQuoteWrapper {
    /**
     * 行情数据快照
     */
    private PloyPrices data;

    /**
     * 最后更新时间（系统时间毫秒）
     */
    private long lastUpdateTime;

    /**
     * 标志该数据是否已超过阈值而过期 (作为返回值标志)
     */
    private boolean expired;
}
