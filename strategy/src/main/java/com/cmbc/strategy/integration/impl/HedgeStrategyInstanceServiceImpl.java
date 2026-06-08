package com.cmbc.strategy.integration.impl;

import com.cmbc.strategy.dao.GoldHedgeStrategyInstanceMapper;
import com.cmbc.strategy.domain.entity.HedgeStrategyInstanceEntity;
import com.cmbc.strategy.integration.IHedgeStrategyInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 策略实例状态落库服务
 */
@Service
public class HedgeStrategyInstanceServiceImpl implements IHedgeStrategyInstanceService {

    @Autowired
    private GoldHedgeStrategyInstanceMapper goldHedgeStrategyInstanceMapper;

    @Override
    public void saveInstance(HedgeStrategyInstanceEntity hedgeStrategyInstanceEntity) {
        goldHedgeStrategyInstanceMapper.insert(hedgeStrategyInstanceEntity);
    }

    @Override
    public void updateStatus(String userName, String instanceId, String status, String message) {
        goldHedgeStrategyInstanceMapper.updateStatusByInstanceId(instanceId, status, message);
    }

    @Override
    public HedgeStrategyInstanceEntity getInstanceById(String instanceId) {
        return goldHedgeStrategyInstanceMapper.queryInfoByInstanceId(instanceId);
    }
}