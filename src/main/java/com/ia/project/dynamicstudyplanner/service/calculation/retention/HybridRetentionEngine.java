package com.ia.project.dynamicstudyplanner.service.calculation.retention;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.retention.SubjectRetentionState;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A hybrid retention engine that uses SM-2 heuristics for interval scaling
 * and the Ebbinghaus forgetting curve for retention probability modeling.
 */
@Service
public class HybridRetentionEngine implements RetentionAlgorithm {

    /**
     * Recall probability below which a review becomes mandatory.
     * <p>
     * Set to {@code e^-1}, the value the forgetting curve {@code R = e^(-t/S)} reaches after
     * exactly one stability interval. That makes the two halves of this "hybrid" agree: SM-2
     * schedules the next review at {@code t = S}, and the Ebbinghaus threshold now fires at the
     * same moment.
     * <p>
     * It was 0.85, which is <b>not</b> consistent with the SM-2 half. Solving
     * {@code e^(-t/S) <= 0.85} gives {@code t >= 0.1625 * S}: the engine declared a review overdue
     * after 16% of the interval it had just scheduled — for a 6-day SM-2 interval, mandatory the
     * next day, a disagreement of roughly 6x across the whole domain. Derivation in
     * {@code docs/revisao-ag/01-auditoria-fitness.md} Apendice C; consequences for the fitness in
     * {@code docs/revisao-ag/05-fitness-function.md}.
     */
    public static final double MANDATORY_REVIEW_THRESHOLD = Math.exp(-1.0);

    @Override
    public double calculateRetentionProbability(SubjectRetentionState state, LocalDate targetDate) {
        if (state == null || state.getLastReviewDate() == null) {
            return 0.0; // No prior knowledge recorded
        }

        long daysSinceReview = ChronoUnit.DAYS.between(state.getLastReviewDate(), targetDate);
        if (daysSinceReview < 0) {
            return 1.0; // Target date is in the past or today
        }

        // Forgetting Curve: R = e^(-t/S)
        // Where S (Stability) is represented by currentIntervalDays
        return Math.exp(-((double) daysSinceReview) / state.getCurrentIntervalDays());
    }

    @Override
    public boolean isReviewMandatory(Subject subject, SubjectRetentionState state, LocalDate targetDate) {
        if (state == null) {
            return true; // Never studied, must be studied/reviewed.
        }

        double probability = calculateRetentionProbability(state, targetDate);
        return probability <= MANDATORY_REVIEW_THRESHOLD;
    }

    @Override
    public SubjectRetentionState processReview(SubjectRetentionState currentState, LocalDate reviewDate, int performanceGrade) {
        // SM-2 Algorithm adaptation

        int nextRepetitionCount;
        double nextIntervalDays;
        double nextEasinessFactor = currentState.getEasinessFactor();

        if (performanceGrade >= 3) {
            // Successful recall
            if (currentState.getRepetitionCount() == 0) {
                nextIntervalDays = 1.0;
            } else if (currentState.getRepetitionCount() == 1) {
                nextIntervalDays = 6.0;
            } else {
                nextIntervalDays = currentState.getCurrentIntervalDays() * currentState.getEasinessFactor();
            }
            nextRepetitionCount = currentState.getRepetitionCount() + 1;
        } else {
            // Failed recall
            nextRepetitionCount = 0;
            nextIntervalDays = 1.0;
        }

        // Update Easiness Factor: EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        nextEasinessFactor = nextEasinessFactor + (0.1 - (5 - performanceGrade) * (0.08 + (5 - performanceGrade) * 0.02));
        if (nextEasinessFactor < 1.3) {
            nextEasinessFactor = 1.3; // Hard floor to prevent infinite loops of short intervals
        }

        return new SubjectRetentionState(nextRepetitionCount, nextEasinessFactor, nextIntervalDays, reviewDate);
    }
}
