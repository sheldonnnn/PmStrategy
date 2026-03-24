package com.cmbc.strategy.integration;

import com.cmbc.strategy.domain.model.OrderRequest;

public interface IOrderService {

    public String newOrder(OrderRequest req);      // 发单
    public void orderCancel(String orderId);        // 撤单

}
