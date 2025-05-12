package com.sudocodes.monitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import com.sudocodes.monitoring.collector.DockerMetricsCollector;
import com.sudocodes.monitoring.model.ServerIdentity;
import org.springframework.data.redis.core.RedisTemplate;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class CollectorConfig {
    
    /**
     * Dummy DockerMetricsCollector bean that does nothing
     * This bean is activated when metrics.docker.enabled=false
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "metrics.docker.enabled", havingValue = "false", matchIfMissing = false)
    public DockerMetricsCollector disabledDockerMetricsCollector(RedisTemplate<String, Object> redisTemplate, ServerIdentity serverIdentity) {
        log.info("Docker metrics collection disabled by configuration");
        return new DockerMetricsCollector(redisTemplate, serverIdentity) {
            @Override
            public void collectMetrics() {
                // Do nothing
                log.debug("Docker metrics collection is disabled");
            }
        };
    }
} 