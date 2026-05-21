package com.ia.project.dynamicstudyplanner.api.security;

import com.ia.project.dynamicstudyplanner.domain.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * A servlet filter that intercepts requests to computationally expensive API endpoints.
 * It checks the client's IP against the Bucket4j RateLimitingService. If the limit
 * is exceeded, it immediately rejects the request, delegating the error response
 * to the GlobalExceptionHandler.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public RateLimitFilter(RateLimitingService rateLimitingService, HandlerExceptionResolver handlerExceptionResolver) {
        this.rateLimitingService = rateLimitingService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only apply rate limiting to the heavy optimizer endpoints
        if (request.getRequestURI().startsWith("/api/v1/optimizer")) {
            String clientIp = getClientIP(request);
            Bucket bucket = rateLimitingService.resolveBucket(clientIp);

            // tryConsume(1) instantly checks and decrements the bucket. It is lock-free and extremely fast.
            if (!bucket.tryConsume(1)) {
                // Delegate to GlobalExceptionHandler to format the response as RFC 7807 (429 Too Many Requests)
                handlerExceptionResolver.resolveException(
                        request,
                        response,
                        null,
                        new RateLimitExceededException("You have exceeded your API rate limit. Please try again later.")
                );
                return; // Stop the filter chain
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Retrieves the client IP.
     * Relying on Spring Boot's built-in ForwardedHeaderFilter configured via
     * server.forward-headers-strategy ensures X-Forwarded-For is only trusted
     * when correctly configured, mitigating IP spoofing vulnerabilities.
     */
    private String getClientIP(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
