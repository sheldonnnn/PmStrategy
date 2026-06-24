package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;

import java.math.BigDecimal;
import java.util.Map;

@EventField(name = "com.finesys.order.statics.MgapIncrementalOrder")
public class MgapIncrementalOrderEvent {

    @EventField(name = "businessType", order = 1)
    private String businessType;
    @EventField(name = "serviceId", order = 2)
    private String serviceId;
    @EventField(name = "counterParty", order = 3)
    private String counterParty;
    @EventField(name = "dataSource", order = 4)
    private String dataSource;
    @EventField(name = "orderId", order = 5)
    private String orderId;
    @EventField(name = "localOrderNo", order = 6)
    private String localOrderNo;
    @EventField(name = "sysOrderNo", order = 7)
    private String sysOrderNo;
    @EventField(name = "matchNo", order = 8)
    private String matchNo;
    @EventField(name = "strategyId", order = 9)
    private String strategyId;
    @EventField(name = "traderNo", order = 11)
    private String userName;
    @EventField(name = "memberId", order = 13)
    private String memberId;
    @EventField(name = "clientId", order = 14)
    private String clientId;
    @EventField(name = "orderAttr", order = 27)
    private String tradePurpose;
    @EventField(name = "type", order = 29)
    private String type;
    @EventField(name = "exchCode", order = 30)
    private String exchCode;
    //    @EventField(name = "orderStatus", order = 31)
    //    private String orderStatus;
    //    @EventField(name = "orderStatusCode", order = 32)
    //    private Integer orderStatusCode;
    //    @EventField(name = "level", order = 33)
    //    private String level;
    //    @EventField(name = "orderTimeStamp", order = 34)
    //    private String orderTimeStamp;
    @EventField(name = "extraParas", order = 31)
    private Map<String, String> extraParas;

    private String side;
    private Integer orderQty;
    private BigDecimal dealPrice;
    private Integer dealQty;
    private String instanceId;
    private String traderNo;
    private String inventoryType;
    private String domesticType;
    private String symbol;
    private String eoFlag;
    private String shFlag;
    private double price;
    private Integer leaveQty;
    private String orderType;
    private String orderAttr;

    public String getLocalOrderNo() { return localOrderNo; }

    public void setLocalOrderNo(String localOrderNo) { this.localOrderNo = localOrderNo; }

    public String getSysOrderNo() { return sysOrderNo; }

    public void setSysOrderNo(String sysOrderNo) { this.sysOrderNo = sysOrderNo; }

    public String getSide() { return side; }

    public void setSide(String side) { this.side = side; }

    public Integer getOrderQty() { return orderQty; }

    public void setOrderQty(Integer orderQty) { this.orderQty = orderQty; }

    public BigDecimal getDealPrice() { return dealPrice; }

    public void setDealPrice(BigDecimal dealPrice) { this.dealPrice = dealPrice; }

    public Integer getDealQty() { return dealQty; }

    public void setDealQty(Integer dealQty) { this.dealQty = dealQty; }

    public Map<String, String> getExtraParas() { return extraParas; }

    public void setExtraParas(Map<String, String> extraParas) { this.extraParas = extraParas; }

    public String getStrategyId() { return strategyId; }

    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

    public String getInstanceId() { return instanceId; }

    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getTraderNo() { return traderNo; }

    public void setTraderNo(String traderNo) { this.traderNo = traderNo; }

    public String getUserName() { return userName; }

    public void setUserName(String userName) { this.userName = userName; }

    public String getMemberId() { return memberId; }

    public void setMemberId(String memberId) { this.memberId = memberId; }

    public String getClientId() { return clientId; }

    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getInventoryType() { return inventoryType; }

    public void setInventoryType(String inventoryType) { this.inventoryType = inventoryType; }

    public String getDomesticType() { return domesticType; }

    public void setDomesticType(String domesticType) { this.domesticType = domesticType; }

    public String getSymbol() { return symbol; }

    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getEoFlag() { return eoFlag; }

    public void setEoFlag(String eoFlag) { this.eoFlag = eoFlag; }

    public String getShFlag() { return shFlag; }

    public void setShFlag(String shFlag) { this.shFlag = shFlag; }

    public double getPrice() { return price; }

    public void setPrice(double price) { this.price = price; }

    public Integer getLeaveQty() { return leaveQty; }

    public void setLeaveQty(Integer leaveQty) { this.leaveQty = leaveQty; }

    public String getOrderType() { return orderType; }

    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getOrderAttr() { return orderAttr; }

    public void setOrderAttr(String orderAttr) { this.orderAttr = orderAttr; }

    public String getTradePurpose() { return tradePurpose; }

    public void setTradePurpose(String tradePurpose) { this.tradePurpose = tradePurpose; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public String getExchCode() { return exchCode; }

    public void setExchCode(String exchCode) { this.exchCode = exchCode; }

    public String getBusinessType() { return businessType; }

    public void setBusinessType(String businessType) { this.businessType = businessType; }

    public String getServiceId() { return serviceId; }

    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getCounterParty() { return counterParty; }

    public void setCounterParty(String counterParty) { this.counterParty = counterParty; }

    public String getDataSource() { return dataSource; }

    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public String getOrderId() { return orderId; }

    public void setOrderId(String orderUniqueId) { this.orderId = orderUniqueId; }

    public String getMatchNo() { return matchNo; }

    public void setMatchNo(String matchNo) { this.matchNo = matchNo; }
}
