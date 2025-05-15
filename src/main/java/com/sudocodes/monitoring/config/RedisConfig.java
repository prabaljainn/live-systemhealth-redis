package com.sudocodes.monitoring.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

@Configuration
@Slf4j
public class RedisConfig implements ApplicationRunner {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.timeout:5000}")
    private int timeout;
    
    @Value("${spring.redis.fallback.enabled:true}")
    private boolean fallbackEnabled;
    
    @Value("${spring.redis.fallback.host:localhost}")
    private String fallbackHost;
    
    @Value("${spring.redis.fallback.port:6379}")
    private int fallbackPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // First try the configured Redis host
        if (isRedisAvailable(redisHost, redisPort)) {
            log.info("Connecting to Redis at {}:{}", redisHost, redisPort);
            return createConnectionFactory(redisHost, redisPort);
        }
        
        // If fallback is enabled and primary is unavailable
        if (fallbackEnabled && !redisHost.equals(fallbackHost) && !isRedisAvailable(fallbackHost, fallbackPort)) {
            log.warn("Redis server at {}:{} is unavailable, falling back to {}:{}", 
                    redisHost, redisPort, fallbackHost, fallbackPort);
            return createConnectionFactory(fallbackHost, fallbackPort);
        }
        
        // Default to the configured host even if unavailable (will retry on operations)
        log.warn("Redis server at {}:{} is unavailable, operations will attempt to reconnect", redisHost, redisPort);
        return createConnectionFactory(redisHost, redisPort);
    }
    
    private RedisConnectionFactory createConnectionFactory(String host, int port) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(host, port);
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }
        
        // Configure Lettuce client with reconnection options
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(timeout))
            .readFrom(ReadFrom.MASTER_PREFERRED) // Always prefer master for read/write operations
            .clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .build())
            .build();
        
        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }
    
    private boolean isRedisAvailable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (Exception e) {
            log.warn("Redis server at {}:{} is not available: {}", host, port, e.getMessage());
            return false;
        }
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory());
        return container;
    }
    
    @Override
    public void run(ApplicationArguments args) {
        // Log Redis connection details on startup
        try {
            String status = isRedisAvailable(redisHost, redisPort) ? "CONNECTED" : "UNAVAILABLE";
            log.info("Redis connection status: {} ({}:{})", status, redisHost, redisPort);
            
            if (fallbackEnabled && !isRedisAvailable(redisHost, redisPort)) {
                String fallbackStatus = isRedisAvailable(fallbackHost, fallbackPort) ? "CONNECTED" : "UNAVAILABLE";
                log.info("Redis fallback status: {} ({}:{})", fallbackStatus, fallbackHost, fallbackPort);
            }
        } catch (Exception e) {
            log.error("Error verifying Redis connection", e);
        }
    }
} 