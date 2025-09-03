package com.ia.project.dynamicstudyplanner.service.calculation;

import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.springframework.stereotype.Service;

/**
 * Calculates the maximum sustainable daily cognitive load for a student.
 * <p>
 * This calculator uses a heuristic model to estimate a student's "mental energy budget,"
 * removing the need for subjective user input and making the system more intelligent
 * and self-calibrating.
 */
@Service
public final class CognitiveLoadCalculator {

    // Assumes the average cognitive load of a typical subject is 3 out of 5.
    private static final double AVERAGE_LOAD_FACTOR = 3.0;

    /**
     * Calculates the personalized MAX_DAILY_COGNITIVE_LOAD for a student.
     * The final value is an integer representing the estimated maximum daily cognitive load.
     *
     * @param profile The student's profile, containing their availability and knowledge gaps.
     * @param exam    The exam, containing the intrinsic difficulty of the subjects.
     * @return An integer value representing the estimated maximum daily cognitive load.
     */
    public int calculate(StudentProfile profile, Exam exam) {
        // Factor 1: Calculate the base capacity from the student's available hours.
        double averageDailyHours = profile.getTotalWeeklyHours() / 7.0;
        double baseCapacity = averageDailyHours * AVERAGE_LOAD_FACTOR;

        // Factor 2: Calculate a fatigue factor based on the student's self-assessed confidence.
        double fatigueFactor = calculateFatigueFactor(profile);

        // Factor 3: Calculate a pressure factor based on the overall difficulty of the exam.
        double pressureFactor = calculatePressureFactor(exam);

        // The final load is the base capacity, adjusted by the fatigue and pressure factors.
        double finalCalculatedLoad = baseCapacity * fatigueFactor * pressureFactor;

        // Round the result and ensure a minimum value to avoid a load of zero.
        return Math.max(5, (int) Math.round(finalCalculatedLoad));
    }

    /**
     * Calculates a multiplier that represents the student's fatigue.
     * Students who feel less prepared (higher average gap) will tire more quickly (factor < 1.0).
     *
     * @param profile The student's profile.
     * @return A multiplier between approximately 0.8 and 1.1.
     */
    private double calculateFatigueFactor(StudentProfile profile) {
        double averageGap = profile.knowledgeGaps().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(3.0); // Assume an average gap if the map is empty.

        // Linearly maps the gap (1-5 scale) to a factor (1.1 - 0.8).
        // 1.1 -> Very confident, can handle more | 0.8 -> Not confident, can handle less.
        return 1.1 - ((averageGap - 1) / 4.0) * 0.3;
    }

    /**
     * Calculates a multiplier that represents the overall difficulty of the exam.
     * Exams with intrinsically harder subjects (higher average cognitive load) are more draining.
     *
     * @param exam The exam object.
     * @return A multiplier between approximately 0.9 and 1.1.
     */
    private double calculatePressureFactor(Exam exam) {
        double averageCognitiveLoad = exam.getAllSubjects().stream()
                .mapToInt(Subject::cognitiveLoad)
                .average()
                .orElse(3.0); // Assume an average load if the list is empty.

        // Linearly maps the load (1-5 scale) to a factor (1.1 - 0.9).
        return 1.1 - ((averageCognitiveLoad - 1) / 4.0) * 0.2;
    }
}
