package com.ia.project.dynamicstudyplanner.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for the Student's psychological, physical, and emotional state.
 *
 * @param stressLevel Stress level on a 1.0 (very low) to 5.0 (very high) scale.
 * @param fatigueLevel Fatigue level on a 1.0 (very low) to 5.0 (very high) scale.
 * @param motivationLevel Motivation level on a 1.0 (very low) to 5.0 (very high) scale.
 */
@Schema(description = "Data Transfer Object representing the student's psychological, physical, and emotional state.")
public record StudentStateDto(
        @Schema(description = "Stress level on a 1.0 (very low) to 5.0 (very high) scale.", example = "3.5")
        @NotNull(message = "Stress level cannot be null.")
        @DecimalMin(value = "1.0", message = "Stress level must be at least 1.0.")
        @DecimalMax(value = "5.0", message = "Stress level must be at most 5.0.")
        Double stressLevel,

        @Schema(description = "Fatigue level on a 1.0 (very low) to 5.0 (very high) scale.", example = "2.0")
        @NotNull(message = "Fatigue level cannot be null.")
        @DecimalMin(value = "1.0", message = "Fatigue level must be at least 1.0.")
        @DecimalMax(value = "5.0", message = "Fatigue level must be at most 5.0.")
        Double fatigueLevel,

        @Schema(description = "Motivation level on a 1.0 (very low) to 5.0 (very high) scale.", example = "4.0")
        @NotNull(message = "Motivation level cannot be null.")
        @DecimalMin(value = "1.0", message = "Motivation level must be at least 1.0.")
        @DecimalMax(value = "5.0", message = "Motivation level must be at most 5.0.")
        Double motivationLevel,

        @Schema(description = "The student's natural circadian rhythm.", example = "INTERMEDIATE")
        com.ia.project.dynamicstudyplanner.domain.Chronotype chronotype
) {}
