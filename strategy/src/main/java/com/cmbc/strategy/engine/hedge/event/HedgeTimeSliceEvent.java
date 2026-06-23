package com.cmbc.strategy.engine.hedge.event;

import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.engine.core.timer.StrategyEvent;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 平盘策略专用时间切片事件
 */
@Data
@AllArgsConstructor
public class HedgeTimeSliceEvent implements StrategyEvent {
    
    public enum EventType {
        /**
         * 切片开始（允许交易）
         */
        START,
        
        /**
         * 切片盘前缓冲期（如闭市前15秒，触发撤单拒单）
         */
        PRE_CLOSE
    }

    private String instanceId;
    
    private SymbolTimeSlice slice;
    
    private EventType type;
}
