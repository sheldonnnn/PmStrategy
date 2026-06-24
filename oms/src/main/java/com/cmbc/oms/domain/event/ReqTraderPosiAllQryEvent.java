package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;

import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/3/9
 * @time 15:09
 * @description 请求查询交易员持仓汇总事件 期货
 */
@EventField(name = "com.finesys.dimp.ReqTraderPosiAllQry")
public class ReqTraderPosiAllQryEvent {

    @EventField(name = "uniqueID", order = 1)
    private String uniqueID;          /*请求唯一ID*/

    @EventField(name = "TraderNo", order = 2)
    private String traderNo;          /*交易员 必填;*/

    @EventField(name = "ContractId", order = 3)
    private String contractId;        /*合约号*/

    @EventField(name = "ContractType", order = 4)
    private String contractType;      /*合约类型 dimple;*/

    @EventField(name = "ActArbiContractID", order = 5)
    private String actArbiContractID; /*套利合约号 dimple;*/

    @EventField(name = "TradePurpose", order = 6)
    private String tradePurpose;      /*交易目的*/

    @EventField(name = "extraParas", order = 7)
    private Map<String, String> extraParas;

    public String getUniqueID() { return uniqueID; }

    public ReqTraderPosiAllQryEvent setUniqueID(String uniqueID) {
        this.uniqueID = uniqueID;
        return this;
    }

    public String getTraderNo() { return traderNo; }

    public ReqTraderPosiAllQryEvent setTraderNo(String traderNo) {
        this.traderNo = traderNo;
        return this;
    }

    public String getContractType() { return contractType; }

    public ReqTraderPosiAllQryEvent setContractType(String contractType) {
        this.contractType = contractType;
        return this;
    }

    public String getActArbiContractID() { return actArbiContractID; }

    public ReqTraderPosiAllQryEvent setActArbiContractID(String actArbiContractID) {
        this.actArbiContractID = actArbiContractID;
        return this;
    }

    public String getTradePurpose() { return tradePurpose; }

    public ReqTraderPosiAllQryEvent setTradePurpose(String tradePurpose) {
        this.tradePurpose = tradePurpose;
        return this;
    }

    public Map<String, String> getExtraParas() { return extraParas; }

    public ReqTraderPosiAllQryEvent setExtraParas(Map<String, String> extraParas) {
        this.extraParas = extraParas;
        return this;
    }
}
