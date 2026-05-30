package com.ia.project.dynamicstudyplanner.service.calculation.retention;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.SubjectRetentionState;

import java.time.LocalDate;

/**
 * Interface defining the cognitive science algorithms for memory retention.
 */
public interface RetentionAlgorithm {

    /**
     * Calculates the probability of recalling the subject's material on a specific date.
     * Uses the Ebbinghaus Forgetting Curve formula.
     *
     * @return Probability between 0.0 and 1.0.
     */
    double calculateRetentionProbability(SubjectRetentionState state, LocalDate targetDate);

    /**
     * Determines if a spaced repetition review is mandatory on or before the target date.
     */
    boolean isReviewMandatory(Subject subject, SubjectRetentionState state, LocalDate targetDate);

    /**
     * Calculates the new memory state after a study/review session.
     *
     * @param currentState The memory state prior to the review.
     * @param reviewDate The date the review occurred.
     * @param performanceGrade Subjective grade of performance (0 to 5) similar to SM-2.
     * @return The updated memory state.
     */
    SubjectRetentionState processReview(SubjectRetentionState currentState, LocalDate reviewDate, int performanceGrade);
}
