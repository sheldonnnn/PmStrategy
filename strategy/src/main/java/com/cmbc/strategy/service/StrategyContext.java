package com.cmbc.strategy.service;

import com.cmbc.strategy.integration.IMarketDataService;
import com.cmbc.strategy.integration.IPositionService;
import lombok.Data;
import org.springframework.scheduling.TaskScheduler;

@Data
public class StrategyContext {

    private TaskScheduler taskScheduler; // Spring提供的线程池调度器

    private IMarketDataService marketDataService;

    private OmsService omsService;

    private IPositionService positionService;

    private IGoldHedgeStrategyWebSocketService goldHedgeStrategyWebSocketService;

    private IGoldHedgeStrategyInstanceService goldHedgeStrategyInstanceService;

    private KsdStaticQuoteCacheService ksdStaticQuoteCacheService;

}
