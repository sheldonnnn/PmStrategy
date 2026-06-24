package com.cmbc.oms.infrastructure.facadeimpl.apama.listener;

import com.cmbc.oms.infrastructure.facadeimpl.apama.IScenarioInstanceStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @Author: Cly
 * @Date: 2026/01/23  11:32
 * @Description:
 */
@Component
public class ScenarioDefinitionListener extends AbstractReceiveModeListener implements PropertyChangeListener {
    @Autowired
    private IScenarioInstanceStatus scenarioInstanceStatus;

    public ScenarioDefinitionListener() {
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (!this.checkApamaDV(evt)) {
            if ("com.apama.services.scenario.IScenarioDefinition.InstanceAdded".equals(evt.getPropertyName())) {
                this.scenarioInstanceStatus.instanceAdded(evt);
            } else if ("com.apama.services.scenario.IScenarioDefinition.InstanceDied".equals(evt.getPropertyName())) {
                this.scenarioInstanceStatus.instanceDied(evt);
            } else if ("com.apama.services.scenario.IScenarioDefinition.InstanceDiscoveryStatus".equals(evt.getPropertyName())) {
                this.scenarioInstanceStatus.instanceStateDiscoveryStatus(evt);
            } else if ("com.apama.services.scenario.IScenarioDefinition.InstanceRemoved".equals(evt.getPropertyName())) {
                this.scenarioInstanceStatus.instanceRemoved(evt);
            } else if ("com.apama.services.scenario.IScenarioDefinition.InstanceEdited".equals(evt.getPropertyName())) {
                this.scenarioInstanceStatus.instanceEdited(evt);
            } else if ("com.apama.services.scenario.IScenarioDefinition.InstanceStateChanged".equals(evt.getPropertyName())) {
                this.scenarioInstanceStatus.instanceStateChange(evt);
            } else if ("com.apama.services.scenario.IScenarioDefinition.InstanceUpdated".equals(evt.getPropertyName())) {
                this.scenarioInstanceStatus.instanceUpdated(evt);
            } else {
                this.scenarioInstanceStatus.instanceOther(evt);
            }
        }
    }
}
