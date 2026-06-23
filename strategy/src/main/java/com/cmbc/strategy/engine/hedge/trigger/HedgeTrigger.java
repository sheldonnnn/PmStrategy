package com.cmbc.strategy.engine.hedge.trigger;

import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class HedgeTrigger {

    /**
     * 评估是否触发平盘
     * @param mgapClientPos   积存金客户净头寸
     * @param mgapHedgedPos   已平盘净头寸
     * @param symbolTimeSlice 当前配置（含阈值、合约、最大单量）
     */
    public boolean evaluate(BigDecimal mgapClientPos, 
                            BigDecimal mgapHedgedPos, 
                            BigDecimal hedgedPos, 
                            SymbolTimeSlice symbolTimeSlice) {
        
        // 1. [前置风控] 数据有效性检查
        if (!isDataValid(mgapClientPos, mgapHedgedPos)) return false;

        // 2. [核心公式] 计算当前实际净敞口
        // NetGap = Client - Hedged
        // 正数表示客户买多，负数表示客户卖空
        BigDecimal netGap = mgapClientPos.add(mgapHedgedPos).add(hedgedPos);

        // 3. [阈值判断]
        // netGap大于买入阈值或小于卖出阈值负数
        if (netGap.compareTo(BigDecimal.ZERO) > 0) {
            return netGap.compareTo(symbolTimeSlice.getTriggerLongPosition()) >= 0;
        }
        // Gap < 0 (多货/空头敞口)：需卖出平盘，直到 Abs(Gap) <= 空头终止线
        else {
            return netGap.abs().compareTo(symbolTimeSlice.getTriggerShortPosition().abs()) >= 0;
        }
    }

    private boolean isDataValid(BigDecimal p1, BigDecimal p2) {
        // 防止上游系统故障传来 null 导致空指针
        return p1 != null && p2 != null;
    }
}
