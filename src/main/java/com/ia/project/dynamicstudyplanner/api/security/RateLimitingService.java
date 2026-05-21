package com.ia.project.dynamicstudyplanner.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service responsible for managing rate-limiting buckets per client IP address.
 * It uses Bucket4j's Token Bucket algorithm entirely in-memory.
 */
@Service
public class RateLimitingService {

    // Caffeine cache avoids memory leak DoS by evicting inactive IPs and limiting size
    private final Cache<String, Bucket> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    private final int capacity;
    private final int tokens;
    private final int durationMinutes;

    public RateLimitingService(
            @Value("${api.rate-limit.capacity:5}") int capacity,
            @Value("${api.rate-limit.refill-tokens:5}") int tokens,
            @Value("${api.rate-limit.refill-duration-minutes:1}") int durationMinutes) {
        this.capacity = capacity;
        this.tokens = tokens;
        this.durationMinutes = durationMinutes;
    }

    /**
     * Resolves an existing bucket for the given IP or creates a new one if it doesn't exist.
     * @param ip The remote IP address of the client.
     * @return The Bucket instance mapped to the IP.
     */
    public Bucket resolveBucket(String ip) {
        return cache.get(ip, this::newBucket);
    }

    private Bucket newBucket(String ip) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(tokens, Duration.ofMinutes(durationMinutes))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
