package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.Collections;
import java.util.Map;

/**
 * Represents the chromosome: a complete allocation of study days for each subject.
 * This class is an immutable value object. Once a StudyPlan is created, it cannot be changed,
 * which ensures the integrity of the genetic algorithm's solutions.
 *
 * @param daysPerSubject A map where each Subject is associated with the total number of days
 * allocated for its study.
 */
public record StudyPlan(Map<Subject, Integer> daysPerSubject) {

    /**
     * The canonical constructor for StudyPlan.
     * It ensures that the map provided is wrapped in an unmodifiable view to
     * guarantee true immutability.
     *
     * @param daysPerSubject The map of subjects to allocated study days.
     */
    public StudyPlan(Map<Subject, Integer> daysPerSubject) {
        // We wrap the map to make it unmodifiable, ensuring the record is deeply immutable.
        this.daysPerSubject = Collections.unmodifiableMap(daysPerSubject);
    }

    /**
     * Retrieves the number of days allocated to a specific subject.
     *
     * @param subject The subject to query.
     * @return The number of allocated days, or 0 if the subject is not in the plan.
     */
    public int getDaysForSubject(Subject subject) {
        return this.daysPerSubject.getOrDefault(subject, 0);
    }

    /**
     * Calculates the total number of days in the entire study plan.
     *
     * @return The sum of all allocated days across all subjects.
     */
    public int getTotalDays() {
        return this.daysPerSubject.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }
}
