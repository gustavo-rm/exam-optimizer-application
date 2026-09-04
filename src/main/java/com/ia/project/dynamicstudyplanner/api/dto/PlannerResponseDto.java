package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
/**
 * The top-level DTO for the API's response, containing the complete study plan.
 * This object aggregates both the strategic optimization results from the genetic
 * algorithm and the tactical, day-by-day study schedule.
 *
 * @param message A descriptive message indicating the outcome.
 * @param optimizationResult The strategic results from the GA (days per subject, fitness, etc.).
 * @param scheduleResult The tactical daily study schedule and its analysis.
 */
@Schema(description = "The comprehensive response containing both the strategic and tactical study plans.")
public record PlannerResponseDto(
        @Schema(description = "A status message summarizing the generation outcome.",
                example = "Full study plan generated successfully.")
        String message,
        @Schema(description = "The strategic output from the Genetic Algorithm (e.g., total days per subject).")
        OptimizationResultDto optimizationResult,
        @Schema(description = "The tactical, day-by-day study schedule based on the optimization result.")
        ScheduleResultDto scheduleResult
) {}
