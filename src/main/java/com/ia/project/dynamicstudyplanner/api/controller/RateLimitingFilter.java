package com.ia.project.dynamicstudyplanner.api.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ia.project.dynamicstudyplanner.api.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Filter that applies Bucket4j rate limiting to the expensive optimizer endpoints.
 * It uses a Caffeine cache to track IP addresses.
 *
 * This filter intercepts requests before the controller, ensuring DoS protection.
 * If a request is rate-limited, it throws a RateLimitExceededException that is
 * handled by the HandlerExceptionResolver to trigger the @RestControllerAdvice.
 */
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> cache;
    private final int capacity;
    private final int refillTokens;
    private final int refillDurationMinutes;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public RateLimitingFilter(
            @Value("${api.rate-limit.capacity:5}") int capacity,
            @Value("${api.rate-limit.refill-tokens:5}") int refillTokens,
            @Value("${api.rate-limit.refill-duration-minutes:1}") int refillDurationMinutes,
            HandlerExceptionResolver handlerExceptionResolver) {

        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillDurationMinutes = refillDurationMinutes;
        this.handlerExceptionResolver = handlerExceptionResolver;

        // Configure Caffeine Cache with an eviction policy to avoid memory leaks
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000) // Maximum 10,000 IPs
                .expireAfterAccess(Duration.ofMinutes(60)) // Evict if inactive for 1h
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only rate limit the optimizer endpoints
        if (request.getRequestURI().startsWith("/api/v1/optimizer/")) {
            String clientIp = getClientIP(request);
            Bucket bucket = cache.get(clientIp, this::createNewBucket);

            if (!bucket.tryConsume(1)) {
                // If bucket is empty, pass to the exception resolver
                handlerExceptionResolver.resolveException(request, response, null,
                        new RateLimitExceededException("Rate limit exceeded for IP: " + clientIp));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createNewBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(refillTokens, Duration.ofMinutes(refillDurationMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
