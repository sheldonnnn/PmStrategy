package com.cmbc.oms.domain.exposure.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class MgapPositionSnapshot implements Serializable {
    private String symbol;
    private String currency;
    private BigDecimal qty;
    private BigDecimal amt;
    private BigDecimal mktPrice;//行情价格
    private BigDecimal price;// 成本价格
    private BigDecimal unrealizedPL;
    private String positionTime;
    private String priceTime;

    public String getSymbol() { return symbol; }
    
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public String getCurrency() { return currency; }
    
    public void setCurrency(String currency) { this.currency = currency; }
    
    public BigDecimal getQty() { return qty; }
    
    public void setQty(BigDecimal qty) { this.qty = qty; }
    
    public BigDecimal getMktPrice() { return mktPrice; }
    
    public void setMktPrice(BigDecimal mktPrice) { this.mktPrice = mktPrice; }
    
    public BigDecimal getAmt() { return amt; }
    
    public void setAmt(BigDecimal amt) { this.amt = amt; }
    
    public BigDecimal getPrice() { return price; }
    
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public BigDecimal getUnrealizedPL() { return unrealizedPL; }
    
    public void setUnrealizedPL(BigDecimal unrealizedPL) { this.unrealizedPL = unrealizedPL; }
    
    public String getPositionTime() { return positionTime; }
    
    public void setPositionTime(String positionTime) { this.positionTime = positionTime; }
    
    public String getPriceTime() { return priceTime; }
    
    public void setPriceTime(String priceTime) { this.priceTime = priceTime; }
}
