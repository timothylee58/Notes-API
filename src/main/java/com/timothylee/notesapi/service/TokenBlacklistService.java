package com.timothylee.notesapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redis;

    public void blacklistToken(String jti, Instant expiry) {
        long ttlSeconds = expiry.getEpochSecond() - Instant.now().getEpochSecond();
        if (ttlSeconds > 0) {
            redis.opsForValue().set(KEY_PREFIX + jti, "revoked", Duration.ofSeconds(ttlSeconds));
            log.debug("Blacklisted token jti={} with TTL={}s", jti, ttlSeconds);
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
