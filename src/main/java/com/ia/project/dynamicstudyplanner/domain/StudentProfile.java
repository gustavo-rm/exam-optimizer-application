package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.Map;

/**
 * Represents the profile of the student using the optimizer.
 * This class is an Entity that encapsulates all personal data relevant to creating a personalized study plan,
 * such as knowledge gaps and weekly availability.
 */
public class StudentProfile {

    private final String name;
    private final Map<Subject, Double> knowledgeGaps;
    private final Map<DayOfWeek, Integer> weeklyAvailability;

    public StudentProfile(String name, Map<Subject, Double> knowledgeGaps, Map<DayOfWeek, Integer> weeklyAvailability) {
        this.name = name;
        this.knowledgeGaps = knowledgeGaps == null ? Map.of() : Collections.unmodifiableMap(knowledgeGaps);
        this.weeklyAvailability = weeklyAvailability == null ? Map.of() : Collections.unmodifiableMap(weeklyAvailability);
    }

    public String getName() {
        return name;
    }

    public Map<DayOfWeek, Integer> getWeeklyAvailability() {
        return weeklyAvailability;
    }
    /**
     * Calculates the total number of study hours available per week.
     * @return The sum of all available hours for the week.
     */
    public int getTotalWeeklyHours() {
        if (weeklyAvailability == null || weeklyAvailability.isEmpty()) {
            return 0;
        }
        return weeklyAvailability.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Retrieves the specific knowledge gap factor for a given subject.
     * If no gap is specified, defaults to 1.0 (neutral).
     *
     * @param subject The subject to query.
     * @return The knowledge gap multiplier.
     */
    public double getKnowledgeGapFactor(Subject subject) {
        return knowledgeGaps.getOrDefault(subject, 1.0);
    }

    /**
     * Retrieves the average knowledge gap across all subjects declared by the student.
     *
     * @return The average gap factor, or 1.0 if empty.
     */
    public double getAverageKnowledgeGap() {
        if (knowledgeGaps.isEmpty()) {
            return 1.0;
        }
        return knowledgeGaps.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(1.0);
    }

    /**
     * Applies the subjective knowledge gap factor to an objective base importance score.
     * This brings the business logic of personalization directly into the domain entity.
     *
     * @param subject The subject being evaluated.
     * @param baseImportance The objective importance score from the exam.
     * @return The final, personalized importance score.
     */
    public double applyKnowledgeGapFactor(Subject subject, double baseImportance) {
        return baseImportance * getKnowledgeGapFactor(subject);
    }
}
