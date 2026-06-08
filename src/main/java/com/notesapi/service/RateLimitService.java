package com.notesapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    @Value("${app.rate-limit.rpm:100}")
    private int requestsPerMinute;

    private final StringRedisTemplate redis;

    /**
     * Returns true if the request is within the allowed rate, false if throttled.
     * Uses a fixed 60-second window keyed on the start of the current minute.
     */
    public boolean isAllowed(UUID userId) {
        long windowStart = Instant.now().getEpochSecond() / 60;
        String key = "ratelimit:" + userId + ":" + windowStart;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofSeconds(60));
        }
        return count != null && count <= requestsPerMinute;
    }
}
