package com.cmbc.oms.infrastructure.facadeimpl.apama.listener;

import com.apama.services.scenario.IScenarioDefinition;
import com.apama.services.scenario.internal.ScenarioInstance;
import com.apama.services.scenario.internal.ScenarioService;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.ApamaConstant;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.beans.PropertyChangeEvent;

/**
 * @Author: Cly
 * @Date: 2026/01/22  17:13
 * @Description:
 */
@Component
public class AbstractReceiveModeListener {
    public AbstractReceiveModeListener() {
    }

    public boolean checkApamaDV(PropertyChangeEvent evt) {
        String dataViewName = evt.getNewValue().toString();
        if (evt.getNewValue() instanceof ScenarioInstance) {
            ScenarioInstance newValue = (ScenarioInstance)evt.getNewValue();
            IScenarioDefinition definition = newValue.getScenarioDefinition();
            dataViewName = definition.getDisplayName();
        }

        return ApamaConstant.DataViewIgnoreEnum.getNameSet().contains(dataViewName);
    }

    public boolean checkApamaInstance(PropertyChangeEvent evt, String channelKey) {
        if (evt.getSource() instanceof ScenarioInstance) {
            ScenarioService scenarioService = (ScenarioService)evt.getSource();
            String processName = scenarioService.getEventService().getEngineClient().getProcessName();
            String instanceName = "ScenarioInstance-" + channelKey;
            if (!StringUtils.isEmpty(processName) && !StringUtils.isEmpty(channelKey)) {
                if (processName.equals(instanceName)) {
                    return true;
                }

                return false;
            }
        }

        return true;
    }
}
