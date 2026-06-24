package com.cmbc.oms.infrastructure.dao;

import com.cmbc.oms.domain.entity.DimpleUserEntity;

import java.util.List;

public interface DimpleUserMapper {

    // 查询所有的合约信息
    public List<DimpleUserEntity> getLogoinDimpleUser();

}
