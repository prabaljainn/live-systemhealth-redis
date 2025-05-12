package com.sudocodes.monitoring.collector;

import com.sudocodes.monitoring.model.StorageMetrics;
import com.sudocodes.monitoring.model.ServerIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class StorageMetricsCollector implements MetricsCollector {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SystemInfo systemInfo;
    private final ServerIdentity serverIdentity;
    
    @Value("${metrics.retention.max_records:3}")
    private int maxRecords;
    
    @Autowired
    public StorageMetricsCollector(RedisTemplate<String, Object> redisTemplate, ServerIdentity serverIdentity) {
        this.redisTemplate = redisTemplate;
        this.serverIdentity = serverIdentity;
        this.systemInfo = new SystemInfo();
    }
    
    @Override
    @Scheduled(fixedRateString = "${metrics.schedule.storage}")
    public void collectMetrics() {
        try {
            FileSystem fileSystem = systemInfo.getOperatingSystem().getFileSystem();
            List<OSFileStore> fileStores = fileSystem.getFileStores();
            
            for (OSFileStore store : fileStores) {
                String mountPoint = store.getMount();
                String name = store.getName();
                String fsType = store.getType();
                
                File file = new File(mountPoint);
                if (!file.exists() || !mountPoint.startsWith("/")) {
                    continue; // Skip non-standard or virtual filesystems
                }
                
                long totalSpace = file.getTotalSpace();
                long freeSpace = file.getFreeSpace();
                long usableSpace = file.getUsableSpace();
                long usedSpace = totalSpace - freeSpace;
                
                // Skip if totalSpace is 0 (some virtual filesystems)
                if (totalSpace == 0) {
                    continue;
                }
                
                double usagePercent = (double) usedSpace / totalSpace * 100.0;
                
                // Create and populate StorageMetrics object
                StorageMetrics metrics = new StorageMetrics(mountPoint);
                metrics.setMountPoint(mountPoint);
                metrics.setFileSystem(fsType);
                metrics.setTotalSpace(totalSpace);
                metrics.setUsedSpace(usedSpace);
                metrics.setFreeSpace(freeSpace);
                metrics.setUsagePercent(usagePercent);
                
                // Store in Redis with server-prefixed keys
                String diskKey = serverIdentity.formatKey("storage", "disk:" + mountPoint.replace("/", "_"));
                
                Map<String, String> diskMetrics = new HashMap<>();
                diskMetrics.put("mount_point", mountPoint);
                diskMetrics.put("name", name);
                diskMetrics.put("filesystem", fsType);
                diskMetrics.put("total_gb", String.format("%.2f", totalSpace / (1024.0 * 1024 * 1024)));
                diskMetrics.put("used_gb", String.format("%.2f", usedSpace / (1024.0 * 1024 * 1024)));
                diskMetrics.put("free_gb", String.format("%.2f", freeSpace / (1024.0 * 1024 * 1024)));
                diskMetrics.put("usage_percent", String.format("%.2f", usagePercent));
                
                redisTemplate.opsForHash().putAll(diskKey, diskMetrics);
                
                // Store time-series data with server-prefixed keys
                String historyKey = serverIdentity.formatKey("storage", "history:" + mountPoint.replace("/", "_"));
                redisTemplate.opsForZSet().add(historyKey, usagePercent, (double) System.currentTimeMillis());
                
                // Trim history data
                trimTimeSeriesData(historyKey);
            }
            
            log.debug("Collected storage metrics for {} filesystems", fileStores.size());
        } catch (Exception e) {
            log.error("Error collecting storage metrics", e);
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