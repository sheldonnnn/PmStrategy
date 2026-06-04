package com.cmbc.strategy.integration;

import com.cmbc.strategy.domain.entity.HedgeStrategyInstanceEntity;

/**
 * 积存金平盘实例服务
 */
public interface IHedgeStrategyPersistService {

    /**
     * 创建策略实例
     */
    void insertStrategyInstanceStatus(HedgeStrategyInstanceEntity hedgeStrategyInstanceEntity);

    /**
     * 修改策略实例状态
     */
    void updateStrategyInstanceStatus(String userName, String instanceId, String status, String message);

    /**
     * 根据策略实例ID查询实例数据
     */
    HedgeStrategyInstanceEntity queryInfoByInstanceId(String instanceId);
}
