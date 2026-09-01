package com.ia.project.dynamicstudyplanner.ga.fitness.penalty;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * A multiplicative penalty for plans that are unusable rather than merely worse.
 * <p>
 * Reserved for the tactical path: these implementations need a {@code TacticalStudyPlan} to have
 * anything to measure, so in the macro path they all return 1.0 by design. Scoring that varies by
 * degree belongs in a {@code FitnessObjective} instead, which is additive and normalised.
 * <p>
 * See {@code docs/revisao-ag/05-fitness-function.md} for where penalties sit in the aggregation and
 * why the graded terms were moved out of here.
 */
public interface FitnessPenalty {
    /**
     * @return a multiplier between 0.0 and 1.0, where 1.0 means no penalty,
     * and lower values indicate a higher penalty.
     */
    double calculatePenaltyFactor(StudyPlan plan, EvolutionContext context);
}
