package com.cmbc.oms.infrastructure.facadeimpl.apama.setting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: Cly
 * @Date: 2026/01/22  16:43
 * @Description:
 */
@Component
@Order(1)
public class OmsCorrelatorSetting {

    @Value("${apama.name}")
    private String name;
    @Value("${apama.host}")
    private String host;
    @Value("${apama.port}")
    private int port;
    
    private List backupCorrelators;
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

    public OmsCorrelatorSetting() {
    }

    public String getName() { return this.name; }

    public void setName(String name) { this.name = name; }

    public String getHost() { return this.host; }

    public void setHost(String host) { this.host = host; }

    public int getPort() { return this.port; }

    public void setPort(int port) { this.port = port; }

    public List getBackupCorrelators() { return this.backupCorrelators; }

    public void setBackupCorrelators(List backupCorrelators) {
        this.backupCorrelators = backupCorrelators;
    }

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
