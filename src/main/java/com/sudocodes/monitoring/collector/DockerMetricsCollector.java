package com.sudocodes.monitoring.collector;

import com.sudocodes.monitoring.model.DockerMetrics;
import com.sudocodes.monitoring.model.ServerIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DockerMetricsCollector implements MetricsCollector {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ServerIdentity serverIdentity;
    
    @Value("${metrics.retention.max_records:3}")
    private int maxRecords;
    
    @Value("${metrics.docker.enabled:true}")
    private boolean dockerEnabled;
    
    @Autowired
    public DockerMetricsCollector(RedisTemplate<String, Object> redisTemplate, ServerIdentity serverIdentity) {
        this.redisTemplate = redisTemplate;
        this.serverIdentity = serverIdentity;
    }
    
    @Override
    @Scheduled(fixedRateString = "${metrics.schedule.docker:30000}")
    public void collectMetrics() {
        if (!dockerEnabled) {
            log.debug("Docker metrics collection is disabled");
            return;
        }
        
        try {
            // Get list of running containers
            List<Map<String, String>> containers = getContainerList();
            
            log.debug("Found {} Docker containers", containers.size());
            
            // For each container, get stats
            for (Map<String, String> container : containers) {
                String containerId = container.get("id");
                String containerName = container.get("name");
                String status = container.get("status");
                String simpleStatus = container.get("simple_status");
                
                // Store container info in Redis
                saveContainerInfo(containerId, container);
                
                // Skip stats collection if container not running
                if (!"running".equalsIgnoreCase(simpleStatus)) {
                    log.debug("Skipping stats collection for non-running container {}: {}", containerName, status);
                    continue;
                }
                
                // Get container stats
                Map<String, String> stats = getContainerStats(containerId);
                if (!stats.isEmpty()) {
                    // Store container stats in Redis
                    saveContainerStats(containerId, stats);
                }
            }
        } catch (Exception e) {
            log.error("Error collecting Docker metrics", e);
        }
    }
    
    /**
     * Get list of all Docker containers
     */
    private List<Map<String, String>> getContainerList() throws Exception {
        List<Map<String, String>> containers = new ArrayList<>();
        
        // Run docker ps -a command
        ProcessBuilder processBuilder = new ProcessBuilder("docker", "ps", "-a", "--format", "{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}");
        Process process = processBuilder.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    Map<String, String> container = new HashMap<>();
                    container.put("id", parts[0].trim());
                    container.put("name", parts[1].trim());
                    container.put("image", parts[2].trim());
                    container.put("status", parts[3].trim());
                    
                    // Extract simple status (running, exited, etc.)
                    if (parts[3].toLowerCase().contains("up")) {
                        container.put("simple_status", "running");
                    } else if (parts[3].toLowerCase().contains("exited")) {
                        container.put("simple_status", "stopped");
                    } else {
                        container.put("simple_status", "unknown");
                    }
                    
                    containers.add(container);
                    log.debug("Found container: {} ({}), status: {}", 
                              parts[1].trim(), parts[0].trim(), container.get("simple_status"));
                }
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("Docker ps command returned non-zero exit code: {}", exitCode);
        }
        
        return containers;
    }
    
    /**
     * Get stats for a specific container
     */
    private Map<String, String> getContainerStats(String containerId) throws Exception {
        Map<String, String> stats = new HashMap<>();
        
        // Run docker stats --no-stream command for a specific container
        log.debug("Fetching stats for container: {}", containerId);
        ProcessBuilder processBuilder = new ProcessBuilder("docker", "stats", containerId, "--no-stream", "--format", 
                "{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}|{{.NetIO}}|{{.BlockIO}}|{{.PIDs}}");
        Process process = processBuilder.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null) {
                log.debug("Raw stats for container {}: {}", containerId, line);
                String[] parts = line.split("\\|");
                if (parts.length >= 6) {
                    // CPU percentage (remove % sign)
                    stats.put("cpu_percent", parts[0].trim().replace("%", ""));
                    
                    // Memory usage
                    stats.put("memory_usage", parts[1].trim());
                    
                    // Extract memory values
                    Pattern memPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)([A-Za-z]+)\\s*/\\s*(\\d+(?:\\.\\d+)?)([A-Za-z]+)");
                    Matcher memMatcher = memPattern.matcher(parts[1].trim());
                    if (memMatcher.find()) {
                        stats.put("memory_used", memMatcher.group(1) + memMatcher.group(2));
                        stats.put("memory_limit", memMatcher.group(3) + memMatcher.group(4));
                    }
                    
                    // Memory percentage (remove % sign)
                    stats.put("memory_percent", parts[2].trim().replace("%", ""));
                    
                    // Network I/O
                    stats.put("net_io", parts[3].trim());
                    
                    // Extract network values
                    Pattern netPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)([A-Za-z]+)\\s*/\\s*(\\d+(?:\\.\\d+)?)([A-Za-z]+)");
                    Matcher netMatcher = netPattern.matcher(parts[3].trim());
                    if (netMatcher.find()) {
                        stats.put("net_input", netMatcher.group(1) + netMatcher.group(2));
                        stats.put("net_output", netMatcher.group(3) + netMatcher.group(4));
                    }
                    
                    // Block I/O
                    stats.put("block_io", parts[4].trim());
                    
                    // Extract block I/O values
                    Pattern blockPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)([A-Za-z]+)\\s*/\\s*(\\d+(?:\\.\\d+)?)([A-Za-z]+)");
                    Matcher blockMatcher = blockPattern.matcher(parts[4].trim());
                    if (blockMatcher.find()) {
                        stats.put("block_read", blockMatcher.group(1) + blockMatcher.group(2));
                        stats.put("block_write", blockMatcher.group(3) + blockMatcher.group(4));
                    }
                    
                    // PIDs
                    stats.put("pids", parts[5].trim());
                    
                    log.debug("Processed stats for container {}: CPU: {}%, Memory: {}", 
                             containerId, stats.get("cpu_percent"), stats.get("memory_percent"));
                }
            } else {
                log.warn("No stats returned for container: {}", containerId);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("Docker stats command returned non-zero exit code: {} for container {}", exitCode, containerId);
        }
        
        return stats;
    }
    
    /**
     * Save container info to Redis
     */
    private void saveContainerInfo(String containerId, Map<String, String> info) {
        try {
            // Store container info in Redis hash using server-prefixed key
            String key = serverIdentity.formatKey("docker", "container:" + containerId);
            redisTemplate.opsForHash().putAll(key, new HashMap<>(info));
            
            log.debug("Stored container info for {}: {}", containerId, info);
        } catch (Exception e) {
            log.error("Error saving container info to Redis", e);
        }
    }
    
    /**
     * Save container stats to Redis
     */
    private void saveContainerStats(String containerId, Map<String, String> stats) {
        try {
            // Store current stats in Redis hash using server-prefixed key
            String statsKey = serverIdentity.formatKey("docker", "stats:" + containerId);
            redisTemplate.opsForHash().putAll(statsKey, new HashMap<>(stats));
            
            // Store time-series data for CPU and memory
            long timestamp = System.currentTimeMillis();
            
            if (stats.containsKey("cpu_percent")) {
                try {
                    double cpuPercent = Double.parseDouble(stats.get("cpu_percent"));
                    redisTemplate.opsForZSet().add(
                            serverIdentity.formatKey("docker", "history:" + containerId + ":cpu"), 
                            cpuPercent, (double) timestamp);
                    
                    // Trim history to keep only recent records
                    trimTimeSeriesData(serverIdentity.formatKey("docker", "history:" + containerId + ":cpu"));
                } catch (NumberFormatException e) {
                    log.warn("Invalid CPU percentage value: {}", stats.get("cpu_percent"));
                }
            }
            
            if (stats.containsKey("memory_percent")) {
                try {
                    double memPercent = Double.parseDouble(stats.get("memory_percent"));
                    redisTemplate.opsForZSet().add(
                            serverIdentity.formatKey("docker", "history:" + containerId + ":memory"), 
                            memPercent, (double) timestamp);
                    
                    // Trim history to keep only recent records
                    trimTimeSeriesData(serverIdentity.formatKey("docker", "history:" + containerId + ":memory"));
                } catch (NumberFormatException e) {
                    log.warn("Invalid memory percentage value: {}", stats.get("memory_percent"));
                }
            }
            
            log.debug("Stored container stats for {}: {}", containerId, stats);
        } catch (Exception e) {
            log.error("Error saving container stats to Redis: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Trims a time-series data set to keep only the most recent records based on configured max records
     */
    private void trimTimeSeriesData(String key) {
        try {
            Long size = redisTemplate.opsForZSet().size(key);
            if (size != null && size > maxRecords) {
                // Get all members sorted by score (timestamp)
                redisTemplate.opsForZSet().removeRange(key, 0, size - maxRecords - 1);
            }
        } catch (Exception e) {
            log.error("Error trimming time series data for key {}: {}", key, e.getMessage());
        }
    }
} 