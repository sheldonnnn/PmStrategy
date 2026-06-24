package com.cmbc.oms.infrastructure.dao;

import com.cmbc.oms.domain.entity.ContractOutInfoEntity;

import java.util.List;

public interface ContractOutInfoMapper {

    // 查询所有的境外合约信息
    public List<ContractOutInfoEntity> getContractOutInfo();

}
