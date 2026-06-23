package com.cmbc.strategy.engine.context;

import com.cmbc.mds.ksd.cache.KsdStaticQuoteCacheService;
import com.cmbc.oms.domain.exception.ExceptionNotificationService;
import com.cmbc.oms.infrastructure.facadeimpl.strategy.OmsService;
import com.cmbc.strategy.integration.*;
import lombok.Data;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @Author: Cly
 * @Date: 2026/02/28 19:46
 * @Description:
 */
@Data
public class StrategyContext {

    private TaskScheduler taskScheduler; // Spring提供的线程池调度器

    private IMarketDataService marketDataService;

    private OmsService omsService;

    private IPositionService positionService;

    private ExceptionNotificationService exceptionNotificationService;

    private IHedgeStrategyInstanceService hedgeStrategyInstanceService;

    private IHedgeStrategyPushService hedgeStrategyPushService;

    private KsdStaticQuoteCacheService ksdStaticQuoteCacheService;

    private com.cmbc.common.util.concurrent.ShardingThreadPool goldHedgeIoPool;

    private com.cmbc.common.util.concurrent.ShardingThreadPool goldHedgeEventPool;

    private com.cmbc.strategy.engine.core.timer.StrategyTimerService strategyTimerService;

}
