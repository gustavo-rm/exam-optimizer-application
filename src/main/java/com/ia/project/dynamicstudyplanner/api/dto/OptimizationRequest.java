package com.ia.project.dynamicstudyplanner.api.dto;

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
public record OptimizationRequest(
        @NotNull(message = "Exam object cannot be null.")
        @Valid
        ExamDto exam,

        @NotNull(message = "Student profile object cannot be null.")
        @Valid
        StudentProfileDto studentProfile,

        @NotNull(message = "GA config object cannot be null.")
        @Valid
        GaConfigDto gaConfig
) {}