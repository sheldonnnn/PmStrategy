package com.cmbc.oms.domain.exposure.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 头寸及平盘数据展示值对象 (VO)
 * 对应 getHedgedSymbolPosition() 方法中组装与返回的实体
 */
@Data
public class SymbolPositionVo {

    // 合约代码 (如: XAUUSD, AURBMB)
    private String symbol;

    // 合约名称 (如: 伦敦金(量化平盘), 克黄金/人民币(量化平盘))
    private String name;

    // 境内外类型 (境内: DOMESTIC_TYPE_INNER, 境外: DOMESTIC_TYPE_OUTER)
    private String domesticType;

    // 净金额 / 平盘金额
    private BigDecimal netAmount;

    // 净头寸--左头寸(盎司)---数量
    private BigDecimal netPositionOunce;

    // 净头寸 / 实际持仓数量 (换算成克之后的重量)
    private BigDecimal netPosition;

    // 最新行情卖出价格
    private BigDecimal ofrPrice;

    // 成本均价 / 平均价格
    private BigDecimal avgPrice;

    // 核心价格 / 市场实时价格
    private BigDecimal mktPrice;

    // 浮动盈亏
    private BigDecimal profitLoss;

    // 更新日期 (格式: yyyyMMdd)
    private String updateDate;

    // 更新时间 (格式: HH:mm:ss)
    private String updateTime;

    // 同步日期时间 (格式: yyyyMMdd HH:mm:ss)
    private String synDateTime;

}
