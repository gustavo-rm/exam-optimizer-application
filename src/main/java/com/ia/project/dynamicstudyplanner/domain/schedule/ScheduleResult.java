package com.ia.project.dynamicstudyplanner.domain.schedule;

import com.ia.project.dynamicstudyplanner.domain.StudyBlock;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the complete result of the schedule generation process.
 *
 * @param schedule The generated day-by-day study schedule.
 * @param status The status indicating the outcome of the generation.
 * @param requiredHours The total hours required by the ideal study plan.
 * @param availableHours The total hours available, according to the student's profile and date range.
 */
public record ScheduleResult(
        Map<LocalDate, List<StudyBlock>> schedule,
        ScheduleStatus status,
        double requiredHours,
        double availableHours
) {}
