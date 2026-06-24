package com.cmbc.oms.infrastructure.dao;

import com.cmbc.oms.domain.order.model.entity.PmOrderEntity;

public interface PmOrderMapper {

    public PmOrderEntity selectByOrderId(String orderId);
    public int updateOrder(PmOrderEntity pmOrderEntity);
    // 管理台委托订单 insertOrUpdate
    public int insertOrUpdate(PmOrderEntity pmOrderEntity);
    public int insertNewOrder(PmOrderEntity pmOrderEntity);

}
