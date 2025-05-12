package com.sudocodes.monitoring.collector;

/**
 * Common interface for all metrics collectors
 */
public interface MetricsCollector {
    
    /**
     * Collect metrics and store them in Redis
     */
    void collectMetrics();
} 