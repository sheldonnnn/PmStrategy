package com.cmbc.oms.dao;

import com.cmbc.oms.domain.exposure.entity.PositionBalanceHistoryEntity;

public interface PositionBalanceHistoryMapper {

    /**
     * 插入头寸历史记录
     */
    int insert(PositionBalanceHistoryEntity entity);
}
