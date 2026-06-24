package com.cmbc.oms.domain.exposure.model;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PositionSnapshot {
    
    private String positionId;
    private String folderId;
    private String symbol;
    
    //手数
    private BigDecimal longQty = BigDecimal.ZERO;
    //金额
    private BigDecimal longAmount = BigDecimal.ZERO;
    //重量
    private BigDecimal longWeight = BigDecimal.ZERO;
    private BigDecimal shortQty = BigDecimal.ZERO;
    private BigDecimal shortAmount = BigDecimal.ZERO;
    private BigDecimal shortWeight = BigDecimal.ZERO;
    
    private BigDecimal frozenLongQty = BigDecimal.ZERO;
    private BigDecimal frozenLongWeight = BigDecimal.ZERO;
    private BigDecimal frozenLongAmount = BigDecimal.ZERO;
    private BigDecimal frozenShortQty = BigDecimal.ZERO;
    private BigDecimal frozenShortWeight = BigDecimal.ZERO;
    private BigDecimal frozenShortAmount = BigDecimal.ZERO;
    
    private BigDecimal mktPrice;
    private BigDecimal floatPnl = BigDecimal.ZERO;
    private LocalDateTime depthUpdateTime;
    
    private LocalDateTime updateTime;
    private final LocalDateTime createTime;
    
    private final String domesticType;
    
    private final BigDecimal unit;
    
    public PositionSnapshot(String folderId, String symbol, BigDecimal unit, String domesticType) {
        this.folderId = folderId;
        this.symbol = symbol;
        this.positionId = folderId + "_" + symbol;
        this.unit = unit;
        this.domesticType = domesticType;
        this.createTime = LocalDateTime.now();
    }
    
    // --- 核心方法，加锁保证单个合约的更新绝对安全 ---
    
    public synchronized void freezeLong(BigDecimal qty, BigDecimal price) {
        this.frozenLongQty = this.frozenLongQty.add(qty);
        BigDecimal addWeight = calWeight(qty);
        this.frozenLongWeight = this.frozenLongWeight.add(addWeight);
        this.frozenLongAmount = this.frozenLongAmount.add(addWeight.multiply(price));
        this.updateTime = LocalDateTime.now();
    }
    
    public synchronized void freezeShort(BigDecimal qty, BigDecimal price) {
        this.frozenShortQty = this.frozenShortQty.add(qty);
        BigDecimal addWeight = calWeight(qty);
        this.frozenShortWeight = this.frozenShortWeight.add(addWeight);
        this.frozenShortAmount = this.frozenShortAmount.add(addWeight.multiply(price));
        this.updateTime = LocalDateTime.now();
    }
    
    public synchronized void unfreezeAndAddLong(BigDecimal dealQty, BigDecimal dealAmount) {
        // 释放冻结量，并增加实持持仓
        releaseFrozenLong(dealQty);
        
        this.longQty = this.longQty.add(dealQty);
        BigDecimal addWeight = calWeight(dealQty);
        this.longWeight = this.longWeight.add(addWeight);
        this.longAmount = this.longAmount.add(dealAmount);
        this.updateTime = LocalDateTime.now();
    }
    
    public synchronized void unfreezeAndAddShort(BigDecimal dealQty, BigDecimal dealAmount) {
        // 释放冻结量，并增加实持持仓
        releaseFrozenShort(dealQty);
        
        this.shortQty = this.shortQty.add(dealQty);
        BigDecimal addWeight = calWeight(dealQty);
        this.shortWeight = this.shortWeight.add(addWeight);
        this.shortAmount = this.shortAmount.add(dealAmount);
        this.updateTime = LocalDateTime.now();
    }
    
    // 释放因撤单等原因未成交的冻结量
    public synchronized void unfreezeLong(BigDecimal qty) {
        releaseFrozenLong(qty);
        this.updateTime = LocalDateTime.now();
    }
    
    public synchronized void unfreezeShort(BigDecimal qty) {
        releaseFrozenShort(qty);
        this.updateTime = LocalDateTime.now();
    }
    
    //内部辅助方法，释放多头冻结头寸
    private void releaseFrozenLong(BigDecimal qty) {
        if (frozenLongQty.compareTo(BigDecimal.ZERO) <= 0) {
            this.frozenLongWeight = BigDecimal.ZERO;
            this.frozenLongAmount = BigDecimal.ZERO;
        } else {
            BigDecimal ratio = qty.divide(this.frozenLongQty, 8, BigDecimal.ROUND_HALF_UP);
            this.frozenLongQty = this.frozenLongQty.subtract(qty).max(BigDecimal.ZERO);
            BigDecimal subWeight = calWeight(qty);
            this.frozenLongWeight = this.frozenLongWeight.subtract(subWeight).max(BigDecimal.ZERO);
            this.frozenLongAmount = this.frozenLongAmount.subtract(this.longAmount.multiply(ratio)).max(BigDecimal.ZERO);
        }
        this.updateTime = LocalDateTime.now();
    }
    
    private void releaseFrozenShort(BigDecimal qty) {
        
        if (this.frozenShortQty.compareTo(qty) <= 0) {
            this.frozenShortWeight = BigDecimal.ZERO;
            this.frozenShortAmount = BigDecimal.ZERO;
        } else {
            BigDecimal ratio = qty.divide(this.frozenShortQty, 8, BigDecimal.ROUND_HALF_UP);
            this.frozenShortQty = this.frozenShortQty.subtract(qty).max(BigDecimal.ZERO);
            BigDecimal subWeight = calWeight(qty);
            this.frozenShortWeight = this.frozenShortWeight.subtract(subWeight).max(BigDecimal.ZERO);
            this.frozenShortAmount = this.frozenShortAmount.subtract(this.shortAmount.multiply(ratio)).max(BigDecimal.ZERO);
        }
        this.updateTime = LocalDateTime.now();
    }
    
    private BigDecimal calWeight(BigDecimal qty) {
        BigDecimal addWeight;
        if("0".equals(this.domesticType)){
            addWeight = qty.multiply(this.unit);
        }else{
            addWeight = qty.multiply(BusinessConstant.OUNCE_GRAM);
        }
        return addWeight;
    }
    
    public BigDecimal getNetQty() { return longQty.subtract(shortQty); }
    
    public BigDecimal getNetWeight() { return longWeight.subtract(shortWeight); }
    
    public BigDecimal getNetAmount() { return shortAmount.subtract(longAmount); }
    
    public BigDecimal getNetFrozenWeight() { return frozenLongWeight.subtract(frozenShortWeight); }
    /**
     * 计算包含在途挂单的预期敞口（用于事前风控防超卖超买）
     */
    public BigDecimal getExpectedNetQty() {
        BigDecimal expectedLong = longQty.add(frozenLongQty);
        BigDecimal expectedShort = shortQty.add(frozenShortQty);
        return expectedLong.subtract(expectedShort);
    }
    
    public BigDecimal getLongAvgPrice() {
        if (longQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return longAmount.divide(longQty, 2, BigDecimal.ROUND_HALF_UP);
    }
    
    public BigDecimal getShortAvgPrice() {
        if (shortQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return shortAmount.divide(shortQty, 2, BigDecimal.ROUND_HALF_UP);
    }
    
    public BigDecimal getNetAvgPrice() {
        if (getNetQty().compareTo(BigDecimal.ZERO) == 0 || getNetWeight().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if(BaseConstants.DOMESTIC_TYPE_INNER.equals(this.domesticType)){
            return getNetAmount().divide(getNetWeight(), 2, BigDecimal.ROUND_HALF_UP).negate();
        }
        return getNetAmount().divide(getNetQty(), 3, BigDecimal.ROUND_HALF_UP).negate();
    }
    
    //当收到最新行情或头寸发生变化时，实时计算浮盈浮亏
    public synchronized void calFloatPnl(BigDecimal mktPrice) {
        if(mktPrice == null){
            return;
        }
        BigDecimal longPnl = BigDecimal.ZERO;
        BigDecimal shortPnl = BigDecimal.ZERO;
        if(BaseConstants.DOMESTIC_TYPE_INNER.equals(this.domesticType)){
            longPnl = longWeight.multiply(mktPrice).subtract(longAmount);
            shortPnl = shortAmount.subtract(shortWeight.multiply(mktPrice));
        }else {
            longPnl = longQty.multiply(mktPrice).subtract(longAmount);      //todo 美元转换为RMB?
            shortPnl = shortAmount.subtract(shortQty.multiply(mktPrice));
        }
        this.floatPnl = longPnl.add(shortPnl);
    }
    
    public synchronized void updateMarketData(BigDecimal mktPrice) {
        if(mktPrice == null){
            return;
        }
        this.mktPrice = mktPrice;
        this.depthUpdateTime = LocalDateTime.now();
        calFloatPnl(mktPrice);
    }
}
