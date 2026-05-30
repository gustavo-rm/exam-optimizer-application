package com.ia.project.dynamicstudyplanner.ga.fitness.penalty;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * Represents a penalty to apply when an individual violates soft constraints
 * or has undesirable traits (e.g., high fatigue).
 */
public interface FitnessPenalty {
    /**
     * @return a multiplier between 0.0 and 1.0, where 1.0 means no penalty,
     * and lower values indicate a higher penalty.
     */
    double calculatePenaltyFactor(StudyPlan plan, EvolutionContext context);
}
