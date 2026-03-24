package com.cmbc.strategy.dao;

import com.cmbc.strategy.domain.entity.StrategyBaseEntity;

public interface StrategyConfigDao {

    public StrategyBaseEntity selectById(String configId);
}
