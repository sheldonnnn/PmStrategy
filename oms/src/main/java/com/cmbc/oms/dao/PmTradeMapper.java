package com.cmbc.oms.dao;

import com.cmbc.oms.domain.entity.PmTrade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PmTradeMapper {
    // 插入成交回报流水
    int insertTrade(PmTrade trade);

    // 通过订单号查询该订单的所有成交流水 (对账/算均价的核心)
    List<PmTrade> selectTradesByOrderId(@Param("orderId") String orderId);
}
