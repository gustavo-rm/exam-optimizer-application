package com.ia.project.dynamicstudyplanner.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * DTO for a ThematicAxis. Includes validation rules.
 *
 * @param id Unique identifier for the axis. Must be at least 1.
 * @param name Descriptive name of the axis. Must not be blank, max 100 characters.
 * @param weight Weight of the axis. Must be a positive value, max 100.
 * @param subjects List of subjects in this axis. Must not be empty, max 50.
 */
public record ThematicAxisDto(
        @Min(value = 1, message = "Axis ID must be at least 1.")
        int id,

        @NotBlank(message = "Axis name cannot be blank.")
        @Size(max = 100, message = "Axis name cannot exceed 100 characters.")
        String name,

        @Positive(message = "Axis weight must be positive.")
        @DecimalMax(value = "100.0", message = "Axis weight cannot exceed 100.0.")
        double weight,

        @NotEmpty(message = "Specific knowledge axis must have at least one subject.")
        @Size(max = 50, message = "Specific knowledge axis cannot have more than 50 subjects.")
        @Valid // Crucial: Triggers validation for each SubjectDto in the list
        List<@NotNull @Valid SubjectDto> subjects
) {}

