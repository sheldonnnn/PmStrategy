package com.cmbc.strategy.configuration;

import com.cmbc.common.util.ShardingThreadPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorConfig {

    // 积存金策略专属网络/磁盘分片 IO 池 (慢车道，严格保序)
    @Bean(name = "goldHedgeIoPool")
    public ShardingThreadPool goldHedgeIoPool() {
        // 设置一定的核心分片数，处理网络推送等慢IO操作
        return new ShardingThreadPool(2, "GoldHedge-IO",300);
    }

    // 积存金策略专属统计事件分片池 (专门处理成交回报归集)
    @Bean(name = "goldHedgeStatPool")
    public ShardingThreadPool goldHedgeStatPool() {
        // 专门承接 OMS 回报事件等不阻断交易的统计动作
        return new ShardingThreadPool(2, "GoldHedge-Stat",100);
    }

    @Bean(name = "hedgeStrategyEventPool")
    public ShardingThreadPool hedgeStrategyEventPool() {
        return new ShardingThreadPool(4, "HEDGE-EVENT-", 200);
    }

    //平盘策略核心定时任务调度器（平盘执行、追单）
    @Bean(name = "strategyEngineTaskScheduler")
    public ThreadPoolTaskScheduler strategyEngineTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("HEDGE-STRATEGY-TIMER-");
        scheduler.initialize();
        return scheduler;
    }
}
