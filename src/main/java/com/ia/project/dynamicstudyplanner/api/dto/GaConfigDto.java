package com.ia.project.dynamicstudyplanner.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * DTO for the Genetic Algorithm's configuration. Includes validation rules to prevent
 * excessively large values that could cause CPU exhaustion (Denial of Service).
 *
 * @param totalStudyDays Total "ideal" days for the GA. Must be between 1 and 365 (1 year max).
 * @param numGenerations Number of generations to run. Must be between 10 and 1000.
 * @param populationSize Size of the population. Must be between 10 and 500.
 */
public record GaConfigDto(
        @Min(value = 1, message = "Total study days must be at least 1.")
        @Max(value = 365, message = "Total study days cannot exceed 365 days (1 year).")
        int totalStudyDays,

        @Min(value = 10, message = "Number of generations must be at least 10.")
        @Max(value = 1000, message = "Number of generations cannot exceed 1000 to prevent CPU exhaustion.")
        int numGenerations,

        @Min(value = 10, message = "Population size must be at least 10.")
        @Max(value = 500, message = "Population size cannot exceed 500 to prevent CPU exhaustion.")
        int populationSize
) {}