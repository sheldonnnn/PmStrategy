package com.cmbc.oms.cash;

import java.math.BigDecimal;

import lombok.Getter;

@Getter
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

        // 合约乘数（假设初始化获取，默认为1）
        @lombok.Setter
        private BigDecimal contractMultiplier = BigDecimal.ONE;

        public PositionSnapshot(String folderId, String symbol) {
            this.folderId = folderId;
            this.symbol = symbol;
            this.positionId = folderId + "_" + symbol;
        }

        // --- 核心方法：加锁保证单个合约的更新绝对安全 ---

        public synchronized void freezeLong(BigDecimal qty, BigDecimal price) {
            this.frozenLongQty = this.frozenLongQty.add(qty);
            BigDecimal weight = qty.multiply(contractMultiplier);
            this.frozenLongWeight = this.frozenLongWeight.add(weight);
            if (price != null) {
                this.frozenLongAmount = this.frozenLongAmount.add(weight.multiply(price));
            }
        }

        public synchronized void freezeShort(BigDecimal qty, BigDecimal price) {
            this.frozenShortQty = this.frozenShortQty.add(qty);
            BigDecimal weight = qty.multiply(contractMultiplier);
            this.frozenShortWeight = this.frozenShortWeight.add(weight);
            if (price != null) {
                this.frozenShortAmount = this.frozenShortAmount.add(weight.multiply(price));
            }
        }

        public synchronized void unfreezeAndAddLong(BigDecimal filledQty, BigDecimal unfreezeQty, BigDecimal price) {
            // 按比例释放冻结金额与克重
            releaseFrozenLong(unfreezeQty);

            this.longQty = this.longQty.add(filledQty);
            // 计算克重 (量 * 乘数)
            BigDecimal weight = filledQty.multiply(contractMultiplier);
            this.longWeight = this.longWeight.add(weight);
            // 计算金额 (克重 * 成交价)
            if (price != null) {
                this.longAmount = this.longAmount.add(weight.multiply(price));
            }
        }

        public synchronized void unfreezeAndAddShort(BigDecimal filledQty, BigDecimal unfreezeQty, BigDecimal price) {
            releaseFrozenShort(unfreezeQty);

            this.shortQty = this.shortQty.add(filledQty);
            BigDecimal weight = filledQty.multiply(contractMultiplier);
            this.shortWeight = this.shortWeight.add(weight);
            if (price != null) {
                this.shortAmount = this.shortAmount.add(weight.multiply(price));
            }
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
                this.frozenShortWeight = this.frozenShortWeight.subtract(this.frozenShortWeight.multiply(ratio)).max(BigDecimal.ZERO);
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

}
