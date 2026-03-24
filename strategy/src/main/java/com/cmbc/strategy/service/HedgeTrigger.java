package com.cmbc.strategy.service;

import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class HedgeTrigger {

    /**
     * 评估是否触发平盘
     * * @param clientPos   积存金客户净头寸 (来源: 积存金系统)
     * @param hedgedPos   已平盘净头寸 (来源: 本地数据库/缓存)
     * @param openExposure 挂单占用头寸 (来源: OMS/OrderService)
     * @param symbolTimeSlice      当前配置 (含阈值、合约、最大单量)
     */
    public boolean evaluate(BigDecimal clientPos,
                                  BigDecimal hedgedPos,
                                  BigDecimal openExposure,
                                  SymbolTimeSlice symbolTimeSlice) {

        // 1. [前置风控] 数据有效性检查
        if (!isDataValid(clientPos, hedgedPos)) return false;

        // 2. [核心公式] 计算当前实际净敞口
        // NetGap = Client - Hedged
        // 正数表示客户买多（银行欠货），负数表示客户卖空（银行多货）
        BigDecimal netGap = clientPos.subtract(hedgedPos).subtract(openExposure);

        // 3. [阈值判断]
        // netGap大于买入阈值或小于卖出阈值负数
        if (netGap.compareTo(symbolTimeSlice.getTriggerLongPosition()) > 0 || netGap.compareTo(symbolTimeSlice.getTriggerShortPosition().negate()) < 0) {
            return true;
        }

        return false; // 未触发
    }

    private boolean isDataValid(BigDecimal p1, BigDecimal p2) {
        // 防止上游系统故障传来 null 导致空指针
        return p1 != null && p2 != null;
    }

}
