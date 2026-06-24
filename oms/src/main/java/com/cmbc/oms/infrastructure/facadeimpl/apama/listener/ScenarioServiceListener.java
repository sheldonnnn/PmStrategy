package com.cmbc.oms.infrastructure.facadeimpl.apama.listener;

import com.apama.services.scenario.IScenarioDefinition;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: Cly
 * @Date: 2026/01/22  16:47
 * @Description:
 */
@Component
public class ScenarioServiceListener extends AbstractReceiveModeListener implements PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ScenarioServiceListener.class);

    @Resource
    ScenarioDefinitionListener scenarioDefinitionListener;
    Set<IScenarioDefinition> list = new HashSet<>();

    public ScenarioServiceListener() {
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (!this.checkApamaDV(evt)) {
            IScenarioDefinition definition;
            if ("com.apama.services.scenario.IScenarioService.ScenarioAdded".equals(evt.getPropertyName())) {
                // this.scenarioStatus.scenarioAdded(evt);
                try {
                    definition = (IScenarioDefinition) evt.getNewValue();
                    String displayName = definition.getDisplayName();
                    if (!this.list.isEmpty()) {
                        List<IScenarioDefinition> oldList = (List) this.list.stream().filter((d) -> {
                            return d.getDisplayName().equals(displayName);
                        }).collect(Collectors.toList());
                        if (!oldList.isEmpty()) {
                            Iterator var5 = oldList.iterator();

                            while (var5.hasNext()) {
                                IScenarioDefinition old = (IScenarioDefinition) var5.next();
                                old.removeListener(this.scenarioDefinitionListener);
                                this.list.remove(old);
                            }
                        }
                    }

                    this.list.add(definition);
                    definition.addListener(this.scenarioDefinitionListener);
                } catch (Exception var10) {
                }
            } else if ("com.apama.services.scenario.IScenarioService.ScenarioDiscoveryStatus".equals(evt.getPropertyName())) {
                // this.scenarioStatus.scenarioDiscoveryStatus(evt);
                try {
                    definition = (IScenarioDefinition) evt.getNewValue();
                    definition.removeListener(this.scenarioDefinitionListener);
                } catch (Exception var9) {
                }
            } else if ("com.apama.services.scenario.IScenarioService.ScenarioRemoved".equals(evt.getPropertyName())) {
                // this.scenarioStatus.scenarioRemoved(evt);
                try {
                    definition = (IScenarioDefinition) evt.getNewValue();
                    definition.removeListener(this.scenarioDefinitionListener);
                } catch (Exception var8) {
                }
            } else if ("com.apama.services.scenario.IScenarioService.ScenarioServiceUnloaded".equals(evt.getPropertyName())) {
                // this.scenarioStatus.scenarioServiceUnloaded(evt);
                try {
                    definition = (IScenarioDefinition) evt.getNewValue();
                    definition.removeListener(this.scenarioDefinitionListener);
                } catch (Exception var7) {
                }
            }
        }
    }
}
