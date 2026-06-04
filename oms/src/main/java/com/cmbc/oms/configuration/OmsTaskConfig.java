package com.cmbc.oms.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class OmsTaskConfig {

    @Bean
    public ThreadPoolTaskScheduler mgapPollingTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("Mgap-Polling-");
        scheduler.initialize();
        return scheduler;
    }

}
