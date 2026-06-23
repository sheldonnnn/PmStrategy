package com.cmbc.strategy.integration;

import com.cmbc.strategy.domain.entity.HedgeStrategyInstanceEntity;

/**
 * 积存金平盘实例服务
 */
public interface IHedgeStrategyPersistService {
    public void insertStrategyInstanceStatus(HedgeStrategyInstanceEntity hedgeStrategyInstanceEntity); // 创建策略实例
    
    public void updateStrategyInstanceStatus(String userName, String instanceId, String status, String message); // 修改策略实例状态
    public void updateInstanceSnapshot(HedgeStrategyInstanceEntity entity);
    // 根据策略实例ID查询实例数据
    HedgeStrategyInstanceEntity queryInfoByInstanceId(String instanceId);
}
