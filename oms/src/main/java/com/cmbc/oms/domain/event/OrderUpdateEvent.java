package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;

import java.util.Map;

/**
 * @Author: chendaqian
 * @Date: 2026/01/26 15:15
 * @Description: OrderUpdate事件定义
 */
@EventField(name = "com.apama.oms.OrderUpdate")
public class OrderUpdateEvent {
    @EventField(name = "orderId", order = 1)
    private String orderId;
    @EventField(name = "symbol", order = 2)
    private String symbol;
    @EventField(name = "price", order = 3)
    private double price;       // 委托价格
    @EventField(name = "side", order = 4)
    private String side;
    @EventField(name = "type", order = 5)
    private String type;
    @EventField(name = "quantity", order = 6)
    private double quantity;    // 委托数量
    @EventField(name = "isSend", order = 7)
    private boolean isSend;             // 是否已委托
    @EventField(name = "isSendConfirm", order = 8)
    private boolean isSendConfirm;      // 是否已收到委托确认
    @EventField(name = "isInMarket", order = 9)
    private boolean isInMarket;         // 是否在市场中
    @EventField(name = "isRejected", order = 10)
    private boolean isRejected;         // 是否拒绝
    @EventField(name = "isTimeout", order = 11)
    private boolean isTimeout;          // 是否超时
    @EventField(name = "isError", order = 12)
    private boolean isError;            // 是否错误
    @EventField(name = "isCancelState", order = 13)
    private boolean isCancelState;      // 是否为已撤单状态
    @EventField(name = "sysOrderNo", order = 14)
    private String sysOrderNo;          // 系统编号
    @EventField(name = "cumQty", order = 15)
    private int cumQty;                 // 累计成交数量
    @EventField(name = "leaveQty", order = 16)
    private int leaveQty;               // 剩余成交量
    @EventField(name = "lastQty", order = 17)
    private int lastQty;                // 成交量
    @EventField(name = "lastPrice", order = 18)
    private double lastPrice;           // 成交价格
    @EventField(name = "avgPrice", order = 19)
    private double avgPrice;            // 成交平均价格
    @EventField(name = "matchDescription", order = 20)
    private String matchDescription;    // 成交描述
    @EventField(name = "extraParams", order = 21)
    private Map<String, String> extraParams;

    public String getOrderId() { return orderId; }

    public OrderUpdateEvent setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public String getSymbol() { return symbol; }

    public OrderUpdateEvent setSymbol(String symbol) {
        this.symbol = symbol;
        return this;
    }

    public double getPrice() { return price; }

    public OrderUpdateEvent setPrice(double price) {
        this.price = price;
        return this;
    }

    public String getSide() { return side; }

    public OrderUpdateEvent setSide(String side) {
        this.type = type; // As in screenshot
        return this;
    }

    public double getQuantity() { return quantity; }

    public OrderUpdateEvent setQuantity(double quantity) {
        this.quantity = quantity;
        return this;
    }

    public boolean getIsSend() { return isSend; }

    public OrderUpdateEvent setIsSend(boolean send) {
        isSend = send;
        return this;
    }

    public boolean getIsSendConfirm() { return isSendConfirm; }

    public OrderUpdateEvent setIsSendConfirm(boolean sendConfirm) {
        isSendConfirm = sendConfirm;
        return this;
    }

    public boolean getIsInMarket() { return isInMarket; }

    public OrderUpdateEvent setIsInMarket(boolean inMarket) {
        isInMarket = inMarket;
        return this;
    }

    public boolean getIsRejected() { return isRejected; }

    public OrderUpdateEvent setIsRejected(boolean rejected) {
        isRejected = rejected;
        return this;
    }

    public boolean getIsTimeout() { return isTimeout; }

    public OrderUpdateEvent setIsTimeout(boolean timeout) {
        isTimeout = timeout;
        return this;
    }

    public boolean getIsError() { return isError; }

    public OrderUpdateEvent setIsError(boolean error) {
        isError = error;
        return this;
    }

    public boolean getIsCancelState() { return isCancelState; }

    public OrderUpdateEvent setIsCancelState(boolean cancelState) {
        isCancelState = cancelState;
        return this;
    }

    public String getSysOrderNo() { return sysOrderNo; }

    public OrderUpdateEvent setSysOrderNo(String sysOrderNo) {
        this.sysOrderNo = sysOrderNo;
        return this;
    }

    public double getLastPrice() { return lastPrice; }

    public OrderUpdateEvent setLastPrice(double lastPrice) {
        this.lastPrice = lastPrice;
        return this;
    }

    public double getAvgPrice() { return avgPrice; }

    public OrderUpdateEvent setAvgPrice(double avgPrice) {
        this.avgPrice = avgPrice;
        return this;
    }

    public String getMatchDescription() { return matchDescription; }

    public OrderUpdateEvent setMatchDescription(String matchDescription) {
        this.matchDescription = matchDescription;
        return this;
    }

    public Map<String, String> getExtraParams() { return extraParams; }

    public OrderUpdateEvent setExtraParams(Map<String, String> extraParams) {
        this.extraParams = extraParams;
        return this;
    }
}
