package com.sudocodes.monitoring.collector;

import com.sudocodes.monitoring.model.NetworkMetrics;
import com.sudocodes.monitoring.model.ServerIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class NetworkMetricsCollector implements MetricsCollector {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final SystemInfo systemInfo;
    private final ServerIdentity serverIdentity;
    
    // Store previous readings for calculating rates
    private Map<String, Long> previousReceivedBytes = new HashMap<>();
    private Map<String, Long> previousSentBytes = new HashMap<>();
    private long previousTimestamp = System.currentTimeMillis();
    
    @Value("${metrics.retention.max_records:3}")
    private int maxRecords;
    
    @Autowired
    public NetworkMetricsCollector(RedisTemplate<String, Object> redisTemplate, ServerIdentity serverIdentity) {
        this.redisTemplate = redisTemplate;
        this.serverIdentity = serverIdentity;
        this.systemInfo = new SystemInfo();
    }
    
    @Override
    @Scheduled(fixedRateString = "${metrics.schedule.system}")
    public void collectMetrics() {
        try {
            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            List<NetworkIF> networkInterfaces = hardware.getNetworkIFs();
            long currentTimestamp = System.currentTimeMillis();
            double timeDiffSeconds = (currentTimestamp - previousTimestamp) / 1000.0;
            
            long totalReceived = 0;
            long totalSent = 0;
            
            // Calculate and store metrics for each network interface
            for (NetworkIF networkIF : networkInterfaces) {
                networkIF.updateAttributes();
                String interfaceName = networkIF.getName();
                
                long bytesReceived = networkIF.getBytesRecv();
                long bytesSent = networkIF.getBytesSent();
                long packetsReceived = networkIF.getPacketsRecv();
                long packetsSent = networkIF.getPacketsSent();
                long inErrors = networkIF.getInErrors();
                long outErrors = networkIF.getOutErrors();
                
                totalReceived += bytesReceived;
                totalSent += bytesSent;
                
                // Calculate rate of data transfer
                double receivedRate = 0;
                double sentRate = 0;
                
                if (previousReceivedBytes.containsKey(interfaceName) && timeDiffSeconds > 0) {
                    long receivedDiff = bytesReceived - previousReceivedBytes.get(interfaceName);
                    long sentDiff = bytesSent - previousSentBytes.get(interfaceName);
                    
                    // Avoid negative values that might occur on counter reset
                    receivedRate = Math.max(0, receivedDiff) / timeDiffSeconds;
                    sentRate = Math.max(0, sentDiff) / timeDiffSeconds;
                }
                
                // Store values for next calculation
                previousReceivedBytes.put(interfaceName, bytesReceived);
                previousSentBytes.put(interfaceName, bytesSent);
                
                // Format metrics to match the NetworkMetrics class
                NetworkMetrics metrics = new NetworkMetrics(interfaceName);
                metrics.setUp(networkIF.getIfOperStatus().name().equalsIgnoreCase("UP"));
                metrics.setIpAddress(String.join(", ", networkIF.getIPv4addr()));
                metrics.setMacAddress(networkIF.getMacaddr());
                metrics.setBytesReceived(bytesReceived);
                metrics.setBytesSent(bytesSent);
                metrics.setPacketsReceived(packetsReceived);
                metrics.setPacketsSent(packetsSent);
                metrics.setReceiveErrors(inErrors);
                metrics.setTransmitErrors(outErrors);
                metrics.setReceiveRateKBps(receivedRate / 1024);
                metrics.setTransmitRateKBps(sentRate / 1024);
                
                // Store in Redis
                Map<String, String> interfaceMetrics = new HashMap<>();
                interfaceMetrics.put("name", networkIF.getName());
                interfaceMetrics.put("status", networkIF.getIfOperStatus().name());
                interfaceMetrics.put("ip_address", String.join(", ", networkIF.getIPv4addr()));
                interfaceMetrics.put("mac", networkIF.getMacaddr());
                interfaceMetrics.put("mtu", String.valueOf(networkIF.getMTU()));
                interfaceMetrics.put("received_mb", String.format("%.2f", bytesReceived / (1024.0 * 1024)));
                interfaceMetrics.put("sent_mb", String.format("%.2f", bytesSent / (1024.0 * 1024)));
                interfaceMetrics.put("packets_received", String.valueOf(packetsReceived));
                interfaceMetrics.put("packets_sent", String.valueOf(packetsSent));
                interfaceMetrics.put("in_errors", String.valueOf(inErrors));
                interfaceMetrics.put("out_errors", String.valueOf(outErrors));
                interfaceMetrics.put("received_rate_kbps", String.format("%.2f", receivedRate / 1024));
                interfaceMetrics.put("sent_rate_kbps", String.format("%.2f", sentRate / 1024));
                
                String interfaceKey = serverIdentity.formatKey("network", "interface:" + interfaceName);
                redisTemplate.opsForHash().putAll(interfaceKey, interfaceMetrics);
                
                // Store time-series data for interface network rates
                String recvHistoryKey = serverIdentity.formatKey("network", "history:" + interfaceName + ":received");
                String sentHistoryKey = serverIdentity.formatKey("network", "history:" + interfaceName + ":sent");
                
                redisTemplate.opsForZSet().add(recvHistoryKey, receivedRate / 1024, (double) currentTimestamp);
                redisTemplate.opsForZSet().add(sentHistoryKey, sentRate / 1024, (double) currentTimestamp);
                
                // Trim data to keep only recent records
                trimTimeSeriesData(recvHistoryKey);
                trimTimeSeriesData(sentHistoryKey);
            }
            
            // Store overall network metrics
            Map<String, String> overallMetrics = new HashMap<>();
            overallMetrics.put("total_received_mb", String.valueOf(totalReceived / (1024 * 1024)));
            overallMetrics.put("total_sent_mb", String.valueOf(totalSent / (1024 * 1024)));
            overallMetrics.put("interface_count", String.valueOf(networkInterfaces.size()));
            
            String overallKey = serverIdentity.formatKey("network", "overall");
            redisTemplate.opsForHash().putAll(overallKey, overallMetrics);
            
            // Update timestamp for next calculation
            previousTimestamp = currentTimestamp;
            
            log.debug("Collected network metrics for {} interfaces", networkInterfaces.size());
        } catch (Exception e) {
            log.error("Error collecting network metrics", e);
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