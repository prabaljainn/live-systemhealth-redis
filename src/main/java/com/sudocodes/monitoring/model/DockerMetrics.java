package com.sudocodes.monitoring.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DockerMetrics extends Metric {
    private String containerId;
    private String containerName;
    private String imageId;
    private String imageName;
    private String status;
    private double cpuUsagePercent;
    private long memoryUsage;
    private double memoryUsagePercent;
    private long networkRx;
    private long networkTx;
    private long blockRead;
    private long blockWrite;
    
    public DockerMetrics() {
        super("docker", MetricsType.DOCKER, null);
    }
    
    public DockerMetrics(String containerId, String containerName) {
        super("docker_" + containerName, MetricsType.DOCKER, null);
        this.containerId = containerId;
        this.containerName = containerName;
    }
} 