package com.urlshortener.config;

import com.urlshortener.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limiting interceptor using Bucket4j token-bucket algorithm.
 *
 * Applied only to POST /api/v1/shorten to prevent abuse (ID exhaustion,
 * database flooding). Redirects (GET /{code}) are not rate-limited since
 * they are the hot read path and are already cached.
 *
 * Design: per-IP token bucket with configurable capacity and refill rate.
 * Default: 20 requests per minute per IP address.
 *
 * In production, this would be backed by Redis for distributed rate limiting
 * across multiple app instances. In-memory ConcurrentHashMap is sufficient
 * for single-node deployment.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int CAPACITY = 20;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String clientIp = getClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, this::createBucket);

        if (bucket.tryConsume(1)) {
            return true;
        }

        // Rate limit exceeded
        throw new com.urlshortener.exception.RateLimitExceededException();
    }

    private Bucket createBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(CAPACITY)
                        .refillGreedy(CAPACITY, REFILL_PERIOD)
                        .build())
                .build();
    }

    /**
     * Extract client IP, respecting X-Forwarded-For for proxied requests.
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
