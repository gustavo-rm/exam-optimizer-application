package com.ia.project.dynamicstudyplanner.api.dto;

import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * DTO for a ScheduleResult.
 * Contains the full tactical schedule and the analysis of its viability.
 *
 * @param schedule The generated day-by-day study schedule, with dates as keys.
 * @param status The outcome status of the generation (e.g., SUCCESS, WARNING_TIME_DEFICIT).
 * @param requiredHours The total hours required by the ideal strategic plan.
 * @param availableHours The total hours the student has available until the exam.
 */
public record ScheduleResultDto(
        Map<LocalDate, List<StudyBlockDto>> schedule,
        ScheduleStatus status,
        double requiredHours,
        double availableHours
) {
    public ScheduleResultDto(Map<LocalDate, List<StudyBlockDto>> schedule, ScheduleStatus status, double requiredHours, double availableHours) {
        this.schedule = new HashMap<>(schedule);
        this.status = status;
        this.requiredHours = requiredHours;
        this.availableHours = availableHours;
    }

    @Override
    public Map<LocalDate, List<StudyBlockDto>> schedule() {
        return new HashMap<>(this.schedule);
    }
}
