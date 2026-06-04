package com.cmbc.strategy.dao;

import com.cmbc.strategy.domain.entity.HedgeStrategyInstanceEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 策略运行实例
 * * @Title: ControlRuleFieldInfoCmMapper
 * @Description:
 * @Author: cuijian
 * @Date: 2026/10/18 15:34
 */
public interface GoldHedgeStrategyInstanceMapper {

    // 策略实例新增
    void insert(HedgeStrategyInstanceEntity entity);

    // 根据策略实例ID修改状态
    void updateStatusByInstanceId(
            @Param(value = "instanceId") String instanceId,
            @Param(value = "status") String status,
            @Param(value = "reason") String reason
    );

    // 根据条件查询实例数据
    List<HedgeStrategyInstanceEntity> queryInstance(HedgeStrategyInstanceEntity entity);

    // 根据策略实例ID查询实例数据
    HedgeStrategyInstanceEntity queryInfoByInstanceId(@Param(value = "instanceId") String instanceId);
}