package com.ia.project.dynamicstudyplanner.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardized API error response structured according to RFC 7807 concepts,
 * but customized with specific requested fields (timestamp, path, validationErrors).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ValidationError> validationErrors
) {
    /**
     * Represents a single validation error on a specific field.
     */
    public record ValidationError(String field, String message) {}
}
