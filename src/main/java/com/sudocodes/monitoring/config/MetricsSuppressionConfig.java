package com.sudocodes.monitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * This configuration class replaces the problematic metrics
 * with dummy beans to prevent Spring Boot from creating
 * the real ones that would cause errors in Docker.
 */
@Configuration
public class MetricsSuppressionConfig {
    
    /**
     * Create a dummy interface for ProcessorMetrics to suppress the real one
     */
    public static class DummyProcessorMetrics {
        // Empty dummy class
    }
    
    /**
     * Provide a dummy ProcessorMetrics bean
     */
    @Bean
    @Primary
    public DummyProcessorMetrics processorMetrics() {
        return new DummyProcessorMetrics();
    }
    
    /**
     * Create a dummy interface for MeterRegistry to suppress the real one
     */
    public static class DummyMeterRegistry {
        // Empty dummy class
    }
    
    /**
     * Provide a dummy MeterRegistry bean
     */
    @Bean
    @Primary
    public DummyMeterRegistry simpleMeterRegistry() {
        return new DummyMeterRegistry();
    }
} 