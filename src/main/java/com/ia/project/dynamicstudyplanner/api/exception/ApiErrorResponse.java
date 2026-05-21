package com.ia.project.dynamicstudyplanner.api.exception;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
/**
 * Standardized API error response structured according to RFC 7807 concepts,
 * but customized with specific requested fields (timestamp, path, validationErrors).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized error response conforming to RFC 7807 concepts.")
public record ApiErrorResponse(
        @Schema(description = "The timestamp when the error occurred.", example = "2023-10-27T10:15:30")
        LocalDateTime timestamp,
        @Schema(description = "The HTTP status code.", example = "400")
        int status,
        @Schema(description = "The HTTP error type.", example = "Bad Request")
        String error,
        @Schema(description = "A human-readable explanation of the error.", example = "Validation failed")
        String message,
        @Schema(description = "The path that triggered the error.", example = "/api/v1/optimizer/generate")
        String path,
        @Schema(description = "A list of detailed field validation errors, if applicable.")
        List<ValidationError> validationErrors
) {
    /**
     * Represents a single validation error on a specific field.
     */
    @Schema(description = "Details about a specific field validation failure.")
    public record ValidationError(
            @Schema(description = "The name of the field that failed validation.", example = "exam.name")
            String field,
            @Schema(description = "The validation error message.", example = "Exam name cannot be blank.")
            String message
    ) {}
}
