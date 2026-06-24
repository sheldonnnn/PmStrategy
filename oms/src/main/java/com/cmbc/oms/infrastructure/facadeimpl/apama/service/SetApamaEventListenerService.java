package com.cmbc.oms.infrastructure.facadeimpl.apama.service;

import com.alibaba.fastjson.JSONObject;
import com.apama.event.parser.EventType;
import com.cmbc.oms.app.service.OrderDownstreamAppService;
import com.cmbc.oms.domain.event.ExceptionNewOrderEvent;
import com.cmbc.oms.domain.event.OrderUpdateEvent;
import com.cmbc.oms.domain.facade.apama.SetApamaEventListener;
import com.cmbc.oms.infrastructure.facadeimpl.apama.ApamaCommonEvenetUtil;
import com.cmbc.oms.infrastructure.facadeimpl.apama.EventServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author chendaqian
 * @date 2026/2/4
 * @time 18:12
 * @description
 */
@Service
public class SetApamaEventListenerService implements SetApamaEventListener {

    private final Logger log = LoggerFactory.getLogger(SetApamaEventListenerService.class);

    @Autowired
    private EventServiceUtils eventServiceUtils;
    @Autowired
    private OrderDownstreamAppService orderDownstreamAppService;
    @Value("${oms.channel}")
    private String omsChannel;

    @Override
    public void orderUpdateEventListener(Class clazz) {
        EventType newOrderRsp = ApamaCommonEvenetUtil.getEventTypeByClass(clazz);
        log.info("AddEventListener channel:{},eventType:{}", omsChannel, newOrderRsp.getName());
        eventServiceUtils.addEventListener(omsChannel, newOrderRsp, event -> {
            OrderUpdateEvent orderUpdateEvent = (OrderUpdateEvent) ApamaCommonEvenetUtil.getObjectFromEvent(event, clazz);
            log.info("Receive orderUpdateEvent:{}", JSONObject.toJSONString(orderUpdateEvent));
            // 调用子单状态变更方法
            orderDownstreamAppService.handleOrderUpdate(orderUpdateEvent);
        });
    }

    @Override
    public void exceptionOrderEventListener(Class clazz) {
        String channel = omsChannel;
        EventType exceptionOrderRsp = ApamaCommonEvenetUtil.getEventTypeByClass(clazz);
        eventServiceUtils.addEventListener(channel, exceptionOrderRsp, event -> {
            ExceptionNewOrderEvent exceptionNewOrderEvent =
                    (ExceptionNewOrderEvent) ApamaCommonEvenetUtil.getObjectFromEvent(event, clazz);
            log.info("Receive exceptionNewOrderEvent:{}", JSONObject.toJSONString(exceptionNewOrderEvent));
            // 调用异常订单处理方法
            orderDownstreamAppService.handleExceptionReport(exceptionNewOrderEvent);
        });
    }
}
