package com.cmbc.oms.domain.order.ability.factory;

import com.cmbc.oms.domain.event.ReqTraderPosiAllQryEvent;
import com.cmbc.oms.domain.event.ReqTraderQryStorageEvent;
import com.cmbc.oms.infrastructure.util.UniqueIdGenerator;
import org.springframework.stereotype.Component;

/**
 * @author chendaqian
 * @date 2026/3/9
 * @time 16:05
 * @description
 */
@Component
public class ReqTraderPosiAllQryEventFactory {
    
    public ReqTraderPosiAllQryEvent createReqTraderPosiAllQryEvent(String traderNo, String typeHead) {
        ReqTraderPosiAllQryEvent event = new ReqTraderPosiAllQryEvent();
        event.setUniqueID(UniqueIdGenerator.generateUniqueId(typeHead));
        event.setTraderNo(traderNo);
        return event;
    }
    
    public ReqTraderQryStorageEvent createReqTraderQryStorageEvent(String traderNo, String typeHead)
    {
        ReqTraderQryStorageEvent event = new ReqTraderQryStorageEvent();
        event.setUniqueID(UniqueIdGenerator.generateUniqueId(typeHead));
        event.setTraderNo(traderNo);
        return event;
    }
}
