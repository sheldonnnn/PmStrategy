package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;

import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/3/11
 * @time 14:29
 * @description
 */
@EventField(name = "com.apama.oms.CancelOrder")
public class CancelOrderEvent {
    @EventField(name = "orderId", order = 1)
    private String orderId;
    @EventField(name = "serviceId", order = 2)
    private String serviceId;
    @EventField(name = "extraParams", order = 3)
    private Map<String, String> extraParams;

    public String getOrderId() { return orderId; }

    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getServiceId() { return serviceId; }

    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public Map<String, String> getExtraParams() { return extraParams; }

    public void setExtraParams(Map<String, String> extraParams) { this.extraParams = extraParams; }
}
