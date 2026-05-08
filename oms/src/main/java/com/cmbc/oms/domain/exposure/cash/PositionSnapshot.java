package com.cmbc.oms.domain.exposure.cash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.Getter;

@Data
public class PositionSnapshot {

        private String positionId;
        private String folderId;
        private String symbol;

        private BigDecimal longQty = BigDecimal.ZERO;
        private BigDecimal shortQty = BigDecimal.ZERO;
        private BigDecimal frozenLongQty = BigDecimal.ZERO;
        private BigDecimal frozenShortQty = BigDecimal.ZERO;

        // 扩展维度：克重与金额
        private BigDecimal longWeight = BigDecimal.ZERO;
        private BigDecimal shortWeight = BigDecimal.ZERO;
        private BigDecimal longAmount = BigDecimal.ZERO;
        private BigDecimal shortAmount = BigDecimal.ZERO;

        // 新增冻结扩展维度
        private BigDecimal frozenLongWeight = BigDecimal.ZERO;
        private BigDecimal frozenShortWeight = BigDecimal.ZERO;
        private BigDecimal frozenLongAmount = BigDecimal.ZERO;
        private BigDecimal frozenShortAmount = BigDecimal.ZERO;

        private BigDecimal mktPrice;
        private BigDecimal floatPnl = BigDecimal.ZERO;
        private LocalDateTime depthUpdateTime;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        private final String domesticType;

        // 合约乘数（假设初始化获取，默认为1）
        private final BigDecimal unit;

        public PositionSnapshot(String folderId, String symbol,BigDecimal unit , String domesticType) {
            this.folderId = folderId;
            this.symbol = symbol;
            this.domesticType = domesticType;
            this.unit = unit;
            this.createTime = LocalDateTime.now();
            this.positionId = folderId + "_" + symbol;
        }

        private BigDecimal calWeight(BigDecimal qty){
            BigDecimal addWeight;
            if("0".equals(domesticType)){
                addWeight = qty.multiply(unit);
            }else {
                addWeight = qty.multiply(BigDecimal.valueOf(31.1035));
            }
            return addWeight;
        }
    public BigDecimal getNetQty() {
        return longQty.subtract(shortQty);
    }

    /**
     * 计算净克重 (多头克重 - 空头克重)
     */
    public BigDecimal getNetWeight() {
        return longWeight.subtract(shortWeight);
    }

    /**
     * 计算净金额 (多头金额 - 空头金额)
     */
    public BigDecimal getNetAmount() {
        return shortAmount.subtract(longAmount);
    }
        // --- 核心方法：加锁保证单个合约的更新绝对安全 ---

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
        // 释放冻结量，并增加实际持仓
        releaseFrozenLong(dealQty);

        this.longQty = this.longQty.add(dealQty);
        BigDecimal addWeight = calWeight(dealQty);
        this.longWeight = this.longWeight.add(addWeight);
        this.longAmount = this.longAmount.add(dealAmount);
        this.updateTime = LocalDateTime.now();
    }

        public synchronized void unfreezeAndAddShort(BigDecimal dealQty, BigDecimal dealAmount) {
            releaseFrozenShort(dealQty);

            this.shortQty = this.shortQty.add(dealQty);
            BigDecimal addWeight = calWeight(dealQty);
            this.shortWeight = this.shortWeight.add(addWeight);
            this.shortAmount = this.shortWeight.add(dealAmount);
            this.updateTime = LocalDateTime.now();
        }

        // 释放因撤单等原因未成交的冻结量
        public synchronized void unfreezeLong(BigDecimal qty) {
            releaseFrozenLong(qty);
        }

        public synchronized void unfreezeShort(BigDecimal qty) {
            releaseFrozenShort(qty);
        }

        // 内部辅助方法：按比例释放多头冻结
        private void releaseFrozenLong(BigDecimal unfreezeQty) {
            if (this.frozenLongQty.compareTo(BigDecimal.ZERO) > 0 && unfreezeQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = unfreezeQty.divide(this.frozenLongQty, 8, java.math.RoundingMode.HALF_UP);
                this.frozenLongWeight = this.frozenLongWeight.subtract(this.frozenLongWeight.multiply(ratio)).max(BigDecimal.ZERO);
                this.frozenLongAmount = this.frozenLongAmount.subtract(this.frozenLongAmount.multiply(ratio)).max(BigDecimal.ZERO);
            } else {
                this.frozenLongWeight = BigDecimal.ZERO;
                this.frozenLongAmount = BigDecimal.ZERO;
            }
            this.frozenLongQty = this.frozenLongQty.subtract(unfreezeQty).max(BigDecimal.ZERO);
            if (this.frozenLongQty.compareTo(BigDecimal.ZERO) == 0) {
                this.frozenLongWeight = BigDecimal.ZERO;
                this.frozenLongAmount = BigDecimal.ZERO;
            }
        }

        // 内部辅助方法：按比例释放空头冻结
        private void releaseFrozenShort(BigDecimal unfreezeQty) {
            if (this.frozenShortQty.compareTo(BigDecimal.ZERO) > 0 && unfreezeQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = unfreezeQty.divide(this.frozenShortQty, 8, java.math.RoundingMode.HALF_UP);
                this.frozenShortWffeight = this.frozenShortWeight.subtract(this.frozenShortWeight.multiply(ratio)).max(BigDecimal.ZERO);
                this.frozenShortAmount = this.frozenShortAmount.subtract(this.frozenShortAmount.multiply(ratio)).max(BigDecimal.ZERO);
            } else {
                this.frozenShortWeight = BigDecimal.ZERO;
                this.frozenShortAmount = BigDecimal.ZERO;
            }
            this.frozenShortQty = this.frozenShortQty.subtract(unfreezeQty).max(BigDecimal.ZERO);
            if (this.frozenShortQty.compareTo(BigDecimal.ZERO) == 0) {
                this.frozenShortWeight = BigDecimal.ZERO;
                this.frozenShortAmount = BigDecimal.ZERO;
            }
        }

    /**
     * 当收到最新行情或头寸发生变化时，实时重算浮动损益
     * 必须 synchronized，防止计算期间头寸被 OMS 线程修改
     */
    public synchronized void recalculatePnL(BigDecimal currentMidPx) {
        if (currentMidPx == null) {
            return;
        }

        BigDecimal totalPnL = BigDecimal.ZERO;

        // 1. 计算多头浮动损益 = (当前中间价 - 多头均价) * 多头克重
        if (this.longWeight.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal avgLongPx = this.longAmount.divide(this.longWeight, 4, RoundingMode.HALF_UP);
            BigDecimal longPnL = currentMidPx.subtract(avgLongPx).multiply(this.longWeight);
            totalPnL = totalPnL.add(longPnL);
        }

        // 2. 计算空头浮动损益 = (空头均价 - 当前中间价) * 空头克重
        if (this.shortWeight.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal avgShortPx = this.shortAmount.divide(this.shortWeight, 4, RoundingMode.HALF_UP);
            BigDecimal shortPnL = avgShortPx.subtract(currentMidPx).multiply(this.shortWeight);
            totalPnL = totalPnL.add(shortPnL);
        }

        this.floatPnl = totalPnL;
    }



}
