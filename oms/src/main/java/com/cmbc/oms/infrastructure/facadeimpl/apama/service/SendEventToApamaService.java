package com.cmbc.oms.infrastructure.facadeimpl.apama.service;

import com.apama.event.Event;
import com.cmbc.oms.domain.facade.apama.SendEventToApama;
import com.cmbc.oms.infrastructure.facadeimpl.apama.ApamaCommonEvenetUtil;
import com.cmbc.oms.infrastructure.facadeimpl.apama.EventServiceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author chendaqian
 * @date 2026/2/4
 * @time 18:12
 * @description 发送事件到Apama服务
 */
@Service
public class SendEventToApamaService implements SendEventToApama {
    @Autowired
    private EventServiceUtils eventServiceUtils;

    @Override
    public void sendEventToApama(Object event) {
        Event newEvent = ApamaCommonEvenetUtil.getEventByObject(event);
        eventServiceUtils.sendEvent(this, newEvent);
    }

    public void sendEventToPm(Object event) {
        Event sendEvent = ApamaCommonEvenetUtil.getEventByObject(event);
        eventServiceUtils.sendEventToPm(this, sendEvent);
    }
}
