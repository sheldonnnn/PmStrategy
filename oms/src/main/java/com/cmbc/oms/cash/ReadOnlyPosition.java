package com.cmbc.oms.cash;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class ReadOnlyPosition {
    
        private final String folderId;
        private final String symbol;

        private final BigDecimal longQty;
        private final BigDecimal shortQty;
        private final BigDecimal frozenLongQty;
        private final BigDecimal frozenShortQty;

        // 扩展增加的克重与金额
        private final BigDecimal longWeight;
        private final BigDecimal shortWeight;
        private final BigDecimal longAmount;
        private final BigDecimal shortAmount;

        // 新增冻结重量、冻结金额
        private final BigDecimal frozenLongWeight;
        private final BigDecimal frozenShortWeight;
        private final BigDecimal frozenLongAmount;
        private final BigDecimal frozenShortAmount;

        // 新增平均价、净平均价 (作为字段存在，而非每次方法调用时实时除法)
        private final BigDecimal averageLongPrice;
        private final BigDecimal averageShortPrice;
        private final BigDecimal netAveragePrice;

        public ReadOnlyPosition(String folderId, String symbol,
                                BigDecimal longQty, BigDecimal shortQty,
                                BigDecimal frozenLongQty, BigDecimal frozenShortQty,
                                BigDecimal longWeight, BigDecimal shortWeight,
                                BigDecimal longAmount, BigDecimal shortAmount,
                                BigDecimal frozenLongWeight, BigDecimal frozenShortWeight,
                                BigDecimal frozenLongAmount, BigDecimal frozenShortAmount) {
            this.folderId = folderId;
            this.symbol = symbol;
            this.longQty = longQty;
            this.shortQty = shortQty;
            this.frozenLongQty = frozenLongQty;
            this.frozenShortQty = frozenShortQty;
            this.longWeight = longWeight;
            this.shortWeight = shortWeight;
            this.longAmount = longAmount;
            this.shortAmount = shortAmount;
            this.frozenLongWeight = frozenLongWeight;
            this.frozenShortWeight = frozenShortWeight;
            this.frozenLongAmount = frozenLongAmount;
            this.frozenShortAmount = frozenShortAmount;

            // 构造时初始化多头平均价
            if (longWeight != null && longWeight.compareTo(BigDecimal.ZERO) != 0) {
                this.averageLongPrice = longAmount.divide(longWeight, 4, RoundingMode.HALF_UP);
            } else {
                this.averageLongPrice = BigDecimal.ZERO;
            }

            // 构造时初始化空头平均价
            if (shortWeight != null && shortWeight.compareTo(BigDecimal.ZERO) != 0) {
                this.averageShortPrice = shortAmount.divide(shortWeight, 4, RoundingMode.HALF_UP);
            } else {
                this.averageShortPrice = BigDecimal.ZERO;
            }

            // 构造时初始化净平均价
            BigDecimal netWeight = (longWeight == null ? BigDecimal.ZERO : longWeight)
                    .subtract(shortWeight == null ? BigDecimal.ZERO : shortWeight);
            BigDecimal netAmount = (longAmount == null ? BigDecimal.ZERO : longAmount)
                    .subtract(shortAmount == null ? BigDecimal.ZERO : shortAmount);
            if (netWeight.compareTo(BigDecimal.ZERO) != 0) {
                this.netAveragePrice = netAmount.divide(netWeight, 4, RoundingMode.HALF_UP);
            } else {
                this.netAveragePrice = BigDecimal.ZERO;
            }
        }

        /**
         * 计算当前净物理敞口量 (多头 - 空头)
         */
        public BigDecimal getNetQty() {
            if (longQty == null || shortQty == null) return BigDecimal.ZERO;
            return longQty.subtract(shortQty);
        }

        /**
         * 计算净克重 (多头克重 - 空头克重)
         */
        public BigDecimal getNetWeight() {
            if (longWeight == null || shortWeight == null) return BigDecimal.ZERO;
            return longWeight.subtract(shortWeight);
        }

        /**
         * 计算净金额 (多头金额 - 空头金额)
         */
        public BigDecimal getNetAmount() {
            if (longAmount == null || shortAmount == null) return BigDecimal.ZERO;
            return longAmount.subtract(shortAmount);
        }

        /**
         * 计算包含在途挂单的预期敞口量 (用于事前风控防超卖超买)
         */
        public BigDecimal getExpectedNetQty() {
            BigDecimal expectedLong = longQty.add(frozenLongQty);
            BigDecimal expectedShort = shortQty.add(frozenShortQty);
            return expectedLong.subtract(expectedShort);
        }
}
