package com.cmbc.oms.infrastructure.dao;

import com.cmbc.oms.domain.order.model.entity.PmOrderEntity;
import com.cmbc.oms.domain.order.model.entity.PmTradeEntity;

public interface PmTradeMapper {

    public PmOrderEntity selectByExecId(String execId);
    public int insertTrade(PmTradeEntity pmOrderEntity);

}
