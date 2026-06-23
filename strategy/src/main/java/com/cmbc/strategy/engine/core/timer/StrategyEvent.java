package com.cmbc.strategy.engine.core.timer;

/**
 * 策略时间事件的通用基类
 */
public interface StrategyEvent {
    /**
     * 获取事件所属的策略实例ID
     * @return 实例ID
     */
    String getInstanceId();
}
