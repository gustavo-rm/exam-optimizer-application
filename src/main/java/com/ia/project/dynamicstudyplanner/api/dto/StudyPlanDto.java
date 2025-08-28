package com.ia.project.dynamicstudyplanner.api.dto;

import java.util.Map;

/**
 * Data Transfer Object for a StudyPlan.
 * Represents the optimized allocation of study days in a JSON-friendly format.
 *
 * @param daysPerSubject A map where the key is the subject's name (String) and the value
 * is the number of days allocated to it.
 */
public record StudyPlanDto(
        Map<String, Integer> daysPerSubject
) {}
