package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;

import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/3/9
 * @time 15:20
 * @description 查询交易员库存响应 现货
 */
@EventField(name = "com.finesys.dimp.RspTraderQryStorage")
public class RspTraderQryStorageEvent {

    @EventField(name = "uniqueID", order = 1)
    private String uniqueID;

    @EventField(name = "TraderNo", order = 2)
    private String traderNo;            /*交易员号*/

    @EventField(name = "MemberID", order = 3)
    private String memberID;            /*会员号*/

    @EventField(name = "ContractID", order = 4)
    private String contractID;          /*合约代码*/

    @EventField(name = "VarietyID", order = 5)
    private String varietyID;           /*交割品种代码*/

    @EventField(name = "TotalStorage", order = 6)
    private double totalStorage;        /*库存总量*/

    @EventField(name = "AvailableStorage", order = 7)
    private double availableStorage;    /*可用库存*/

    @EventField(name = "FrozenStorage", order = 8)
    private double frozenStorage;       /*现货冻结库存*/

    @EventField(name = "PendStorage", order = 9)
    private double pendStorage;         /*待提库存*/

    @EventField(name = "ImpawnStorage", order = 10)
    private double impawnStorage;       /*质押库存*/

    @EventField(name = "LawFrozen", order = 11)
    private double lawFrozen;           /*法律冻结库存*/

    @EventField(name = "TradePurpose", order = 12)
    private String tradePurpose;        /*交易目的*/

    @EventField(name = "SetoffFrozen", order = 13)
    private double setoffFrozen;        /*充抵冻结库存*/

    @EventField(name = "TransferFrozen", order = 14)
    private double transferFrozen;      /*过户业务冻结库存*/

    @EventField(name = "isSuccess", order = 15)
    private boolean isSuccess;

    @EventField(name = "bIsLast", order = 16)
    private boolean bIsLast;

    @EventField(name = "extraParas", order = 17)
    private Map<String, String> extraParas; //key ErrorID

    public String getUniqueID() { return uniqueID; }

    public RspTraderQryStorageEvent setUniqueID(String uniqueID) {
        this.uniqueID = uniqueID;
        return this;
    }

    public String getTraderNo() { return traderNo; }

    public RspTraderQryStorageEvent setTraderNo(String traderNo) {
        this.traderNo = traderNo;
        return this;
    }

    public String getMemberID() { return memberID; }

    public RspTraderQryStorageEvent setMemberID(String memberID) {
        this.memberID = memberID;
        return this;
    }

    public String getContractID() { return contractID; }

    public RspTraderQryStorageEvent setContractID(String contractID) {
        this.contractID = contractID;
        return this;
    }

    public String getVarietyID() { return varietyID; }

    public RspTraderQryStorageEvent setVarietyID(String varietyID) {
        this.varietyID = varietyID;
        return this;
    }

    public double getTotalStorage() { return totalStorage; }

    public RspTraderQryStorageEvent setTotalStorage(double totalStorage) {
        this.totalStorage = totalStorage;
        return this;
    }

    public double getAvailableStorage() { return availableStorage; }

    public RspTraderQryStorageEvent setAvailableStorage(double availableStorage) {
        this.availableStorage = availableStorage;
        return this;
    }

    public double getFrozenStorage() { return frozenStorage; }

    public RspTraderQryStorageEvent setFrozenStorage(double frozenStorage) {
        this.frozenStorage = frozenStorage;
        return this;
    }

    public double getPendStorage() { return pendStorage; }

    public RspTraderQryStorageEvent setPendStorage(double pendStorage) {
        this.pendStorage = pendStorage;
        return this;
    }

    public double getImpawnStorage() { return impawnStorage; }

    public RspTraderQryStorageEvent setTransferFrozen(double transferFrozen) {
        this.transferFrozen = transferFrozen;
        return this;
    }

    public boolean isSuccess() { return isSuccess; }

    public RspTraderQryStorageEvent setSuccess(boolean success) {
        isSuccess = success;
        return this;
    }

    public boolean isbIsLast() { return bIsLast; }

    public RspTraderQryStorageEvent setbIsLast(boolean bIsLast) {
        this.bIsLast = bIsLast;
        return this;
    }

    public Map<String, String> getExtraParas() { return extraParas; }

    public RspTraderQryStorageEvent setExtraParas(Map<String, String> extraParas) {
        this.extraParas = extraParas;
        return this;
    }
}
