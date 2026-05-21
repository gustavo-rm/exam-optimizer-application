package com.ia.project.dynamicstudyplanner.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.util.Map;

/**
 * DTO for the Student's Profile. Includes validation rules.
 *
 * @param name Student's name. Must not be blank.
 * @param knowledgeGaps Map of self-assessed knowledge gaps. Must not be empty.
 * @param weeklyAvailability Map of weekly study availability. Must not be empty.
 */
public record StudentProfileDto(
        @NotBlank(message = "Student name cannot be blank.")
        @Size(max = 100, message = "Student name cannot exceed 100 characters.")
        String name,

        @NotEmpty(message = "Knowledge gaps map cannot be empty.")
        @Size(max = 100, message = "Cannot specify more than 100 knowledge gaps.")
        Map<@NotBlank @Size(max = 100) String, @NotNull @DecimalMin("1.0") @DecimalMax("5.0") Double> knowledgeGaps,

        @NotEmpty(message = "Weekly availability map cannot be empty.")
        @Size(max = 7, message = "Weekly availability cannot have more than 7 days.")
        Map<@NotNull DayOfWeek, @NotNull @Min(0) @Max(24) Integer> weeklyAvailability
) {}
