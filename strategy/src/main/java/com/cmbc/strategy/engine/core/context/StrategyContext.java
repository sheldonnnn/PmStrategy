package com.cmbc.strategy.engine.core.context;

import com.cmbc.oms.domain.exception.ExceptionNotificationService;
import com.cmbc.oms.facade.strategy.OmsService;
import com.cmbc.strategy.integration.IHedgeStrategyPersistService;
import com.cmbc.strategy.integration.IHedgeStrategyWebSocketService;
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

    private ExceptionNotificationService exceptionNotificationService;

    private IHedgeStrategyPersistService goldHedgeStrategyInstanceService;

    private IHedgeStrategyWebSocketService goldHedgeStrategyWebSocketService;

    private KsdStaticQuoteCacheService ksdStaticQuoteCacheService;

    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor goldHedgeIoExecutor;

}
