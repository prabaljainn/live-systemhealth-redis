package com.sudocodes.monitoring.service;

import com.sudocodes.monitoring.model.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class AlertService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Thresholds
    private static final double CPU_USAGE_THRESHOLD = 80.0;
    private static final double MEMORY_USAGE_THRESHOLD = 85.0;
    private static final double DISK_USAGE_THRESHOLD = 90.0;
    
    @Autowired
    public AlertService(RedisTemplate<String, Object> redisTemplate, SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
    }
    
    @Scheduled(fixedRate = 15000)
    public void checkSystemAlerts() {
        try {
            // Check CPU usage
            Object cpuUsage = redisTemplate.opsForHash().get("system:cpu", "usage_percent");
            if (cpuUsage != null && Double.parseDouble(cpuUsage.toString()) > CPU_USAGE_THRESHOLD) {
                createAlert(
                    "HIGH_CPU_USAGE",
                    "System CPU usage is high: " + cpuUsage + "%",
                    Alert.AlertLevel.WARNING,
                    Alert.AlertType.HIGH_CPU_USAGE,
                    "SYSTEM"
                );
            }
            
            // Check Memory usage
            Object memoryUsage = redisTemplate.opsForHash().get("system:memory", "usage_percent");
            if (memoryUsage != null && Double.parseDouble(memoryUsage.toString()) > MEMORY_USAGE_THRESHOLD) {
                createAlert(
                    "HIGH_MEMORY_USAGE",
                    "System memory usage is high: " + memoryUsage + "%",
                    Alert.AlertLevel.WARNING,
                    Alert.AlertType.HIGH_MEMORY_USAGE,
                    "SYSTEM"
                );
            }
        } catch (Exception e) {
            log.error("Error checking system alerts", e);
        }
    }
    
    @Scheduled(fixedRate = 30000)
    public void checkStorageAlerts() {
        try {
            // Check disk usage
            Set<String> diskKeys = redisTemplate.keys("storage:disk:*");
            
            for (String key : diskKeys) {
                String diskId = key.substring("storage:disk:".length());
                Object usagePercent = redisTemplate.opsForHash().get(key, "usage_percent");
                
                if (usagePercent != null && Double.parseDouble(usagePercent.toString()) > DISK_USAGE_THRESHOLD) {
                    createAlert(
                        "DISK_SPACE_LOW_" + diskId,
                        "Disk " + diskId + " usage is high: " + usagePercent + "%",
                        Alert.AlertLevel.WARNING,
                        Alert.AlertType.DISK_SPACE_LOW,
                        "STORAGE"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error checking storage alerts", e);
        }
    }
    
    @Scheduled(fixedRate = 10000)
    public void checkCameraAlerts() {
        try {
            Set<String> cameraKeys = redisTemplate.keys("camera:*:connection");
            
            for (String key : cameraKeys) {
                String cameraId = key.split(":")[1];
                Object status = redisTemplate.opsForHash().get(key, "status");
                
                if (status != null && "disconnected".equals(status.toString())) {
                    createAlert(
                        "CAMERA_DISCONNECTED_" + cameraId,
                        "Camera " + cameraId + " is disconnected",
                        Alert.AlertLevel.ERROR,
                        Alert.AlertType.CAMERA_DISCONNECTED,
                        "CAMERA"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error checking camera alerts", e);
        }
    }
    
    @Scheduled(fixedRate = 10000)
    public void checkRtspAlerts() {
        try {
            Set<String> streamKeys = redisTemplate.keys("rtsp:stream:*");
            
            for (String key : streamKeys) {
                String streamId = key.substring("rtsp:stream:".length());
                Object status = redisTemplate.opsForHash().get(key, "status");
                
                if (status != null && "disconnected".equals(status.toString())) {
                    createAlert(
                        "RTSP_STREAM_DOWN_" + streamId,
                        "RTSP stream " + streamId + " is down",
                        Alert.AlertLevel.ERROR,
                        Alert.AlertType.RTSP_STREAM_DOWN,
                        "RTSP"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error checking RTSP alerts", e);
        }
    }
    
    @Scheduled(fixedRate = 15000)
    public void checkDockerAlerts() {
        try {
            Set<String> containerKeys = redisTemplate.keys("docker:container:*");
            
            for (String key : containerKeys) {
                String containerId = key.substring("docker:container:".length());
                Object status = redisTemplate.opsForHash().get(key, "status");
                Object name = redisTemplate.opsForHash().get(key, "name");
                
                if (status != null && !"running".equalsIgnoreCase(status.toString())) {
                    String containerName = name != null ? name.toString() : containerId;
                    createAlert(
                        "DOCKER_CONTAINER_DOWN_" + containerId,
                        "Docker container " + containerName + " is not running (status: " + status + ")",
                        Alert.AlertLevel.ERROR,
                        Alert.AlertType.DOCKER_CONTAINER_DOWN,
                        "DOCKER"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error checking Docker alerts", e);
        }
    }
    
    private void createAlert(String alertKey, String message, Alert.AlertLevel level, Alert.AlertType type, String source) {
        try {
            // Check if this alert already exists and is active
            String alertId = "alert:" + alertKey;
            Boolean exists = redisTemplate.hasKey(alertId);
            
            if (Boolean.TRUE.equals(exists)) {
                Object activeStatus = redisTemplate.opsForHash().get(alertId, "active");
                if (activeStatus != null && Boolean.parseBoolean(activeStatus.toString())) {
                    // Alert already exists and is active, don't create a duplicate
                    return;
                }
            }
            
            // Create new alert
            String uuid = UUID.randomUUID().toString();
            Instant now = Instant.now();
            
            Alert alert = new Alert();
            alert.setId(uuid);
            alert.setMessage(message);
            alert.setLevel(level);
            alert.setType(type);
            alert.setSource(source);
            alert.setTimestamp(now);
            alert.setActive(true);
            
            // Store in Redis
            Map<String, String> alertData = new HashMap<>();
            alertData.put("id", uuid);
            alertData.put("message", message);
            alertData.put("level", level.name());
            alertData.put("type", type.name());
            alertData.put("source", source);
            alertData.put("timestamp", now.toString());
            alertData.put("active", "true");
            
            redisTemplate.opsForHash().putAll(alertId, alertData);
            
            // Add to active alerts set
            redisTemplate.opsForSet().add("active_alerts", alertKey);
            
            // Add to alert history list for this source
            String historyKey = "alert_history:" + source;
            redisTemplate.opsForList().leftPush(historyKey, uuid);
            redisTemplate.opsForList().trim(historyKey, 0, 99); // Keep last 100 alerts
            
            // Send via WebSocket for real-time notification
            messagingTemplate.convertAndSend("/topic/alerts", alert);
            
            log.info("Created alert: {}", message);
        } catch (Exception e) {
            log.error("Error creating alert", e);
        }
    }
    
    public void resolveAlert(String alertKey) {
        try {
            String alertId = "alert:" + alertKey;
            
            if (Boolean.TRUE.equals(redisTemplate.hasKey(alertId))) {
                // Update alert status
                redisTemplate.opsForHash().put(alertId, "active", "false");
                redisTemplate.opsForHash().put(alertId, "resolvedAt", Instant.now().toString());
                
                // Remove from active alerts
                redisTemplate.opsForSet().remove("active_alerts", alertKey);
                
                log.info("Resolved alert: {}", alertKey);
                
                // Get the alert data to send a resolved notification
                Map<Object, Object> alertData = redisTemplate.opsForHash().entries(alertId);
                alertData.put("active", false);
                alertData.put("resolvedAt", Instant.now().toString());
                
                // Send via WebSocket
                messagingTemplate.convertAndSend("/topic/alerts", alertData);
            }
        } catch (Exception e) {
            log.error("Error resolving alert: {}", alertKey, e);
        }
    }
} 