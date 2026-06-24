package com.cmbc.oms.domain.facade.apama;

/**
 * @author chendaqian
 * @date 2026/2/4
 * @time 18:06
 * @description
 */
public interface SendEventToApama {
    
    // 发送新订单事件到Apama
//    public void sendNewOrderToApama(NewOrderEvent event);

    public void sendEventToApama(Object event);
    public void sendEventToPm(Object event);
}
