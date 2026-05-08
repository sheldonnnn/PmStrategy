package com.cmbc.oms.domain.exposure.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PositionVo {

    private int id;                        // 序号

    private String name;                   // 名称

    private String symbol;                 // 交易品种

    private BigDecimal netPosition;        // 净头寸

    private BigDecimal netAmount;          // 平盘金额

    private BigDecimal bidPrice;           // 最新行情买入价

    private BigDecimal mktPrice;           // 最新行情价格

    private BigDecimal avgPrice;           // 平均价格

    private BigDecimal profitLoss;         // 浮动损益

    private String positionUpdateTime;     // 头寸更新时间

    private String depthUpdateTime;        // 行情更新时间

    private BigDecimal baseSpread;         // 基差

    private BigDecimal spreadPL;           // 基差盈亏
}
