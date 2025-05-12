package com.sudocodes.monitoring.collector;

import com.sudocodes.monitoring.model.RtspMetrics;
import com.sudocodes.monitoring.model.ServerIdentity;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVInputFormat;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.BytePointer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;

@Service
@Slf4j
public class RtspMetricsCollector implements MetricsCollector {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ServerIdentity serverIdentity;
    
    @Value("${metrics.retention.max_records:3}")
    private int maxRecords;
    
    @Value("${rtsp.connect.timeout:5000}")
    private int connectTimeout;
    
    // Hardcoded list of streams for now
    private final List<Map<String, String>> streams = List.of(
        Map.of("name", "stream1", "url", "rtsp://rtsp.sudocodes.com:8554/stream1"),
        Map.of("name", "stream2", "url", "rtsp://rtsp.sudocodes.com:8554/stream2"),
        Map.of("name", "stream3", "url", "rtsp://rtsp.sudocodes.com:8554/stream3"),
        Map.of("name", "stream4", "url", "rtsp://rtsp.sudocodes.com:8554/stream4"),
        Map.of("name", "stream5", "url", "rtsp://rtsp.sudocodes.com:8554/stream5")
    );
    
    @Autowired
    public RtspMetricsCollector(RedisTemplate<String, Object> redisTemplate, ServerIdentity serverIdentity) {
        this.redisTemplate = redisTemplate;
        this.serverIdentity = serverIdentity;
    }
    
    @PostConstruct
    public void init() {
        // Initialize FFmpeg network components
        // Note: av_register_all() is deprecated in newer FFmpeg versions and no longer needed
        avformat.avformat_network_init();
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);
        log.info("FFmpeg initialized for RTSP stream monitoring");
    }
    
    @Override
    @Scheduled(fixedRateString = "${metrics.schedule.rtsp:60000}")
    public void collectMetrics() {
        try {
            log.debug("Collecting RTSP stream metrics for {} streams", streams.size());
            
            // For each stream, check status
            for (Map<String, String> stream : streams) {
                String streamName = stream.get("name");
                String streamUrl = stream.get("url");
                
                // Get stream status
                Map<String, Object> status = checkStreamStatus(streamName, streamUrl);
                
                // Store stream info in Redis
                saveStreamInfo(streamName, streamUrl, status);
            }
        } catch (Exception e) {
            log.error("Error collecting RTSP metrics", e);
        }
    }
    
    /**
     * Check RTSP stream status using bytedeco's FFmpeg bindings
     */
    private Map<String, Object> checkStreamStatus(String streamName, String streamUrl) {
        Map<String, Object> result = new HashMap<>();
        result.put("stream_name", streamName);
        result.put("stream_url", streamUrl);
        result.put("last_checked", System.currentTimeMillis());
        
        // Get previous consecutive failures from Redis
        String redisKey = serverIdentity.formatKey("rtsp", "stream:" + streamName);
        Object prevFailures = redisTemplate.opsForHash().get(redisKey, "consecutive_failures");
        int consecutiveFailures = prevFailures != null ? Integer.parseInt(prevFailures.toString()) : 0;
        
        // Create AVFormatContext
        AVFormatContext formatContext = avformat.avformat_alloc_context();
        
        try {
            log.debug("Checking RTSP stream: {}", streamUrl);
            
            // Set connection timeout options
            AVDictionary options = new AVDictionary(null);
            avutil.av_dict_set(options, "rtsp_transport", "tcp", 0);
            avutil.av_dict_set(options, "timeout", String.valueOf(connectTimeout * 1000), 0); // microseconds
            avutil.av_dict_set(options, "stimeout", String.valueOf(connectTimeout * 1000), 0); // microseconds
            
            // Open the RTSP stream
            int ret = avformat.avformat_open_input(formatContext, streamUrl, null, options);
            if (ret < 0) {
                BytePointer errorMsg = new BytePointer(256);
                avutil.av_strerror(ret, errorMsg, 256);
                String errorString = errorMsg.getString();
                throw new IOException("Could not open RTSP stream: " + errorString);
            }
            
            // Get stream information
            ret = avformat.avformat_find_stream_info(formatContext, (PointerPointer) null);
            if (ret < 0) {
                throw new IOException("Could not find stream info");
            }
            
            // Get information about the streams
            StringBuilder codecTypes = new StringBuilder();
            boolean hasVideoStream = false;
            boolean hasAudioStream = false;
            
            for (int i = 0; i < formatContext.nb_streams(); i++) {
                AVCodecParameters codecParams = formatContext.streams(i).codecpar();
                
                if (codecParams.codec_type() == avutil.AVMEDIA_TYPE_VIDEO) {
                    hasVideoStream = true;
                    codecTypes.append("video").append("\n");
                } else if (codecParams.codec_type() == avutil.AVMEDIA_TYPE_AUDIO) {
                    hasAudioStream = true;
                    codecTypes.append("audio").append("\n");
                }
            }
            
            // We found at least one stream, so the RTSP connection is valid
            result.put("active", true);
            result.put("error_message", "");
            result.put("codec_type", codecTypes.toString().trim());
            result.put("consecutive_failures", 0);
            result.put("has_video", hasVideoStream);
            result.put("has_audio", hasAudioStream);
            
            log.debug("Stream {} is active: {}", streamName, codecTypes.toString().trim());
            
        } catch (Exception e) {
            result.put("active", false);
            result.put("error_message", e.getMessage());
            result.put("codec_type", "");
            result.put("consecutive_failures", ++consecutiveFailures);
            result.put("has_video", false);
            result.put("has_audio", false);
            
            log.error("Error checking RTSP stream {}: {}", streamUrl, e.getMessage());
        } finally {
            // Clean up resources
            if (formatContext != null) {
                avformat.avformat_close_input(formatContext);
                avformat.avformat_free_context(formatContext);
            }
        }
        
        return result;
    }
    
    /**
     * Save stream info to Redis
     */
    private void saveStreamInfo(String streamName, String streamUrl, Map<String, Object> status) {
        try {
            // Store stream info in Redis hash with server-prefixed key
            String redisKey = serverIdentity.formatKey("rtsp", "stream:" + streamName);
            redisTemplate.opsForHash().putAll(redisKey, status);
            
            // Store time-series data for stream status with server-prefixed key
            long timestamp = System.currentTimeMillis();
            boolean isActive = (boolean) status.get("active");
            
            String historyKey = serverIdentity.formatKey("rtsp", "history:" + streamName);
            redisTemplate.opsForZSet().add(historyKey, isActive ? 1.0 : 0.0, (double) timestamp);
            
            // Trim history to keep only recent records
            trimTimeSeriesData(historyKey);
            
            log.debug("Stored RTSP stream info for {}: {}", streamName, status);
        } catch (Exception e) {
            log.error("Error saving RTSP stream info to Redis: {}", e.getMessage(), e);
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