package com.cmbc.strategy.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.cmbc.oms.util.concurrent.ShardingThreadPool;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class QuantExecutorConfig {

    // 积存金策略专属异步 IO 线程池 (慢车道)
    @Bean(name = "goldHedgeIoExecutor")
    public ThreadPoolTaskExecutor goldHedgeIoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("GoldHedge-IO-");
        
        // 关键安全配置：优雅停机，等待任务执行完毕再关机
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        
        // 满了就调用者(主线程)执行，绝不丢弃数据
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    // 积存金策略专属无锁撮合分片池 (快车道)
    @Bean(name = "goldHedgeEventPool")
    public ShardingThreadPool goldHedgeEventPool() {
        return new ShardingThreadPool(16, "GoldHedge-Event");
    }
}
