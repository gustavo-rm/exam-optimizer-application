package com.ia.project.dynamicstudyplanner.api.dto;

/**
 * The top-level Data Transfer Object for the API's response after a successful optimization.
 * It provides a status message and the detailed optimization result.
 *
 * @param message A descriptive message indicating the outcome (e.g., "Plan generated successfully").
 * @param result The detailed result payload of the optimization.
 */
public record OptimizationResponse(
        String message,
        OptimizationResultDto result
) {}
