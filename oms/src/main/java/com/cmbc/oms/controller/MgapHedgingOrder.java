package com.cmbc.oms.controller;

import com.cmbc.oms.app.service.OrderUpstreamAppService;
import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.controller.dto.StrategyOrderRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author chendaqian
 * @date 2026/2/5
 * @time 10:29
 * @description 积存金平盘策略交互function
 */
@Component
public class MgapHedgingOrder {

    @Autowired
    private OrderUpstreamAppService orderUpstreamAppService;

    //接收积存金平盘策略订单
    public StrategyOrderRes newOrder(StrategyOrder strategyOrderReq) {
        try {
            return orderUpstreamAppService.handleNewOrder(strategyOrderReq);
        }catch (Exception e){
            return StrategyOrderRes.fail(e.getMessage());
        }
    }
}
