package com.sudocodes.monitoring.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RtspMetrics extends Metric {
    private String streamUrl;
    private String streamName;
    private boolean isActive;
    private String streamType;
    private String errorMessage;
    private long lastChecked;
    private int consecutiveFailures;
    private String codecType;
    
    public RtspMetrics() {
        super("rtsp", MetricsType.RTSP, null);
    }
    
    public RtspMetrics(String streamName, String streamUrl) {
        super("rtsp_" + streamName, MetricsType.RTSP, null);
        this.streamName = streamName;
        this.streamUrl = streamUrl;
    }
} 