package com.cmbc.oms.dao;

import com.cmbc.oms.domain.exposure.entity.MgapPositionBalanceEntity;

public interface MgapPositionBalanceMapper {

    /**
     * 插入外部积存金头寸历史记录
     */
    int insert(MgapPositionBalanceEntity entity);
}
