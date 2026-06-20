package com.timothylee.notesapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!LOGIN_PATH.equals(path) && !REGISTER_PATH.equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        Bucket bucket = LOGIN_PATH.equals(path)
                ? loginBuckets.computeIfAbsent(ip, k -> newBucket(5))
                : registerBuckets.computeIfAbsent(ip, k -> newBucket(3));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = bucket.getAvailableTokens() == 0 ? 60 : 1;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            var problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded — try again later");
            objectMapper.writeValue(response.getWriter(), problem);
        }
    }

    private static Bucket newBucket(int tokensPerMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(tokensPerMinute)
                        .refillGreedy(tokensPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
