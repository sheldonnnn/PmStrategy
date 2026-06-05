package com.cmbc.strategy.integration.impl;

import com.cmbc.strategy.dao.GoldHedgeStrategyInstanceMapper;
import com.cmbc.strategy.domain.entity.HedgeStrategyInstanceEntity;
import com.cmbc.strategy.integration.IHedgeStrategyPersistService;
import com.cmbc.strategy.integration.IHedgeStrategyWebSocketService; // 假设在同一或相邻包下
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 策略信息及策略汇总信息通过websocket发送机制
 * @Date: 2026/03/02 16:15
 * @Description:
 */
@Service
public class HedgeStrategyPersistService implements IHedgeStrategyPersistService {

    @Autowired
    private GoldHedgeStrategyInstanceMapper goldHedgeStrategyInstanceMapper;

    @Autowired
    private IHedgeStrategyWebSocketService goldHedgeStrategyWebSocketService;

    @Override
    public void insertStrategyInstanceStatus(HedgeStrategyInstanceEntity hedgeStrategyInstanceEntity) {
        goldHedgeStrategyInstanceMapper.insert(hedgeStrategyInstanceEntity);
        goldHedgeStrategyWebSocketService.sendGoldHedgeStrategyStatus(
                hedgeStrategyInstanceEntity.getCreateBy(),
                hedgeStrategyInstanceEntity.getInstanceId(),
                hedgeStrategyInstanceEntity.getStatus(),
                null
        );
    }

    @Override
    public void updateStrategyInstanceStatus(String userName, String instanceId, String status, String message) {
        goldHedgeStrategyInstanceMapper.updateStatusByInstanceId(instanceId, status, message);
        goldHedgeStrategyWebSocketService.sendGoldHedgeStrategyStatus(userName, instanceId, status, message);
    }

    @Override
    public HedgeStrategyInstanceEntity queryInfoByInstanceId(String instanceId) {
        return goldHedgeStrategyInstanceMapper.queryInfoByInstanceId(instanceId);
    }
}