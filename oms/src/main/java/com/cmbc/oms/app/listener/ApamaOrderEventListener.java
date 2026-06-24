package com.cmbc.oms.app.listener;

import com.cmbc.oms.domain.event.ExceptionNewOrderEvent;
import com.cmbc.oms.domain.event.OrderUpdateEvent;
import com.cmbc.oms.domain.facade.apama.SetApamaEventListener;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author chendaqian
 * @date 2026/3/4
 * @time 18:34
 * @description apana下行订单更新事件监听
 */
@Service
public class ApamaOrderEventListener {

    @Autowired
    private SetApamaEventListener setApamaEventListener;
    
    @PostConstruct
    public void onAllOrderUpdateEvent() {
        // 设置订单更新事件监听
        setApamaEventListener.orderUpdateEventListener(OrderUpdateEvent.class);
        // 设置异常订单事件监听
        setApamaEventListener.exceptionOrderEventListener(ExceptionNewOrderEvent.class);
    }
}
