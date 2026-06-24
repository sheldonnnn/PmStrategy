package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;

import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/3/9
 * @time 15:17
 * @description 查询交易员库存请求事件 现货
 */
@EventField(name = "com.finesys.dimp.ReqTraderQryStorage")
public class ReqTraderQryStorageEvent {
    @EventField(name = "uniqueID", order = 1)
    private String uniqueID;          /*请求唯一ID*/

    @EventField(name = "TraderNo", order = 2)
    private String traderNo;          /*交易员 必填;*/

    @EventField(name = "extraParas", order = 3)
    private Map<String, String> extraParas;

    public String getUniqueID() { return uniqueID; }

    public ReqTraderQryStorageEvent setUniqueID(String uniqueID) {
        this.uniqueID = uniqueID;
        return this;
    }

    public String getTraderNo() { return traderNo; }

    public ReqTraderQryStorageEvent setTraderNo(String traderNo) {
        this.traderNo = traderNo;
        return this;
    }

    public Map<String, String> getExtraParas() { return extraParas; }

    public ReqTraderQryStorageEvent setExtraParas(Map<String, String> extraParas) {
        this.extraParas = extraParas;
        return this;
    }
}
