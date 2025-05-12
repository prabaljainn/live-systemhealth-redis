package com.sudocodes.monitoring.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import com.sudocodes.monitoring.model.ServerIdentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/metrics")
@Slf4j
public class MetricsController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ServerIdentity serverIdentity;
    
    @Autowired
    public MetricsController(RedisTemplate<String, Object> redisTemplate, ServerIdentity serverIdentity) {
        this.redisTemplate = redisTemplate;
        this.serverIdentity = serverIdentity;
    }
    
    /**
     * Get list of all servers reporting metrics
     */
    @GetMapping("/servers")
    public List<Map<String, Object>> getAllServers() {
        List<Map<String, Object>> servers = new ArrayList<>();
        
        try {
            // Find all server info keys (pattern: *:server:info)
            Set<String> serverKeys = redisTemplate.keys("*:server:info");
            
            for (String key : serverKeys) {
                Map<Object, Object> serverInfo = redisTemplate.opsForHash().entries(key);
                String serverId = key.split(":")[0]; // Extract server ID from key
                
                Map<String, Object> serverData = new HashMap<>();
                serverData.put("id", serverId);
                serverData.putAll(convertToStringMap(serverInfo));
                
                servers.add(serverData);
            }
        } catch (Exception e) {
            log.error("Error retrieving server list from Redis", e);
        }
        
        return servers;
    }
    
    /**
     * Get metrics for a specific server
     */
    @GetMapping("/server/{serverId}")
    public Map<String, Object> getServerMetrics(@PathVariable String serverId) {
        Map<String, Object> allMetrics = new HashMap<>();
        
        try {
            // Get server info
            Map<Object, Object> serverInfo = redisTemplate.opsForHash().entries(serverId + ":server:info");
            allMetrics.put("server_info", convertToStringMap(serverInfo));
            
            // Get all metrics for this server
            allMetrics.put("system", getSystemMetricsForServer(serverId));
            allMetrics.put("docker", getDockerMetricsForServer(serverId));
            allMetrics.put("storage", getStorageMetricsForServer(serverId));
            allMetrics.put("rtsp", getRtspMetricsForServer(serverId));
            allMetrics.put("network", getNetworkMetricsForServer(serverId));
            allMetrics.put("history", getMetricsHistoryForServer(serverId));
            
        } catch (Exception e) {
            log.error("Error retrieving metrics for server {}", serverId, e);
            allMetrics.put("error", "Error retrieving metrics: " + e.getMessage());
        }
        
        return allMetrics;
    }
    
    @GetMapping("/system")
    public Map<String, Object> getSystemMetrics() {
        // Default to current server's metrics
        return getSystemMetricsForServer(serverIdentity.getServerId());
    }
    
    private Map<String, Object> getSystemMetricsForServer(String serverId) {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Get CPU metrics
            Map<Object, Object> cpuMetrics = redisTemplate.opsForHash().entries(serverId + ":system:cpu");
            metrics.put("cpu", convertToStringMap(cpuMetrics));
            
            // Get Memory metrics
            Map<Object, Object> memoryMetrics = redisTemplate.opsForHash().entries(serverId + ":system:memory");
            metrics.put("memory", convertToStringMap(memoryMetrics));
            
            // Get Process metrics
            Map<Object, Object> processMetrics = redisTemplate.opsForHash().entries(serverId + ":system:processes");
            metrics.put("processes", convertToStringMap(processMetrics));
            
        } catch (Exception e) {
            log.error("Error retrieving system metrics from Redis for server {}", serverId, e);
            metrics.put("error", "Error retrieving system metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    @GetMapping("/docker")
    public Map<String, Object> getDockerMetrics() {
        // Default to current server's metrics
        return getDockerMetricsForServer(serverIdentity.getServerId());
    }
    
    private Map<String, Object> getDockerMetricsForServer(String serverId) {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Find all container keys for this server
            Set<String> containerKeys = redisTemplate.keys(serverId + ":docker:container:*");
            Map<String, Object> containers = new HashMap<>();
            
            for (String key : containerKeys) {
                String containerId = key.substring((serverId + ":docker:container:").length());
                Map<Object, Object> containerInfo = redisTemplate.opsForHash().entries(key);
                Map<Object, Object> containerStats = redisTemplate.opsForHash().entries(serverId + ":docker:stats:" + containerId);
                
                Map<String, Object> containerData = new HashMap<>();
                containerData.put("info", convertToStringMap(containerInfo));
                containerData.put("stats", convertToStringMap(containerStats));
                
                containers.put(containerId, containerData);
            }
            
            metrics.put("containers", containers);
            
        } catch (Exception e) {
            log.error("Error retrieving docker metrics from Redis for server {}", serverId, e);
            metrics.put("error", "Error retrieving docker metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    @GetMapping("/storage")
    public Map<String, Object> getStorageMetrics() {
        // Default to current server's metrics
        return getStorageMetricsForServer(serverIdentity.getServerId());
    }
    
    private Map<String, Object> getStorageMetricsForServer(String serverId) {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Find all disk keys for this server
            Set<String> diskKeys = redisTemplate.keys(serverId + ":storage:disk:*");
            Map<String, Object> disks = new HashMap<>();
            
            for (String key : diskKeys) {
                String diskId = key.substring((serverId + ":storage:disk:").length());
                Map<Object, Object> diskInfo = redisTemplate.opsForHash().entries(key);
                disks.put(diskId, convertToStringMap(diskInfo));
            }
            
            metrics.put("disks", disks);
            
        } catch (Exception e) {
            log.error("Error retrieving storage metrics from Redis for server {}", serverId, e);
            metrics.put("error", "Error retrieving storage metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    @GetMapping("/rtsp")
    public Map<String, Object> getRtspMetrics() {
        // Default to current server's metrics
        return getRtspMetricsForServer(serverIdentity.getServerId());
    }
    
    private Map<String, Object> getRtspMetricsForServer(String serverId) {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Find all rtsp stream keys for this server
            Set<String> streamKeys = redisTemplate.keys(serverId + ":rtsp:stream:*");
            Map<String, Object> streams = new HashMap<>();
            
            for (String key : streamKeys) {
                String streamId = key.substring((serverId + ":rtsp:stream:").length());
                Map<Object, Object> streamInfo = redisTemplate.opsForHash().entries(key);
                streams.put(streamId, convertToStringMap(streamInfo));
            }
            
            metrics.put("streams", streams);
            
        } catch (Exception e) {
            log.error("Error retrieving RTSP metrics from Redis for server {}", serverId, e);
            metrics.put("error", "Error retrieving RTSP metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    @GetMapping("/network")
    public Map<String, Object> getNetworkMetrics() {
        // Default to current server's metrics
        return getNetworkMetricsForServer(serverIdentity.getServerId());
    }
    
    private Map<String, Object> getNetworkMetricsForServer(String serverId) {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Find all network interface keys for this server
            Set<String> interfaceKeys = redisTemplate.keys(serverId + ":network:interface:*");
            Map<String, Object> interfaces = new HashMap<>();
            
            for (String key : interfaceKeys) {
                String interfaceName = key.substring((serverId + ":network:interface:").length());
                Map<Object, Object> interfaceInfo = redisTemplate.opsForHash().entries(key);
                interfaces.put(interfaceName, convertToStringMap(interfaceInfo));
            }
            
            metrics.put("interfaces", interfaces);
            
            // Get overall network stats
            Map<Object, Object> overallStats = redisTemplate.opsForHash().entries(serverId + ":network:overall");
            metrics.put("overall", convertToStringMap(overallStats));
            
        } catch (Exception e) {
            log.error("Error retrieving network metrics from Redis for server {}", serverId, e);
            metrics.put("error", "Error retrieving network metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    @GetMapping("/all")
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> allServersMetrics = new HashMap<>();
        
        try {
            // Get list of all servers
            List<Map<String, Object>> servers = getAllServers();
            
            for (Map<String, Object> server : servers) {
                String serverId = (String) server.get("id");
                Map<String, Object> serverMetrics = getServerMetrics(serverId);
                allServersMetrics.put(serverId, serverMetrics);
            }
            
            // Add server list for reference
            allServersMetrics.put("servers", servers);
            
        } catch (Exception e) {
            log.error("Error retrieving all metrics", e);
            allServersMetrics.put("error", "Error retrieving all metrics: " + e.getMessage());
        }
        
        return allServersMetrics;
    }
    
    @GetMapping("/history")
    public Map<String, List<Map<String, Object>>> getMetricsHistory() {
        // Default to current server's metrics history
        return getMetricsHistoryForServer(serverIdentity.getServerId());
    }
    
    private Map<String, List<Map<String, Object>>> getMetricsHistoryForServer(String serverId) {
        Map<String, List<Map<String, Object>>> historyData = new HashMap<>();
        
        try {
            // Get CPU history
            List<Map<String, Object>> cpuHistory = getTimeSeriesData(serverId + ":system:history:cpu");
            historyData.put("cpu", cpuHistory);
            
            // Get Memory history
            List<Map<String, Object>> memoryHistory = getTimeSeriesData(serverId + ":system:history:memory");
            historyData.put("memory", memoryHistory);
            
        } catch (Exception e) {
            log.error("Error retrieving history metrics from Redis for server {}", serverId, e);
        }
        
        return historyData;
    }
    
    /**
     * Retrieves time series data from Redis sorted set
     * @param key The Redis key for the sorted set
     * @return List of data points with timestamp and value
     */
    private List<Map<String, Object>> getTimeSeriesData(String key) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        try {
            // Get all entries from the sorted set
            Set<ZSetOperations.TypedTuple<Object>> dataPoints = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);
            
            if (dataPoints != null) {
                for (ZSetOperations.TypedTuple<Object> point : dataPoints) {
                    Map<String, Object> dataPoint = new HashMap<>();
                    dataPoint.put("timestamp", point.getScore());
                    dataPoint.put("value", point.getValue());
                    result.add(dataPoint);
                }
            }
        } catch (Exception e) {
            log.error("Error getting time series data for key {}: {}", key, e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Helper method to convert a Map<Object, Object> to Map<String, Object>
     */
    private Map<String, Object> convertToStringMap(Map<Object, Object> source) {
        Map<String, Object> target = new HashMap<>();
        if (source != null) {
            for (Map.Entry<Object, Object> entry : source.entrySet()) {
                target.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return target;
    }
} 