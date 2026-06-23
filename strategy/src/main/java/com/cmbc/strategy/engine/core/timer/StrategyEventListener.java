package com.cmbc.strategy.engine.core.timer;

/**
 * 策略时间事件监听器
 */
public interface StrategyEventListener<T extends StrategyEvent> {
    
    /**
     * 当收到时间事件时触发
     * @param event 时间事件
     */
    void onTimeEvent(T event);
}
