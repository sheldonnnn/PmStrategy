package com.cmbc.oms.domain.exposure.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PositionBalanceHistoryEntity {

    private String positionId;

    /** 系统ID，标识所属系统 */
    private String systemId;

    /** 归属组组，标识头寸所属的组别 */
    private String folderId;

    /** 合约，标识交易的合约代码 */
    private String symbol;

    /** 多头成交量(手)，多头方向的成交量 */
    private BigDecimal longQty;

    /** 多头重量(g)，多头方向的重量 */
    private BigDecimal longWeight;

    /** 多头金额，多头方向的金额 */
    private BigDecimal longAmount;

    /** 空头成交量，空头方向的成交量 */
    private BigDecimal shortQty;

    /** 空头重量(g)，空头方向的重量 */
    private BigDecimal shortWeight;

    /** 空头金额，空头方向的金额 */
    private BigDecimal shortAmount;

    /** 更新时间，记录数据的最后更新时间 */
    private LocalDateTime updateTime;

    private LocalDateTime createTime;

    private String statisticDate;
    private String domesticType;//境内外标识
    private BigDecimal unit;

}
