package com.ia.project.dynamicstudyplanner.domain.exception;

/**
 * Base exception for all business rule violations in the domain layer.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
