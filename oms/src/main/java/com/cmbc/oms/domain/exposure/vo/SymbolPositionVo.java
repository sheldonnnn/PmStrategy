package com.cmbc.oms.domain.exposure.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
/** 量化平盘头寸使用 */
public class SymbolPositionVo {
    private String name; //交易币种名称名称
    private String symbol;  //交易品种
    private BigDecimal netAmount;   //余额（右头寸）
    private BigDecimal netPosition;   //净头寸（左头寸 (克)
    private BigDecimal netPositionOunce;  //交易敞口 (左头寸盎司)
    private BigDecimal avgPrice;  //成本汇率
    private String domesticType; // 境内境外 0 -境内 1-境外
    private BigDecimal profitLoss;   //浮动盈亏
    private BigDecimal mktPrice;   //行情价-核心价
    private String updateTime; // 更新时间 HH:MM:ss
    private String updateDate; // 更新时间 YYYYMMDD
    private String synDateTime; // 同步日期时间
}
