package com.ia.project.dynamicstudyplanner.api.dto;

import java.util.List;
import java.util.ArrayList;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * DTO for a ThematicAxis. Includes validation rules.
 *
 * @param id Unique identifier for the axis. Must be at least 1.
 * @param name Descriptive name of the axis. Must not be blank.
 * @param weight Weight of the axis. Must be a positive value.
 * @param subjects List of subjects in this axis. Must not be empty, and its items will be validated.
 */
public record ThematicAxisDto(
        @Min(value = 1, message = "Axis ID must be at least 1.")
        int id,

        @NotBlank(message = "Axis name cannot be blank.")
        String name,

        @Positive(message = "Axis weight cannot be negative.")
        double weight,

        @NotEmpty(message = "Specific knowledge axis must have at least one subject.")
        @Valid // Crucial: Aciona a validação para cada SubjectDto na lista
        List<SubjectDto> subjects
) {
    public ThematicAxisDto(int id, String name, double weight, List<SubjectDto> subjects) {
        this.id = id;
        this.name = name;
        this.weight = weight;
        this.subjects = new ArrayList<>(subjects);
    }

    @Override
    public List<SubjectDto> subjects() {
        return new ArrayList<>(this.subjects);
    }
}
