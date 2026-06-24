package com.cmbc.oms.infrastructure.facadeimpl.apama;

import com.apama.services.scenario.IScenarioService;
import com.apama.services.scenario.ScenarioServiceConfig;
import com.apama.services.scenario.ScenarioServiceFactory;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.ApamaConstant;
import com.cmbc.oms.infrastructure.facadeimpl.apama.listener.ScenarioServiceListener;
import com.cmbc.oms.infrastructure.facadeimpl.apama.setting.OmsCorrelatorSetting;
import com.cmbc.oms.infrastructure.facadeimpl.apama.setting.PmCorrelatorSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Cly
 * @Date: 2026/01/22  15:29
 * @Description:
 */
@Component
public class CorrelatorUtils {

    @Autowired
    private OmsCorrelatorSetting omsCorrelatorSetting;
    @Autowired
    private PmCorrelatorSetting pmCorrelatorSetting;

    private IScenarioService scenarioService;
    private IScenarioService pmScenarioService;
    private String connectedCorrelatorName;
    private String pmConnectedCorrelatorName;

    @Autowired
    private ScenarioServiceListener scenarioServiceListener;
    private AtomicBoolean isDestroyed = new AtomicBoolean(false);
    protected Logger logger = LoggerFactory.getLogger(CorrelatorUtils.class);

    public CorrelatorUtils() {
    }

    public void connectToOmsCorrelator(String correlatorName) {
        this.disconnectIfConnected();
        try {
            if (omsCorrelatorSetting != null && omsCorrelatorSetting.getName().equals(correlatorName)) {
                Map<String, Object> scenarioConfig = new HashMap();
                ScenarioServiceConfig.setAutoInstanceDiscovery(scenarioConfig, omsCorrelatorSetting.isAutoInstanceDiscovery());
                ScenarioServiceConfig.setDisconnectIfSlow(scenarioConfig, omsCorrelatorSetting.isDisconnectIfSlow());
                ScenarioServiceConfig.setStrongDataInboundEventQueue(scenarioConfig, omsCorrelatorSetting.isStrongDataInboundEventQueue());
                ScenarioServiceConfig.setReconnectPeriod(scenarioConfig, (long) omsCorrelatorSetting.getConnectTimeOutPeriod());
                ScenarioServiceConfig.setAckDataTimeout(scenarioConfig, 3000L);
                ScenarioServiceConfig.setScenarioExclusionFilter(scenarioConfig, this.scenarioExclusionFilter());
                this.logger.info("Attempting connection to " + omsCorrelatorSetting.getHost() + " on " + omsCorrelatorSetting.getPort());
                this.scenarioService = ScenarioServiceFactory.createScenarioService(omsCorrelatorSetting.getHost(),
                        omsCorrelatorSetting.getPort(), correlatorName, scenarioConfig, this.scenarioServiceListener);

                this.logger.info("Connected to " + omsCorrelatorSetting.getHost() + " on " + omsCorrelatorSetting.getPort());
                this.connectedCorrelatorName = correlatorName;
                this.isDestroyed.getAndSet(false);
            }
        } catch (Exception e) {
            logger.error("Connected to {} on {} failed!!", omsCorrelatorSetting.getHost(), omsCorrelatorSetting.getPort(), e);
        }
    }

    public void connectToPmCorrelator(String correlatorName) {
        this.disconnectIfConnected();
        try {
            Map<String, Object> scenarioConfig = new HashMap();
            ScenarioServiceConfig.setAutoInstanceDiscovery(scenarioConfig, pmCorrelatorSetting.isAutoInstanceDiscovery());
            ScenarioServiceConfig.setDisconnectIfSlow(scenarioConfig, pmCorrelatorSetting.isDisconnectIfSlow());
            ScenarioServiceConfig.setStrongDataInboundEventQueue(scenarioConfig, pmCorrelatorSetting.isStrongDataInboundEventQueue());
            ScenarioServiceConfig.setReconnectPeriod(scenarioConfig, (long) pmCorrelatorSetting.getConnectTimeOutPeriod());
            ScenarioServiceConfig.setAckDataTimeout(scenarioConfig, 3000L);
            ScenarioServiceConfig.setScenarioExclusionFilter(scenarioConfig, this.scenarioExclusionFilter());
            this.logger.info("Attempting connection to " + pmCorrelatorSetting.getHost() + " on " + pmCorrelatorSetting.getPort());
            this.pmScenarioService = ScenarioServiceFactory.createScenarioService(pmCorrelatorSetting.getHost(),
                    pmCorrelatorSetting.getPort(), correlatorName, scenarioConfig, this.scenarioServiceListener);

            this.logger.info("Connected to " + pmCorrelatorSetting.getHost() + " on " + pmCorrelatorSetting.getPort());
            this.pmConnectedCorrelatorName = correlatorName;
            this.isDestroyed.getAndSet(false);
        } catch (Exception e) {
            logger.error("Connected to {} on {} failed!!", omsCorrelatorSetting.getHost(), omsCorrelatorSetting.getPort(), e);
        }
    }

    private Set<String> scenarioExclusionFilter() {
        Set<String> scenarioExclusionFilter = ApamaConstant.DataViewIgnoreEnum.getDvNameSet();
        return scenarioExclusionFilter;
    }

    public void disconnectIfConnected() {
        this.logger.info("Disposing of current scenario service");
        if (this.scenarioServiceListener != null) {
            try {
                this.scenarioServiceListener.destroyed();
            } catch (Exception var8) {
                this.logger.error("关闭APAMA连接监听失败");
            }

            this.logger.info("Completed dispose of the ScenarioServiceListener");
        }

        if (this.scenarioService != null) {
            try {
                this.scenarioService.destroy();
            } catch (Exception var6) {
                this.logger.error("关闭scenarioService失败");
            } finally {
                this.scenarioService = null;
            }

            this.logger.info("Completed dispose of the ScenarioService");
        }

        if (this.pmScenarioService != null) {
            try {
                this.pmScenarioService.destroy();
            } catch (Exception var6) {
                this.logger.error("关闭scenarioService失败");
            } finally {
                this.pmScenarioService = null;
            }

            this.logger.info("Completed dispose of the PmScenarioService");
        }

        this.isDestroyed.getAndSet(true);
    }

    public IScenarioService getScenarioService() {
        if (this.scenarioService == null) {
            this.connectToOmsCorrelator("PmStrategy");
        }

        return this.scenarioService;
    }

    public IScenarioService getPmScenarioService() {
        if (this.pmScenarioService == null) {
            this.connectToPmCorrelator("PmStrategy");
        }

        return this.pmScenarioService;
    }

    public void destoryed() {
        this.logger.info("CorrelatorUtils is destoryed");
        if (!this.isDestroyed.get()) {
            this.disconnectIfConnected();
        }

    }
}
