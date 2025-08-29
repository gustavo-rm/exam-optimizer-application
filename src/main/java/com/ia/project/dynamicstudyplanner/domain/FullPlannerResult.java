package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleResult;

/**
 * Encapsulates the complete, end-to-end result of the study planning process.
 * <p>
 * This object serves as a data carrier that aggregates both the strategic results from
 * the genetic algorithm ({@link OptimizationResult}) and the tactical daily schedule
 * ({@link ScheduleResult}). It represents the full output of the {@code DynamicStudyPlannerService}.
 */
public record FullPlannerResult(
        OptimizationResult optimizationResult,
        ScheduleResult scheduleResult
) {}