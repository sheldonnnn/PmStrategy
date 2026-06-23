package com.cmbc.strategy.integration;

import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.oms.domain.exposure.model.HedgePositionSummary;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.strategy.domain.model.config.StrategyStatSummary;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.domain.model.hedge.GoldStrategyBean;

import java.util.List;
import java.util.Map;

/**
 * 策略监控webSocket服务发送
 */
public interface IHedgeStrategyWebSocketService {
    
    /**
     * 发送策略运行信息
     * @param 
     */
    public void sendGoldHedgeStrategyMap(String userName, String instanceId,
                                         Map<String, StrategyStatSummary> map,
                                         HedgePositionSummary position,
                                         SymbolTimeSlice activeTimeSlice,
                                         List<NewOrder> newOrderList,
                                         PloyPrices onshorePloyPrice, Integer chaseNumber);

    /**
     * 计算积存进实时数据
     * @param instanceId
     * @param map
     * @param position
     * @param activeTimeSlice
     */
    public GoldStrategyBean getGoldHedgeStrategyInstanceInfo(String userName,
                                                             String instanceId,
                                                             Map<String, StrategyStatSummary> map,
                                                             HedgePositionSummary position,
                                                             SymbolTimeSlice activeTimeSlice,
                                                             List<NewOrder> newOrderList,
                                                             PloyPrices onshorePloyPrice,
                                                             Integer chaseNumber);

    /**
     * 追单提醒警告
     */
    public void sendChasingRequest(String instanceId, String userName);

    /**
     * 追单超时提醒警告
     */
    public void sendChasingTimeOutWarning(String instanceId, String userName);

    /**
     * 发送策略运行状态变更信息
     * @param
     */
    public void sendGoldHedgeStrategyStatus(String userName, String instanceId, String status, String message);
}
