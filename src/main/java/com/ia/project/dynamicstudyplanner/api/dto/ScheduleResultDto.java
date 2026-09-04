package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
/**
 * DTO for a ScheduleResult.
 * Contains the full tactical schedule and the analysis of its viability.
 *
 * @param schedule The generated day-by-day study schedule, with dates as keys.
 * @param status The outcome status of the generation (e.g., SUCCESS, WARNING_TIME_DEFICIT).
 * @param requiredHours The total hours required by the ideal strategic plan.
 * @param availableHours The total hours the student has available until the exam.
 */
@Schema(description = "The tactical, day-by-day scheduling result based on the optimized plan "
        + "and student availability.")
public record ScheduleResultDto(
        @Schema(description = "A mapping of specific dates to a list of study blocks scheduled for that day.",
                example = "{\"2024-10-15\": [{\"subjectName\": \"Constitutional Law\", \"hours\": 2}]}")
        Map<LocalDate, List<StudyBlockDto>> schedule,
        @Schema(description = "An indicator of whether the ideal plan was fully scheduled, or if there was a time " +
                "deficit/surplus.",
                example = "SUCCESS_IDEAL_PLAN")
        ScheduleStatus status,
        @Schema(description = "The total number of hours required to complete the ideal study plan.", example = "120.0")
        double requiredHours,
        @Schema(description = "The total number of hours the student has available before the exam.", example = "150.0")
        double availableHours
) {}
