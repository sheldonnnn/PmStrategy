package com.cmbc.strategy.configuration;

import com.cmbc.common.util.ShardingThreadPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorConfig {

    // 策略异步 IO 线程池
    @Bean(name = "hedgeIoExecutor")
    public ThreadPoolTaskExecutor hedgeIoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("HEDGE-IO-Pool-");

        // 关键安全配置：优雅停机，等待任务执行完毕再关机
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);

        // 满了就调用者(主线程)执行，绝不丢弃数据
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "hedgeStrategyEventPool")
    public ShardingThreadPool hedgeStrategyEventPool() {
        return new ShardingThreadPool(4, "HEDGE-EVENT-", 1000);
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
