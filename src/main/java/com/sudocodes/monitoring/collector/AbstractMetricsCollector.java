package com.sudocodes.monitoring.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for metrics collectors with proper shutdown handling
 */
@Slf4j
public abstract class AbstractMetricsCollector implements MetricsCollector {

    protected final RedisTemplate<String, Object> redisTemplate;
    protected final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final int maxConsecutiveFailuresToLog = 3;
    
    protected AbstractMetricsCollector(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @PostConstruct
    public void init() {
        log.info("{} initialized", getClass().getSimpleName());
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("{} shutting down", getClass().getSimpleName());
        shuttingDown.set(true);
    }
    
    /**
     * Check if the application is shutting down
     * @return true if the application is in the process of shutting down
     */
    protected boolean isShuttingDown() {
        return shuttingDown.get();
    }
    
    /**
     * Safely perform a Redis operation, handling connection failures during shutdown
     * @param operation The Redis operation to perform
     * @param errorMessage The error message to log if an exception occurs
     */
    protected void safeRedisOperation(Runnable operation, String errorMessage) {
        if (isShuttingDown()) {
            log.debug("Skipping Redis operation - application is shutting down");
            return;
        }
        
        try {
            operation.run();
            // Reset consecutive failures on success
            if (consecutiveFailures.get() > 0) {
                log.info("Redis connection restored after {} consecutive failures", consecutiveFailures.get());
                consecutiveFailures.set(0);
            }
        } catch (RedisConnectionFailureException e) {
            if (isShuttingDown()) {
                log.debug("Redis connection failed during shutdown (expected)");
            } else {
                int failures = consecutiveFailures.incrementAndGet();
                // Only log every few failures to avoid filling logs
                if (failures <= maxConsecutiveFailuresToLog || failures % 10 == 0) {
                    log.error("{}: {}", errorMessage, e.getMessage());
                    if (failures == maxConsecutiveFailuresToLog) {
                        log.warn("Suppressing excessive Redis connection errors. Will log every 10th error.");
                    }
                }
            }
        } catch (Exception e) {
            log.error("{}", errorMessage, e);
        }
    }
} 