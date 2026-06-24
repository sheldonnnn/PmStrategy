package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @Author: Cly
 * @Date: 2026/01/23 19:29
 * @Description:
 */
@EventField(name = "com.apama.oms.NewOrder")
public class NewOrderEvent {

    @EventField(name = "orderId", order = 1)
    private String orderId;
    @EventField(name = "symbol", order = 2)
    private String symbol;
    @EventField(name = "price", order = 3)
    private BigDecimal price;
    @EventField(name = "side", order = 4)
    private String side;
    @EventField(name = "type", order = 5)
    private String type;
    @EventField(name = "quantity", order = 6)
    private Integer quantity;
    @EventField(name = "serviceId", order = 7)
    private String serviceId;
    @EventField(name = "brokerId", order = 8)
    private String brokerId;
    @EventField(name = "bookId", order = 9)
    private String bookId;
    @EventField(name = "marketId", order = 10)
    private String marketId;
    @EventField(name = "exchange", order = 11)
    private String exchange;
    @EventField(name = "ownerId", order = 12)
    private String ownerId;
    @EventField(name = "extraParams", order = 13)
    private Map<String, String> extraParams;

    public String getOrderId() { return orderId; }

    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getSymbol() { return symbol; }

    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getPrice() { return price; }

    public void setPrice(BigDecimal price) { this.price = price; }

    public String getSide() { return side; }

    public void setSide(String side) { this.side = side; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public Integer getQuantity() { return quantity; }

    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getServiceId() { return serviceId; }

    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getBrokerId() { return brokerId; }

    public void setBrokerId(String brokerId) { this.brokerId = brokerId; }

    public String getBookId() { return bookId; }

    public void setBookId(String bookId) { this.bookId = bookId; }

    public String getMarketId() { return marketId; }

    public void setMarketId(String marketId) { this.marketId = marketId; }

    public String getExchange() { return exchange; }

    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getOwnerId() { return ownerId; }

    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public Map<String, String> getExtraParams() { return extraParams; }

    public void setExtraParams(Map<String, String> extraParams) { this.extraParams = extraParams; }
}
