package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;

import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/3/9
 * @time 15:12
 * @description 请求查询交易员持仓汇总响应 期货
 */
@EventField(name = "com.finesys.dimp.RspTraderPosiAllQry")
public class RspTraderPosiAllQryEvent {
    @EventField(name = "uniqueID", order = 1)
    private String uniqueID;

    @EventField(name = "MemberID", order = 2)
    private String memberID;            /*会员号016811*/

    @EventField(name = "ClientID", order = 3)
    private String clientID;            /*客户0000000168*/

    @EventField(name = "TraderNo", order = 4)
    private String traderNo;            /*交易员号quant11*/

    @EventField(name = "ActArbiContractID", order = 5)
    private String actArbiContractID;   /*套利合约号*/

    @EventField(name = "ContractID", order = 6)
    private String contractID;          /*合约号*/

    @EventField(name = "BsFlag", order = 7)
    private String bsFlag;              /*买卖标志*/

    @EventField(name = "LastQty", order = 9)
    private double lastQty;             /*上日持仓量*/

    @EventField(name = "OffsetQty", order = 11)
    private double offsetQty;           /*可平量 成交量===总持仓量*/

    @EventField(name = "OffsetLastQty", order = 12)
    private double offsetLastQty;       /*可平昨 未成交数量*/

    @EventField(name = "ProfitFund", order = 19)
    private double profitFund;          /*平仓盈亏*/

    @EventField(name = "FloatProfitLoss", order = 20)
    private double floatProfitLoss;     /*持仓盈亏*/

    @EventField(name = "TradePurpose", order = 21)
    private String tradePurpose;        /*交易目的*/

    @EventField(name = "ExposureStr", order = 22)
    private String exposureStr;         /*敞口*/

    @EventField(name = "MarginStr", order = 24)
    private String marginStr;           /*保证金*/

    @EventField(name = "ProfitFundStr", order = 25)
    private String profitFundStr;       /*平仓盈亏*/

    @EventField(name = "FloatProfitLossStr", order = 26)
    private String floatProfitLossStr;  /*持仓盈亏*/

    @EventField(name = "isSuccess", order = 27)
    private boolean isSuccess;

    @EventField(name = "bIsLast", order = 28)
    private boolean bIsLast;

    @EventField(name = "extraParas", order = 29)
    private Map<String, String> extraParas; // key ErrorID

    private double exposure;
    private double amt;
    private double margin;
    private double avePrice;
    private String amtStr;

    public String getUniqueID() { return uniqueID; }

    public RspTraderPosiAllQryEvent setUniqueID(String uniqueID) {
        this.uniqueID = uniqueID;
        return this;
    }

    public String getMemberID() { return memberID; }

    public RspTraderPosiAllQryEvent setMemberID(String memberID) {
        this.memberID = memberID;
        return this;
    }

    public String getClientID() { return clientID; }

    public RspTraderPosiAllQryEvent setBsFlag(String bsFlag) {
        this.bsFlag = bsFlag;
        return this;
    }

    public double getExposure() { return exposure; }

    public RspTraderPosiAllQryEvent setExposure(double exposure) {
        this.exposure = exposure;
        return this;
    }

    public double getAmt() { return amt; }

    public RspTraderPosiAllQryEvent setAmt(double amt) {
        this.amt = amt;
        return this;
    }

    public double getMargin() { return margin; }

    public RspTraderPosiAllQryEvent setMargin(double margin) {
        this.margin = margin;
        return this;
    }

    public double getAvePrice() { return avePrice; }

    public RspTraderPosiAllQryEvent setAvePrice(double avePrice) {
        this.avePrice = avePrice;
        return this;
    }

    public double getProfitFund() { return profitFund; }

    public RspTraderPosiAllQryEvent setFloatProfitLoss(double floatProfitLoss) {
        this.floatProfitLoss = floatProfitLoss;
        return this;
    }

    public String getTradePurpose() { return tradePurpose; }

    public RspTraderPosiAllQryEvent setTradePurpose(String tradePurpose) {
        this.tradePurpose = tradePurpose;
        return this;
    }

    public String getExposureStr() { return exposureStr; }

    public RspTraderPosiAllQryEvent setExposureStr(String exposureStr) {
        this.exposureStr = exposureStr;
        return this;
    }

    public String getAmtStr() { return amtStr; }

    public RspTraderPosiAllQryEvent setAmtStr(String amtStr) {
        this.amtStr = amtStr;
        return this;
    }

    public String getMarginStr() { return marginStr; }

    public RspTraderPosiAllQryEvent setMarginStr(String marginStr) {
        this.marginStr = marginStr;
        return this;
    }

    public String getProfitFundStr() { return profitFundStr; }

    public RspTraderPosiAllQryEvent setProfitFundStr(String profitFundStr) {
        this.profitFundStr = profitFundStr;
        return this;
    }

    public String getFloatProfitLossStr() { return floatProfitLossStr; }

    public RspTraderPosiAllQryEvent setFloatProfitLossStr(String floatProfitLossStr) {
        this.floatProfitLossStr = floatProfitLossStr;
        return this;
    }

    public boolean isSuccess() { return isSuccess; }

    public RspTraderPosiAllQryEvent setSuccess(boolean success) {
        isSuccess = success;
        return this;
    }

    public boolean isbIsLast() { return bIsLast; }

    public RspTraderPosiAllQryEvent setbIsLast(boolean bIsLast) {
        this.bIsLast = bIsLast;
        return this;
    }

    public Map<String, String> getExtraParas() { return extraParas; }

    public RspTraderPosiAllQryEvent setExtraParas(Map<String, String> extraParas) {
        this.extraParas = extraParas;
        return this;
    }
}
