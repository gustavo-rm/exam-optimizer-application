package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
/**
 * Represents the top-level request body for the optimization endpoint.
 * Ensures that all nested objects are also validated.
 *
 * @param exam The exam structure. Cannot be null and will be validated.
 * @param studentProfile The student's profile. Cannot be null and will be validated.
 * @param gaConfig The GA configuration. Cannot be null and will be validated.
 */
@Schema(description = "The main request payload for generating an optimized study plan.")
public record OptimizationRequest(
        @Schema(description = "The structure and rules of the exam.")
        @NotNull(message = "Exam object cannot be null.")
        @Valid
        ExamDto exam,
        @Schema(description = "The personal profile of the student, including availability and knowledge gaps.")
        @NotNull(message = "Student profile object cannot be null.")
        @Valid
        StudentProfileDto studentProfile,
        @Schema(description = "Configuration parameters for the underlying Genetic Algorithm engine.")
        @NotNull(message = "GA config object cannot be null.")
        @Valid
        GaConfigDto gaConfig
) {}
