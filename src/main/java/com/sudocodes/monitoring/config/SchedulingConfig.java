package com.sudocodes.monitoring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Value("${metrics.schedule.critical}")
    private long criticalMetricsInterval;

    @Value("${metrics.schedule.system}")
    private long systemMetricsInterval;

    @Value("${metrics.schedule.storage}")
    private long storageMetricsInterval;

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(5);
        threadPoolTaskScheduler.setThreadNamePrefix("MetricsScheduler-");
        return threadPoolTaskScheduler;
    }

    public long getCriticalMetricsInterval() {
        return criticalMetricsInterval;
    }

    public long getSystemMetricsInterval() {
        return systemMetricsInterval;
    }

    public long getStorageMetricsInterval() {
        return storageMetricsInterval;
    }
} 