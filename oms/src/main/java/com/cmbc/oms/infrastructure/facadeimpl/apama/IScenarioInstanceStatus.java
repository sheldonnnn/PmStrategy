package com.cmbc.oms.infrastructure.facadeimpl.apama;

import java.beans.PropertyChangeEvent;

/**
 * @Author: Cly
 * @Date: 2026/01/23  11:36
 * @Description:
 */
public interface IScenarioInstanceStatus {
    void instanceAdded(PropertyChangeEvent var1);

    void instanceDied(PropertyChangeEvent var1);

    void instanceEdited(PropertyChangeEvent var1);

    void instanceUpdated(PropertyChangeEvent var1);

    void instanceRemoved(PropertyChangeEvent var1);

    void instanceStateChange(PropertyChangeEvent var1);

    void instanceStateDiscoveryStatus(PropertyChangeEvent var1);

    void instanceOther(PropertyChangeEvent var1);
}
