package com.sudocodes.monitoring.model;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Contains server identity information for multi-server deployments
 */
@Component
@Data
public class ServerIdentity {

    @Value("${server.identity}")
    private String serverId;
    
    @Value("${server.display-name}")
    private String displayName;
    
    @Value("${server.location}")
    private String location;
    
    @Value("${metrics.key.prefix}")
    private String metricsKeyPrefix;
    
    /**
     * Format a Redis key with the server prefix
     * @param keyType The type of metric (system, docker, rtsp, etc)
     * @param resourceId The specific resource ID
     * @return Formatted Redis key with server prefix
     */
    public String formatKey(String keyType, String resourceId) {
        return metricsKeyPrefix + ":" + keyType + ":" + resourceId;
    }
    
    /**
     * Format a Redis key pattern for wildcards with the server prefix
     * @param keyType The type of metric (system, docker, rtsp, etc)
     * @return Formatted Redis key pattern with server prefix
     */
    public String formatKeyPattern(String keyType) {
        return metricsKeyPrefix + ":" + keyType + ":*";
    }
} 