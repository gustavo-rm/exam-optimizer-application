package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Data Transfer Object representing the student's personal constraints and knowledge state.")
public record StudentProfileDto(
        @Schema(description = "The name of the student.", example = "Jane Doe")
        @NotBlank(message = "Student name cannot be blank.")
        @Size(max = 100, message = "Student name cannot exceed 100 characters.")
        String name,
        @Schema(description = "A mapping of subject names to the student's self-assessed knowledge gap on a 1.0 (very strong) to 5.0 (very weak) scale.", example = "{\"Constitutional Law\": 4.5, \"Portuguese\": 2.0}")
        @NotEmpty(message = "Knowledge gaps map cannot be empty.")
        @Size(max = 100, message = "Cannot specify more than 100 knowledge gaps.")
        Map<@NotBlank @Size(max = 100) String, @NotNull @DecimalMin("1.0") @DecimalMax("5.0") Double> knowledgeGaps,
        @Schema(description = "A mapping of the days of the week to the number of hours the student is available to study on that day.", example = "{\"MONDAY\": 4, \"SATURDAY\": 8}")
        @NotEmpty(message = "Weekly availability map cannot be empty.")
        @Size(max = 7, message = "Weekly availability cannot have more than 7 days.")
        Map<@NotNull DayOfWeek, @NotNull @Min(0) @Max(24) Integer> weeklyAvailability
) {}
