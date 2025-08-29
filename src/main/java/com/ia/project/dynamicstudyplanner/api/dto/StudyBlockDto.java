package com.ia.project.dynamicstudyplanner.api.dto;

/**
 * DTO for a StudyBlock.
 * Represents a scheduled block of study for a specific subject on a given day.
 *
 * @param subjectName The name of the subject to be studied.
 * @param hours       The number of hours allocated for this subject in this block.
 */
public record StudyBlockDto(
        String subjectName,
        int hours
) {}
