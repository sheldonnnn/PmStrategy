package com.cmbc.strategy.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Data
public class SymbolSliceEntity {


    /**
     * 主键ID
     */
    private String id;

    /**
     * 每个合约配置的唯一标识
     */
    private String configId;

    /**
     * 合约类型
     * DB: CONTRACT_TYPE (VARCHAR2 2)
     */
    private String domesticType;

    /**
     * 合约品种 (如: Au99.99, XAUUSD)
     * DB: SYMBOL (VARCHAR2 20)
     */
    private String symbol;

    /**
     * 交易开始时间 (格式 HH:mm:ss)
     * DB: TRADE_START_TIME (VARCHAR2 10)
     */
    private LocalTime startTime;

    /**
     * 交易结束时间 (格式 HH:mm:ss)
     * DB: TRADE_END_TIME (VARCHAR2 10)
     */
    private LocalTime endTime;

    /**
     * 交易对手
     * DB: COUNTER_PARTY (VARCHAR2 50)
     */
    private List<String> sources;

    /**
     * [标黄] 多头平仓触发线 (达到此敞口值触发买入平盘)
     * DB: TRIGGER_LONG_POSITION (NUMBER 20,3)
     */
    private BigDecimal triggerLongPosition;

    /**
     * [标黄] 多头平仓终止线 (平盘至此敞口值停止)
     * DB: END_LONG_POSITION (NUMBER 20,3)
     */
    private BigDecimal endLongPosition;

    /**
     * [标黄] 空头平仓触发线 (达到此敞口值触发卖出平盘)
     * DB: TRIGGER_SHORT_POSITION (NUMBER 20,3)
     */
    private BigDecimal triggerShortPosition;

    /**
     * [标黄] 空头平仓终止线 (平盘至此敞口值停止)
     * DB: END_SHORT_POSITION (NUMBER 20,3)
     */
    private BigDecimal endShortPosition;

    /**
     * 外汇货币对 (用于国际金折算，如 USDCNH)
     * DB: FX_SYMBOL (VARCHAR2 20)
     */
    private String fxSymbol;

    private BigDecimal unit;

}
