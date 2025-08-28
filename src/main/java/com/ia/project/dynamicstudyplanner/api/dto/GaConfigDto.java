package com.ia.project.dynamicstudyplanner.api.dto;

import jakarta.validation.constraints.Min;

/**
 * DTO for the Genetic Algorithm's configuration. Includes validation rules.
 *
 * @param totalStudyDays Total "ideal" days for the GA. Must be at least 1.
 * @param numGenerations Number of generations to run. Must be at least 10.
 * @param populationSize Size of the population. Must be at least 10.
 */
public record GaConfigDto(
        @Min(value = 1, message = "Total study days must be at least 1.")
        int totalStudyDays,

        @Min(value = 10, message = "Number of generations must be at least 10.")
        int numGenerations,

        @Min(value = 10, message = "Population size must be at least 10.")
        int populationSize
) {}