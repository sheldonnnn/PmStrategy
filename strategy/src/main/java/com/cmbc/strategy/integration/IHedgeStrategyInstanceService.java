package com.cmbc.strategy.integration;

import com.cmbc.strategy.domain.entity.HedgeStrategyInstanceEntity;

/**
 * 积存金平盘实例服务
 */
public interface IHedgeStrategyInstanceService {

    /**
     * 创建策略实例
     */
    void saveInstance(HedgeStrategyInstanceEntity hedgeStrategyInstanceEntity);

    /**
     * 修改策略实例状态
     */
    void updateStatus(String userName, String instanceId, String status, String message);

    /**
     * 根据策略实例ID查询实例数据
     */
    HedgeStrategyInstanceEntity getInstanceById(String instanceId);
}
