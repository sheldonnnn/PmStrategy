package com.cmbc.oms.infrastructure.dao;

import com.cmbc.oms.domain.entity.ContractInfoEntity;

import java.util.List;

public interface ContractInfoMapper {

    // 查询所有的合约信息
    public List<ContractInfoEntity> getContractInfo();

}
