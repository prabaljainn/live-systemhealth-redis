package com.sudocodes.monitoring.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StorageMetrics extends Metric {
    private String mountPoint;
    private String fileSystem;
    private long totalSpace;
    private long usedSpace;
    private long freeSpace;
    private double usagePercent;
    private long inodeTotal;
    private long inodeUsed;
    private long inodeFree;
    private double inodeUsagePercent;
    
    public StorageMetrics() {
        super("storage", MetricsType.STORAGE, null);
    }
    
    public StorageMetrics(String mountPoint) {
        super("storage_" + mountPoint.replace("/", "_"), MetricsType.STORAGE, null);
        this.mountPoint = mountPoint;
    }
} 