package com.sudocodes.monitoring.model;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class Metric {
    private String name;
    private MetricsType type;
    private Instant timestamp;
    private Map<String, Object> values;
    
    public Metric() {
        this.timestamp = Instant.now();
    }
    
    public Metric(String name, MetricsType type, Map<String, Object> values) {
        this.name = name;
        this.type = type;
        this.values = values;
        this.timestamp = Instant.now();
    }
} 