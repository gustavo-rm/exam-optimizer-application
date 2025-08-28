package com.ia.project.dynamicstudyplanner.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for the Exam structure. Includes validation rules.
 *
 * @param name Official name of the exam. Must not be blank.
 * @param examDate Date of the exam. Must be in the future.
 * @param generalKnowledgeTotalScore Total score for the General Knowledge section. Cannot be negative.
 * @param generalKnowledgeSubjects List of general knowledge subjects. Can be empty, but not null.
 * @param specificKnowledgeAxes List of specific knowledge axes. Can be empty, but not null.
 */
public record ExamDto(
        @NotBlank(message = "Exam name cannot be blank.")
        String name,

        @NotNull(message = "Exam date cannot be null.")
        @Future(message = "Exam date must be in the future.")
        LocalDate examDate,

        @Min(value = 0, message = "Total score cannot be negative.")
        double generalKnowledgeTotalScore,

        @NotNull(message = "General knowledge subjects list cannot be null (can be empty).")
        @Valid
        List<SubjectDto> generalKnowledgeSubjects,

        @NotNull(message = "Specific knowledge axes list cannot be null (can be empty).")
        @Valid
        List<ThematicAxisDto> specificKnowledgeAxes
) {}
