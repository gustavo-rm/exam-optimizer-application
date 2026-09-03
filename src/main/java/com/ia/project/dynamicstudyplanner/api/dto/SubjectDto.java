package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/**
 * DTO for a Subject. Includes validation rules for API requests.
 *
 * @param name The name of the subject. Must not be null or blank, max 100 characters.
 * @param questionCount The number of questions. Must be between 1 and 500.
 * @param cognitiveLoad Intrinsic difficulty (1-5 scale).
 */
@Schema(description = "Data Transfer Object for a specific study subject within an exam.")
public record SubjectDto(
        @Schema(description = "The name of the subject.", example = "Constitutional Law")
        @NotBlank(message = "Subject name cannot be blank.")
        @Size(max = 100, message = "Subject name cannot exceed 100 characters.")
        String name,
        @Schema(description = "The number of questions expected for this subject on the exam.", example = "15")
        @Min(value = 1, message = "Question count must be at least 1.")
        @Max(value = 500, message = "Question count cannot exceed 500.")
        int questionCount,
        @Schema(description = "An intrinsic, objective difficulty score for this subject "
                + "(1=Very Easy, 5=Extremely Difficult), used for load balancing.",
                example = "4")
        @Min(value = 1, message = "Cognitive load must be at least 1.")
        @Max(value = 5, message = "Cognitive load must be at most 5.")
        int cognitiveLoad
) {}
