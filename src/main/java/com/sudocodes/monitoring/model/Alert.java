package com.sudocodes.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    private String id;
    private String message;
    private AlertLevel level;
    private AlertType type;
    private String source;
    private Instant timestamp;
    private boolean active;
    private Instant resolvedAt;
    
    public enum AlertLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    public enum AlertType {
        RTSP_STREAM_DOWN,
        CAMERA_DISCONNECTED,
        DOCKER_CONTAINER_DOWN,
        HIGH_CPU_USAGE,
        HIGH_MEMORY_USAGE,
        DISK_SPACE_LOW,
        NETWORK_BANDWIDTH_HIGH,
        STORAGE_ERROR,
        S3_BUCKET_ERROR,
        SYSTEM_ERROR
    }
} 