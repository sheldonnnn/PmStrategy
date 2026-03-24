package com.cmbc.strategy.dao;

import com.cmbc.strategy.domain.entity.ContractInfoEntity;

import java.util.Map;

public interface ContractInfoDao {

    public Map<String,ContractInfoEntity> selectById();

}
