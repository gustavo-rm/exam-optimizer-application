package com.ia.project.dynamicstudyplanner.api.dto;

/**
 * Data Transfer Object for an OptimizationResult.
 * Contains the detailed results of the genetic algorithm's execution.
 *
 * @param plan The optimized study plan.
 * @param fitness The final fitness score of the best plan found.
 * @param generationsRun The total number of generations the algorithm ran for.
 * @param executionTimeMillis The total time in milliseconds the optimization process took.
 */
public record OptimizationResultDto(
        StudyPlanDto plan,
        double fitness,
        int generationsRun,
        long executionTimeMillis
) {}
