package com.ia.project.dynamicstudyplanner.api.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
