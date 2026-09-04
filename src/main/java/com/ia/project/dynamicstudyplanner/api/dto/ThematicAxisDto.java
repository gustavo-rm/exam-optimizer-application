package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
/**
 * DTO for a ThematicAxis. Includes validation rules.
 *
 * @param id Unique identifier for the axis. Must be at least 1.
 * @param name Descriptive name of the axis. Must not be blank, max 100 characters.
 * @param weight Weight of the axis. Must be a positive value, max 100.
 * @param subjects List of subjects in this axis. Must not be empty, max 50.
 */
@Schema(description = "Data Transfer Object grouping related specific knowledge subjects "
        + "into an axis with a shared weight.")
public record ThematicAxisDto(
        @Schema(description = "A unique identifier for the thematic axis.", example = "1")
        @Min(value = 1, message = "Axis ID must be at least 1.")
        int id,
        @Schema(description = "A descriptive name for the axis.", example = "Information Technology")
        @NotBlank(message = "Axis name cannot be blank.")
        @Size(max = 100, message = "Axis name cannot exceed 100 characters.")
        String name,
        @Schema(description = "The multiplier weight applied to all questions within this axis.", example = "2.5")
        @Positive(message = "Axis weight must be positive.")
        @DecimalMax(value = "100.0", message = "Axis weight cannot exceed 100.0.")
        double weight,
        @Schema(description = "The list of subjects belonging to this thematic axis.")
        @NotEmpty(message = "Specific knowledge axis must have at least one subject.")
        @Size(max = 50, message = "Specific knowledge axis cannot have more than 50 subjects.")
        @Valid // Crucial: Triggers validation for each SubjectDto in the list
        List<@NotNull @Valid SubjectDto> subjects
) {}
