package com.ia.project.dynamicstudyplanner.api.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
/**
 * Data Transfer Object for a StudyPlan.
 * Represents the optimized allocation of study days in a JSON-friendly format.
 *
 * @param daysPerSubject A map where the key is the subject's name (String) and the value
 * is the number of days allocated to it.
 */
@Schema(description = "The core output of the Genetic Algorithm, representing the optimal "
        + "distribution of study days across subjects.")
public record StudyPlanDto(
        @Schema(description = "A mapping where keys are subject names and values are the optimal "
                + "number of days to study them.",
                example = "{\"Constitutional Law\": 10, \"Portuguese\": 5}")
        Map<String, Integer> daysPerSubject
) {}
