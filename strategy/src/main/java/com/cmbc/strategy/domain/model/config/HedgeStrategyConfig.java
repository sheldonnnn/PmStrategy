package com.cmbc.strategy.domain.model.config;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
@Data
public class HedgeStrategyConfig extends StrategyConfig {

    private String instanceId;

        // ==========================================
        // 1. 基础信息配置 (Basic Info)
        // ==========================================

        /**
         * 策略配置ID (主键)
         * DB: CONFIG_ID (VARCHAR2)
         */
        private String configId;

        /**
         * 策略名称
         * DB: STRATEGY_NAME (VARCHAR2)
         */
        private String strategyName;

        /**
         * 交易标签
         *
         */
        private String tradeTag;

        // ==========================================
        // 2. 平盘下单参数 (Hedging Order Params)
        // ==========================================

        /**
         * 下单模式
         * A: 激进 (Aggressive), N: 中性 (Neutral), D: 防守 (Defensive)
         * DB: ORDER_MODE (VARCHAR2)
         */
        private String tradeMode;

        /**
         * 价格基准类型 (如: 对手价、中间价、最新价)
         * DB: PRICE_BASE_TYPE (VARCHAR2)
         */
        private String priceBaseType;

        /**
         * 买入点差 (用于调整下单价格)
         * DB: BID_SPREAD (NUMBER 10,4)
         */
        private BigDecimal bidSpread;

        /**
         * 卖出点差 (用于调整下单价格)
         * DB: OFR_SPREAD (NUMBER 10,4)
         */
        private BigDecimal ofrSpread;

        /**
         * 单笔最大下单量
         * DB: MAX_QTY (NUMBER 16)
         */
        private BigDecimal maxOrderQty;

        /**
         * 报单间隔时间 (单位: 秒)
         * DB: ORDER_INTERVAL_SEC (NUMBER 8,3)
         */
        private BigDecimal orderIntervalSec;

        /**
         * 最大挂单量 (业务含义) / 数据库字段名似乎为"最大止盈次数"
         * 注意：字段名沿用数据库定义以确保映射正确
         * DB: MAX_PROFIT_TIMES (VARCHAR2)
         */
        private String maxQtySum;

        /**
         * 下单弹窗提醒 (0: 不勾选, 1: 勾选)
         * DB: ORDER_REMINDER (VARCHAR2)
         */
        private String orderReminder;

        /**
         * 追单开平类型 (0: 只开不平, 1: 先平后开)
         * DB: TRACKING_FLAT_ORDER_TYPE (VARCHAR2)
         */
        private String chaseFlatOrderType;

        /**
         * 开平类型 (0: 只开不平, 1: 先平后开)
         * 注：此字段与 trackingFlatOrderType 功能类似，依据Image2定义添加
         * DB: OFFSET_FLAG (VARCHAR2)
         */
        private String offsetFlag;

        // ==========================================
        // 3. 追单/监控参数 (Chase & Tracking Params)
        // ==========================================

        /**
         * 追单次数
         * DB: TRACKING_NUMBER (VARCHAR2 50)
         */
        private Integer chaseNumber;

        /**
         * 追单报价偏离度
         * DB: TRACKING_ORDER_DEVIATION (VARCHAR2 50)
         */
        private BigDecimal chaseOrderDeviation;

        /**
         * 单笔挂单超时撤单时间 (单位: 秒)
         * DB: ORDER_TIMEOUT_SEC (NUMBER 10,3)
         */
        private BigDecimal orderTimeoutSec;

        /**
         * 追单触发时间 / 平盘最大耗时 (单位: 分钟)
         * DB: HEDGING_MAX_TIME (NUMBER 12,3)
         */
        private BigDecimal hedgingMaxTime;

        /**
         * 追单挂单价格基准
         * DB: CHASE_PRICE_TYPE (VARCHAR2)
         */
        private String chasePriceType;

        /**
         * 买入追单点差
         * DB: BUY_CHASE_SPREAD (NUMBER 10,4)
         */
        private BigDecimal buyChaseSpread;

        /**
         * 卖出追单点差
         * DB: SELL_CHASE_SPREAD (NUMBER 10,4)
         */
        private BigDecimal sellChaseSpread;

        /**
         * 追单单笔最大下单量
         * DB: CHASE_MAX_ORDER_QTY (NUMBER 16)
         */
        private BigDecimal chaseMaxOrderQty;

        /**
         * 追单最大容忍时间 (单位: 秒)
         * DB: CHASE_MAX_DURATION (NUMBER 12,3)
         */
        private BigDecimal chaseMaxDuration;

    /**
     * 分时段平盘规则列表
     * 对应关系: 一条策略配置 -> 多条时间段规则
     */
    private List<SymbolTimeSlice> symbolTimeSlices;
    /**
     * 查找对应合约
     */
    public SymbolTimeSlice findSlice(LocalTime time) {
        if (symbolTimeSlices == null) return null;
        for (SymbolTimeSlice slice : symbolTimeSlices) {
            LocalTime start = slice.getStartTime();
            LocalTime end = slice.getEndTime();
            if (!time.isBefore(start) && time.isBefore(end)) {
                return slice;
            }
        }
        return null;
    }

}
