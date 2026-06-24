package com.cmbc.oms.infrastructure.dao;

import com.cmbc.oms.domain.entity.SeatInfoEntity;

import java.util.List;

public interface SeatInfoMapper {

    // 查询所有的合约信息
    public List<SeatInfoEntity> findList();

}
