package com.cmbc.oms.domain.position.model.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author chendaqian
 * @date 2026/4/3
 * @time 11:51
 * @description 持仓明细(一般策略下单时使用,常用Positions转换得到)
 */
@Data
public class PositionDeal {
    private BigDecimal qty;                /**可用持仓量*/
    private BigDecimal LastQty;            /*上日持仓量*/
    private BigDecimal todayQty;           /*今日持仓量*/
    private BigDecimal OffsetQty;          /*可平量 成交量*/
    private BigDecimal OffsetLastQty;      /*可平昨 未成交数量*/
    private BigDecimal OffsetTodayQty;     /*可平今 撤单数量*/
    private BigDecimal frozenQty;          /**冻结持仓量*/
    private BigDecimal totalQty;           /**总持仓量*/
    private BigDecimal dealQty;            /**策略成交量*/
    private BigDecimal amt;                /**持仓金额*/
    private BigDecimal dealAmt;            /**策略成交金额*/
    private BigDecimal margin;             /**保证金*/
    private BigDecimal avePrice;           /**持仓均价*/
    private BigDecimal lastPrice;          /**最新价*/
    private BigDecimal profitFund;         /**平仓盈亏*/
    private BigDecimal floatProfitLoss;    /**持仓盈亏*/
    private String tradePurpose;           /**交易目的*/
}
