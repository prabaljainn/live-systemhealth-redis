package com.sudocodes.monitoring.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration to exclude metrics auto-configuration and provide replacement beans
 * to prevent dependency injection errors.
 */
@Configuration
@ConditionalOnMissingClass({
    "io.micrometer.core.instrument.MeterRegistry",
    "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration"
})
public class MetricsExclusionConfig {
    
    /**
     * Placeholder class for webMvcMetricsFilter to prevent injection errors
     */
    public static class DummyWebMvcMetricsFilter {
        // Empty placeholder
    }
    
    /**
     * Bean to replace webMvcMetricsFilter
     */
    @Bean
    @Primary
    public DummyWebMvcMetricsFilter webMvcMetricsFilter() {
        return new DummyWebMvcMetricsFilter();
    }
} 