package com.ia.project.dynamicstudyplanner.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for a Subject. Includes validation rules for API requests.
 *
 * @param name The name of the subject. Must not be null or blank.
 * @param questionCount The number of questions. Must be at least 1.
 */
public record SubjectDto(
        @NotBlank(message = "Subject name cannot be blank.")
        String name,

        @Min(value = 1, message = "Question count must be at least 1.")
        int questionCount,

        @Min(value = 1, message = "Cognitive load must be at least 1.")
        @Max(value = 5, message = "Cognitive load must be at most 5.")
        int cognitiveLoad
) {}
