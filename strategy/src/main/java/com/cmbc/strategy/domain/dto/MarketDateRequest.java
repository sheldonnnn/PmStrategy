package com.cmbc.strategy.domain.dto;

import lombok.Data;

@Data
public class MarketDateRequest {

    private String symbol;   // 合约代码
    private String exchange;    // 交易所
    private String provider; // 具体提供方 (LP)

    /**
     * 生成唯一Topic标识，用于去重
     * e.g., "XAUUSD@FXALL@UBS"
     */
    public String getUniqueTopic() {
        return String.format("%s@%s@%s", symbol, exchange, provider == null ? "" : provider);
    }
}
