package com.cmbc.mds.ksd.cache;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KsdStaticQuoteInfo {
    private String instrumentId;     // 合约代码
    private volatile BigDecimal openPrice;        // 开盘价
    private volatile BigDecimal preClosePrice;    // 昨日收盘价
    private volatile BigDecimal upperLimitPrice;  // 涨停价
    private volatile BigDecimal lowerLimitPrice;  // 跌停价
    private volatile String updateTime;           // 最新行情更新时间（实时覆盖）
    private volatile long cachedAt;               // 写入缓存时间戳（毫秒，用于运维诊断）
}
