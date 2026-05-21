package com.ia.project.dynamicstudyplanner.api.exception;

import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Centralized exception handler for all API controllers.
 * Implements strict RFC 7807 Problem Details for all HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String INVALID_PARAMS_PROPERTY = "invalid_params";

    /**
     * Handles validation errors from @Valid on RequestBody.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<InvalidParam> errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    String fieldName = error instanceof FieldError ? ((FieldError) error).getField() : error.getObjectName();
                    return new InvalidParam(fieldName, error.getDefaultMessage());
                })
                .toList();

        log.warn("Validation error on path {}: {}", request.getRequestURI(), errors);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more validation constraints were violated.");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/validation-failed"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setProperty(INVALID_PARAMS_PROPERTY, errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles validation errors from path variables or request parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<InvalidParam> errors = ex.getConstraintViolations()
                .stream()
                .map(violation -> new InvalidParam(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        log.warn("Constraint violation on path {}: {}", request.getRequestURI(), errors);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more validation constraints were violated.");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/constraint-violation"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setProperty(INVALID_PARAMS_PROPERTY, errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles standard IllegalArgumentExceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Illegal argument on path {}: {}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/illegal-argument"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles custom business logic domain exceptions.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(
            DomainException ex, HttpServletRequest request) {

        log.warn("Domain exception on path {}: {}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problemDetail.setTitle("Unprocessable Entity");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/domain-rule-violation"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problemDetail);
    }

    /**
     * Handles Spring Security Authentication Exceptions (e.g. invalid credentials).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed on path {}: {}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
        problemDetail.setTitle("Unauthorized");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/unauthorized"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    /**
     * Handles Spring Security Access Denied Exceptions (e.g. insufficient roles).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied on path {}: {}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
        problemDetail.setTitle("Forbidden");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/forbidden"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    /**
     * Catch-all handler for any unhandled RuntimeExceptions or general Exceptions.
     * Prevents internal implementation details or stack traces from leaking to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllUncaughtException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error occurred on path {}", request.getRequestURI(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred.");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/internal-server-error"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
