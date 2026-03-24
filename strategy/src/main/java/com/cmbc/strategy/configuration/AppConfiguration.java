package com.cmbc.strategy.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AppConfiguration {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4); // 根据策略实例数量调整，建议 > 实例数 * 2
        scheduler.setThreadNamePrefix("Hedge-Scheduler-");
        scheduler.initialize();
        return scheduler;
    }

}
