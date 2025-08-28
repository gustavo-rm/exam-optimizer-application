package com.ia.project.dynamicstudyplanner.domain;

/**
 * A data transfer object (DTO) that encapsulates the final result of the optimization process.
 * This is an immutable value object, ensuring that the result data cannot be changed after creation.
 *
 * @param plan The best study plan found by the algorithm.
 * @param fitness The fitness score of the best plan. This represents the calculated "potential score".
 * @param generationsRun The total number of generations the algorithm ran for.
 * @param executionTimeMillis The total time in milliseconds the optimization process took to complete.
 */
public record OptimizationResult(
        StudyPlan plan,
        double fitness,
        int generationsRun,
        long executionTimeMillis
) {
    // A record automatically provides:
    // - A canonical constructor
    // - Accessor methods for each field (e.g., plan(), fitness())
    // - Implementations of equals(), hashCode(), and toString()
}
