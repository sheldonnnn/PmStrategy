package com.cmbc.oms.dao;

import com.cmbc.oms.domain.exposure.entity.PositionBalanceEntity;

import java.util.List;

public interface PositionBalanceMapper {

    List<PositionBalanceEntity> getAllPositionBalance();

    /**
     * 插入或更新头寸余额
     */
    int saveOrUpdate(PositionBalanceEntity entity);
}
