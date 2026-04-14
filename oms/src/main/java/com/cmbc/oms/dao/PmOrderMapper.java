package com.cmbc.oms.dao;

import com.cmbc.oms.domain.entity.PmOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PmOrderMapper {
    // 极速插入新订单
    int insertOrder(PmOrder order);

    // 根据内部单号查询
    PmOrder selectByOrderId(@Param("orderId") String orderId);

    // 动态更新订单状态及聚合数据 (成交数量、剩余数量等)
    int updateOrderSelective(PmOrder order);
}