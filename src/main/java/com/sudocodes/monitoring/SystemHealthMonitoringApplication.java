package com.sudocodes.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SystemHealthMonitoringApplication {

    public static void main(String[] args) {
        // Set system properties for better performance and to disable metrics
        System.setProperty("spring.main.banner-mode", "off");
        System.setProperty("spring.main.allow-bean-definition-overriding", "true");
        
        // Set JVM flags to disable cgroup metrics (only necessary in Docker environments)
        System.setProperty("jdk.internal.platform.cgroupMetricsAccess", "false");
        
        // Explicitly disable Docker metrics when running outside of Docker
        boolean dockerDetected = isDockerAvailable();
        if (!dockerDetected) {
            System.setProperty("metrics.docker.enabled", "false");
            System.out.println("Docker not detected. Docker metrics collection will be disabled.");
        }
        
        // Explicitly disable all actuator-related auto configurations
        System.setProperty("spring.autoconfigure.exclude", 
                "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.metrics.web.servlet.WebMvcMetricsAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.metrics.SystemMetricsAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration");
        
        SpringApplication.run(SystemHealthMonitoringApplication.class, args);
    }
    
    /**
     * Detect if Docker is available on the system
     */
    private static boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
} 