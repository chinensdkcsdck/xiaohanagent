package com.atguigu.java.ai.langchain4j.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Persists resumable workflow state; MySQL remains the source of truth for bookings. */
@Component
public class CheckpointStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Map<Long, TypedState> fallback = new ConcurrentHashMap<>();

    public CheckpointStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public TypedState load(Long memoryId) {
        TypedState local = fallback.get(memoryId);
        try {
            String value = redis.opsForValue().get(key(memoryId));
            if (value != null) return mapper.readValue(value, TypedState.class);
        } catch (Exception ignored) { }
        return local == null ? TypedState.initial() : local;
    }

    public void save(Long memoryId, TypedState state) {
        fallback.put(memoryId, state);
        try {
            redis.opsForValue().set(key(memoryId), mapper.writeValueAsString(state), Duration.ofHours(24));
        } catch (JsonProcessingException | RuntimeException ignored) { }
    }

    public void clear(Long memoryId) {
        fallback.remove(memoryId);
        try { redis.delete(key(memoryId)); } catch (RuntimeException ignored) { }
    }

    private String key(Long memoryId) { return "xiaohan:checkpoint:" + (memoryId == null ? -1 : memoryId); }
}
