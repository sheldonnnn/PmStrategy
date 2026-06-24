package com.cmbc.oms.infrastructure.facadeimpl.apama.service;

import com.apama.event.parser.EventType;
import com.cmbc.oms.domain.event.RspTraderPosiAllQryEvent;
import com.cmbc.oms.domain.event.RspTraderQryStorageEvent;
import com.cmbc.oms.domain.facade.apama.SetApamaPositionEventListener;
import com.cmbc.oms.domain.position.ability.ApamaResponseHandlerService;
import com.cmbc.oms.infrastructure.facadeimpl.apama.ApamaCommonEvenetUtil;
import com.cmbc.oms.infrastructure.facadeimpl.apama.EventServiceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SetApamaPositionEventListenerService implements SetApamaPositionEventListener {

    @Autowired
    private EventServiceUtils eventServiceUtils;

    @Autowired
    private ApamaResponseHandlerService apamaResponseHandlerService;

    @Value("${oms.channel}")
    private String omsChannel;

    @Override
    public void setRspTraderQryStorageEventListener() {
        String channel = omsChannel;
        Class clazz = RspTraderQryStorageEvent.class;
        EventType newOrderRsp = ApamaCommonEvenetUtil.getEventTypeByClass(clazz);
        eventServiceUtils.addEventListener(channel, newOrderRsp, event -> {
            RspTraderQryStorageEvent storageEvent =
                    (RspTraderQryStorageEvent) ApamaCommonEvenetUtil.getObjectFromEvent(event, clazz);
            //log.info("Receive RspTraderQryStorageEvent:{}", event.toString());
            // 调用Apama响应处理服务，避免直接依赖PositionManageService
            apamaResponseHandlerService.handleSpotPositionResponse(storageEvent);
        });
    }

    @Override
    public void setRspTraderPosiAllQryEventListener() {
        String channel = omsChannel;
        Class clazz = RspTraderPosiAllQryEvent.class;
        EventType newOrderRsp = ApamaCommonEvenetUtil.getEventTypeByClass(clazz);
        eventServiceUtils.addEventListener(channel, newOrderRsp, event -> {
            RspTraderPosiAllQryEvent qryPositionEvent =
                    (RspTraderPosiAllQryEvent) ApamaCommonEvenetUtil.getObjectFromEvent(event, clazz);
            //log.info("Receive RspTraderPosiAllQryEvent:{}", JSONObject.toJSONString(event));
            // 调用Apama响应处理服务，避免直接依赖PositionManageService
            apamaResponseHandlerService.handleContractPositionResponse(qryPositionEvent);
        });
    }
}
