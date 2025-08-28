package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Represents the profile of the student using the optimizer.
 * This class encapsulates all personal data relevant to creating a personalized study plan,
 * such as knowledge gaps and weekly availability.
 *
 * @param name The student's name, for identification.
 * @param knowledgeGaps A map where each Subject is associated with a self-assessed knowledge gap score
 * (e.g., 1.0 = very strong, 5.0 = very weak).
 * @param weeklyAvailability A map detailing how many hours the student can study on each day of the week.
 * This allows for more granular and realistic planning.
 */
public record StudentProfile(
        String name,
        Map<Subject, Double> knowledgeGaps,
        Map<DayOfWeek, Integer> weeklyAvailability
) {
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
}
