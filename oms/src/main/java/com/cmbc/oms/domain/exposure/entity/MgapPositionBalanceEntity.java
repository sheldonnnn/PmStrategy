package com.cmbc.oms.domain.exposure.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MgapPositionBalanceEntity {

    /** 交易品种，如 XAUUSD, PGCRMB */
    private String symbol;
    /** 品种名称，如 伦敦金, 熊猫币 */
    private String name;
    /** 净头寸 (累计重量/净头寸) */
    private BigDecimal netPosition;
    /** 净金额 (累计金额) */
    private BigDecimal netAmount;
    /** 成本均价 */
    private BigDecimal avgPrice;
    /** 最新市价 */
    private BigDecimal mktPrice;
    /** 浮动盈亏 (本币) */
    private BigDecimal profitLoss;
    /** 更新时间，记录数据的最后更新时间 */
    private LocalDateTime updateTime;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 统计日期，如 20260526 */
    private String statisticDate;
}
