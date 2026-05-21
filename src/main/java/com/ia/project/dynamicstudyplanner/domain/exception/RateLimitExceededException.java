package com.ia.project.dynamicstudyplanner.domain.exception;

/**
 * Exception thrown when a client exceeds their API rate limit allocation.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
