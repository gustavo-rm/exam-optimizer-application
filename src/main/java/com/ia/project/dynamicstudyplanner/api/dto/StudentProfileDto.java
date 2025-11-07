package com.ia.project.dynamicstudyplanner.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.HashMap;

/**
 * DTO for the Student's Profile. Includes validation rules.
 *
 * @param name Student's name. Must not be blank.
 * @param knowledgeGaps Map of self-assessed knowledge gaps. Must not be empty.
 * @param weeklyAvailability Map of weekly study availability. Must not be empty.
 */
public record StudentProfileDto(
        @NotBlank(message = "Student name cannot be blank.")
        String name,

        @NotEmpty(message = "Knowledge gaps map cannot be empty.")
        Map<String, Double> knowledgeGaps,

        @NotEmpty(message = "Weekly availability map cannot be empty.")
        Map<DayOfWeek, Integer> weeklyAvailability
) {
    public StudentProfileDto(String name, Map<String, Double> knowledgeGaps, Map<DayOfWeek, Integer> weeklyAvailability) {
        this.name = name;
        this.knowledgeGaps = new HashMap<>(knowledgeGaps);
        this.weeklyAvailability = new HashMap<>(weeklyAvailability);
    }

    @Override
    public Map<String, Double> knowledgeGaps() {
        return new HashMap<>(this.knowledgeGaps);
    }

    @Override
    public Map<DayOfWeek, Integer> weeklyAvailability() {
        return new HashMap<>(this.weeklyAvailability);
    }
}
