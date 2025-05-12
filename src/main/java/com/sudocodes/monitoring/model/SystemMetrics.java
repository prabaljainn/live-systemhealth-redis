package com.sudocodes.monitoring.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SystemMetrics extends Metric {
    private double cpuUsagePercent;
    private int cpuCores;
    private double systemLoadAverage;
    private long totalMemory;
    private long usedMemory;
    private long freeMemory;
    private double memoryUsagePercent;
    private int processCount;
    private int threadCount;
    private long uptime;
    
    public SystemMetrics() {
        super("system", MetricsType.SYSTEM, null);
    }
} 