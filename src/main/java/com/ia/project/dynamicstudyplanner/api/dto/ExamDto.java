package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Data Transfer Object representing the official structure of the exam (e.g., from an edital).")
public record ExamDto(
        @Schema(description = "The official name of the exam.", example = "Federal Police Officer 2024")
        @NotBlank(message = "Exam name cannot be blank.")
        @Size(max = 200, message = "Exam name cannot exceed 200 characters.")
        String name,
        @Schema(description = "The scheduled date for the exam. Must be in the future.", example = "2024-12-01")
        @NotNull(message = "Exam date cannot be null.")
        @Future(message = "Exam date must be in the future.")
        LocalDate examDate,
        @Schema(description = "The total score available in the general knowledge section.", example = "50.0")
        @Min(value = 0, message = "Total score cannot be negative.")
        @DecimalMax(value = "1000.0", message = "Total score cannot exceed 1000.0.")
        double generalKnowledgeTotalScore,
        @Schema(description = "List of subjects evaluated in the general knowledge section.")
        @NotNull(message = "General knowledge subjects list cannot be null (can be empty).")
        @Size(max = 50, message = "Cannot have more than 50 general knowledge subjects.")
        @Valid
        List<@NotNull @Valid SubjectDto> generalKnowledgeSubjects,
        @Schema(description = "List of thematic axes, which group specific knowledge subjects and define their weights.")
        @NotNull(message = "Specific knowledge axes list cannot be null (can be empty).")
        @Size(max = 50, message = "Cannot have more than 50 specific knowledge axes.")
        @Valid
        List<@NotNull @Valid ThematicAxisDto> specificKnowledgeAxes
) {}
