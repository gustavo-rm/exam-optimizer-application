package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
/**
 * DTO for a StudyBlock.
 * Represents a scheduled block of study for a specific subject on a given day.
 *
 * @param subjectName The name of the subject to be studied.
 * @param hours       The number of hours allocated for this subject in this block.
 */
@Schema(description = "Represents a distinct, contiguous block of study time allocated to a specific subject.")
public record StudyBlockDto(
        @Schema(description = "The name of the subject to study in this block.", example = "Constitutional Law")
        String subjectName,
        @Schema(description = "The number of hours allocated to this block.", example = "2")
        int hours
) {}
