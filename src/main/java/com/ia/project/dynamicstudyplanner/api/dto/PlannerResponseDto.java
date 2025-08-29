package com.ia.project.dynamicstudyplanner.api.dto;

/**
 * The top-level DTO for the API's response, containing the complete study plan.
 * This object aggregates both the strategic optimization results from the genetic
 * algorithm and the tactical, day-by-day study schedule.
 *
 * @param message A descriptive message indicating the outcome.
 * @param optimizationResult The strategic results from the GA (days per subject, fitness, etc.).
 * @param scheduleResult The tactical daily study schedule and its analysis.
 */
public record PlannerResponseDto(
        String message,
        OptimizationResultDto optimizationResult,
        ScheduleResultDto scheduleResult
) {}