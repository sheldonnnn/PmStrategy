package com.cmbc.oms.infrastructure.facadeimpl.apama.setting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @Author: Cly
 * @Date: 2026/01/22  16:43
 * @Description:
 */
@Component
@Order(2)
public class PmCorrelatorSetting {

    @Value("${apama.name}")
    private String name;

    @Value("${apama.host}")
    private String host;

    @Value("${apama.pm_port}")
    private int port;

    @Value("${apama.connectTimeOutPeriod}")
    private int connectTimeOutPeriod;

    @Value("${apama.retryCount}")
    private int retryCount;

    @Value("${apama.disconnectIfSlow}")
    private boolean disconnectIfSlow;

    @Value("${apama.autoInstanceDiscovery}")
    private boolean autoInstanceDiscovery;

    @Value("${apama.strongDataInboundEventQueue}")
    private boolean strongDataInboundEventQueue;

    @Value("${apama.isUsing}")
    private boolean isUsingApama;

    public PmCorrelatorSetting() {
    }

    public String getName() { return this.name; }

    public void setName(String name) { this.name = name; }

    public String getHost() { return this.host; }

    public void setHost(String host) { this.host = host; }

    public int getPort() { return this.port; }

    public void setPort(int port) { this.port = port; }

    public int getConnectTimeOutPeriod() { return this.connectTimeOutPeriod; }

    public void setConnectTimeOutPeriod(int connectTimeOutPeriod) {
        this.connectTimeOutPeriod = connectTimeOutPeriod;
    }

    public int getRetryCount() { return this.retryCount; }

    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public boolean isDisconnectIfSlow() { return this.disconnectIfSlow; }

    public void setDisconnectIfSlow(boolean disconnectIfSlow) {
        this.disconnectIfSlow = disconnectIfSlow;
    }

    public boolean isAutoInstanceDiscovery() { return this.autoInstanceDiscovery; }

    public void setAutoInstanceDiscovery(boolean autoInstanceDiscovery) {
        this.autoInstanceDiscovery = autoInstanceDiscovery;
    }

    public boolean isStrongDataInboundEventQueue() { return this.strongDataInboundEventQueue; }

    public void setStrongDataInboundEventQueue(boolean strongDataInboundEventQueue) {
        this.strongDataInboundEventQueue = strongDataInboundEventQueue;
    }

    public boolean isUsingApama() { return this.isUsingApama; }

    public void setUsingApama(boolean usingApama) { this.isUsingApama = usingApama; }
}
