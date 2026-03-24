package com.cmbc.strategy.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 策略实例运行记录实体
 * 对应表: GOLD_STRATEGY_INSTANCE
 */
@Data
public class HedgeStrategyInstanceEntity {



        // ================= 身份标识 =================
        /**
         * 策略实例ID (主键, 由管理端生成传入)
         */
        private String instanceId;

        /**
         * 基础配置ID
         */
        private String baseConfigId;

        /**
         * 合约规则组ID
         */
        private String symbolConfigId;

        /**
         * 交易标签
         */
        private String tagId;

        // ================= 运行状态 =================
        /**
         * 执行状态
         * 见 StrategyStatus 枚举 (0:Created, 1:Monitoring, 2:Hedging, 4:Paused, 10:Stopped, 99:Meltdown)
         */
        private Integer status;

        /**
         * 停止/熔断原因
         * (e.g. "User Request", "Gap Limit Exceeded", "Market Data Timeout")
         */
        private String stopReason;

        // ================= 统计指标 (Metrics) =================
        /**
         * 累计买入成交量 (克)
         */
        private BigDecimal totalBuyQty;

        /**
         * 累计卖出成交量 (克)
         */
        private BigDecimal totalSellQty;

        /**
         * 累计成交笔数
         */
        private Integer totalDealCount;

        // ================= 审计信息 =================
        /**
         * 操作员/启动人
         */
        private String operatorName;

        /**
         * 配置快照 (JSON字符串)
         * 将启动时的 GoldHedgingConfig 序列化保存，用于事后审计
         */
        private String configSnapshot;

        // ================= 时间戳 =================
        /**
         * 启动时间
         */
        private LocalDateTime creatTime;

        /**
         * 最新更新时间 (心跳)
         */
        private LocalDateTime updateTime;



}
