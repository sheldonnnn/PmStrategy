package com.cmbc.strategy.integration;

import com.cmbc.mds.distribution.PloyPrices;
import com.cmbc.oms.domain.exposure.dto.HedgePositionSummary;
import com.cmbc.oms.domain.order.model.NewOrder;
import com.cmbc.strategy.domain.model.StrategyStatSummary;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.domain.model.hedge.GoldStrategyBean;

import java.util.List;
import java.util.Map;

/**
 * 策略WebSocket服务接口
 */
public interface IHedgeStrategyWebSocketService {

    /**
     * 发送策略运行信息
     */
    void sendGoldHedgeStrategyMap(
            String userName,
            String instanceId,
            Map<String, StrategyStatSummary> map,
            HedgePositionSummary position,
            SymbolTimeSlice activeTimeSlice,
            List<NewOrder> newOrderList,
            PloyPrices onshorePloyPrice,
            Integer chaseNumber
    );

    /**
     * 计算积存金实时数据
     * * @param instanceId
     *
     * @param map
     * @param position
     * @param activeTimeSlice
     */
    GoldStrategyBean getGoldHedgeStrategyInstanceInfo(
            String userName,
            String instanceId,
            Map<String, StrategyStatSummary> map,
            HedgePositionSummary position,
            SymbolTimeSlice activeTimeSlice,
            List<NewOrder> newOrderList,
            PloyPrices onshorePloyPrice,
            Integer chaseNumber
    );

    /**
     * 追单提醒警告
     */
    void sendChasingRequest(String instanceId, String userName);

    /**
     * 追单超时提醒警告
     */
    void sendChasingTimeoutWarning(String instanceId, String userName);

    /**
     * 发送策略运行状态变更信息
     */
    void sendGoldHedgeStrategyStatus(String userName, String instanceId, String status, String message);
}
