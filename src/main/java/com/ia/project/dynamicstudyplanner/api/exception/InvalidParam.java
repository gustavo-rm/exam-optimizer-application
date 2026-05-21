package com.ia.project.dynamicstudyplanner.api.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a single validation error on a specific field,
 * to be appended as an extension to the RFC 7807 ProblemDetail.
 */
@Schema(description = "Details about a specific field validation failure.")
public record InvalidParam(
        @Schema(description = "The name of the field that failed validation.", example = "exam.name")
        String name,

        @Schema(description = "The validation error message explaining the rule violation.", example = "Exam name cannot be blank.")
        String reason
) {}
