package com.sudocodes.monitoring.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NetworkMetrics extends Metric {
    private String interfaceName;
    private boolean up;
    private String ipAddress;
    private String macAddress;
    private long bytesReceived;
    private long bytesSent;
    private long packetsReceived;
    private long packetsSent;
    private long receiveErrors;
    private long transmitErrors;
    private double receiveRateKBps;
    private double transmitRateKBps;
    
    public NetworkMetrics() {
        super("network", MetricsType.NETWORK, null);
    }
    
    public NetworkMetrics(String interfaceName) {
        super("network_" + interfaceName, MetricsType.NETWORK, null);
        this.interfaceName = interfaceName;
    }
} 