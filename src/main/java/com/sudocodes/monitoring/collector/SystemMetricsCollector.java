package com.sudocodes.monitoring.collector;

import com.sudocodes.monitoring.model.ServerIdentity;
import com.sudocodes.monitoring.model.SystemMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class SystemMetricsCollector implements MetricsCollector {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SystemInfo systemInfo;
    private final ServerIdentity serverIdentity;
    
    private long[] prevTicks;
    private long prevTickTime;
    
    @Value("${metrics.retention.max_records:3}")
    private int maxRecords;
    
    @Autowired
    public SystemMetricsCollector(RedisTemplate<String, Object> redisTemplate, ServerIdentity serverIdentity) {
        this.redisTemplate = redisTemplate;
        this.serverIdentity = serverIdentity;
        this.systemInfo = new SystemInfo();
        
        // Initialize CPU tracking
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        CentralProcessor processor = hardware.getProcessor();
        prevTicks = processor.getSystemCpuLoadTicks();
        prevTickTime = System.currentTimeMillis();
    }
    
    @Override
    @Scheduled(fixedRateString = "${metrics.schedule.system}")
    public void collectMetrics() {
        try {
            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            OperatingSystem os = systemInfo.getOperatingSystem();
            
            // Get CPU metrics
            Map<String, String> cpuMetrics = collectCpuMetrics(hardware);
            
            // Get memory metrics
            Map<String, String> memoryMetrics = collectMemoryMetrics(hardware);
            
            // Get process metrics
            Map<String, String> processMetrics = collectProcessMetrics(os);
            
            // Store in Redis using prefixed keys
            redisTemplate.opsForHash().putAll(serverIdentity.formatKey("system", "cpu"), cpuMetrics);
            redisTemplate.opsForHash().putAll(serverIdentity.formatKey("system", "memory"), memoryMetrics);
            redisTemplate.opsForHash().putAll(serverIdentity.formatKey("system", "processes"), processMetrics);
            
            // Store system identity information
            Map<String, String> serverInfo = new HashMap<>();
            serverInfo.put("id", serverIdentity.getServerId());
            serverInfo.put("name", serverIdentity.getDisplayName());
            serverInfo.put("location", serverIdentity.getLocation());
            serverInfo.put("os_name", os.getFamily() + " " + os.getVersionInfo());
            serverInfo.put("hostname", os.getNetworkParams().getHostName());
            
            redisTemplate.opsForHash().putAll(serverIdentity.formatKey("server", "info"), serverInfo);
            
            // Store time-series data for CPU and memory
            long timestamp = System.currentTimeMillis();
            double cpuUsage = Double.parseDouble(cpuMetrics.get("usage_percent"));
            double memoryUsage = Double.parseDouble(memoryMetrics.get("usage_percent"));
            
            redisTemplate.opsForZSet().add(serverIdentity.formatKey("system", "history:cpu"), 
                    cpuUsage, Double.valueOf(timestamp));
            redisTemplate.opsForZSet().add(serverIdentity.formatKey("system", "history:memory"), 
                    memoryUsage, Double.valueOf(timestamp));
            
            // Trim data to keep only recent records
            trimTimeSeriesData(serverIdentity.formatKey("system", "history:cpu"));
            trimTimeSeriesData(serverIdentity.formatKey("system", "history:memory"));
            
            log.debug("Collected system metrics - CPU: {}%, Memory: {}%", 
                    cpuMetrics.get("usage_percent"), memoryMetrics.get("usage_percent"));
            
        } catch (Exception e) {
            log.error("Error collecting system metrics", e);
        }
    }
    
    private Map<String, String> collectCpuMetrics(HardwareAbstractionLayer hardware) {
        Map<String, String> metrics = new HashMap<>();
        CentralProcessor processor = hardware.getProcessor();
        
        // Calculate CPU load between interval
        long[] ticks = processor.getSystemCpuLoadTicks();
        long tickTime = System.currentTimeMillis();
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks);
        double cpuUsage = cpuLoad * 100.0;
        prevTicks = ticks;
        prevTickTime = tickTime;
        
        // Get CPU load averages
        double[] loadAverage = processor.getSystemLoadAverage(3);
        
        metrics.put("cores", String.valueOf(processor.getLogicalProcessorCount()));
        metrics.put("usage_percent", String.format("%.2f", cpuUsage));
        
        if (loadAverage[0] != -1) {
            metrics.put("load_avg_1m", String.valueOf(loadAverage[0]));
        }
        if (loadAverage[1] != -1) {
            metrics.put("load_avg_5m", String.valueOf(loadAverage[1]));
        }
        if (loadAverage[2] != -1) {
            metrics.put("load_avg_15m", String.valueOf(loadAverage[2]));
        }
        
        return metrics;
    }
    
    private Map<String, String> collectMemoryMetrics(HardwareAbstractionLayer hardware) {
        Map<String, String> metrics = new HashMap<>();
        GlobalMemory memory = hardware.getMemory();
        
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        long usedMemory = totalMemory - availableMemory;
        
        double usagePercent = (double) usedMemory / totalMemory * 100.0;
        
        metrics.put("total_mb", String.valueOf(totalMemory / (1024 * 1024)));
        metrics.put("used_mb", String.valueOf(usedMemory / (1024 * 1024)));
        metrics.put("free_mb", String.valueOf(availableMemory / (1024 * 1024)));
        metrics.put("usage_percent", String.format("%.2f", usagePercent));
        
        return metrics;
    }
    
    private Map<String, String> collectProcessMetrics(OperatingSystem os) {
        Map<String, String> metrics = new HashMap<>();
        
        metrics.put("count", String.valueOf(os.getProcessCount()));
        metrics.put("threads", String.valueOf(os.getThreadCount()));
        
        return metrics;
    }
    
    /**
     * Trims a time-series data set to keep only the most recent records based on configured max records
     */
    private void trimTimeSeriesData(String key) {
        try {
            Long size = redisTemplate.opsForZSet().size(key);
            if (size != null && size > maxRecords) {
                // Get all members sorted by score (timestamp)
                Set<Object> oldestMembers = redisTemplate.opsForZSet().range(key, 0, size - maxRecords - 1);
                if (oldestMembers != null && !oldestMembers.isEmpty()) {
                    // Remove the oldest members, keeping only the most recent maxRecords
                    redisTemplate.opsForZSet().remove(key, oldestMembers.toArray());
                }
            }
        } catch (Exception e) {
            log.error("Error trimming time series data for key {}: {}", key, e.getMessage());
        }
    }
} 