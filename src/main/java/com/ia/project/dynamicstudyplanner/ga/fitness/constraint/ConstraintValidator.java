package com.ia.project.dynamicstudyplanner.ga.fitness.constraint;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights;

/**
 * Validates hard constraints that an individual must meet.
 * <p>
 * See {@code docs/revisao-ag/05-fitness-function.md} for how constraint violations enter the
 * aggregated fitness and why they are subtracted rather than multiplied.
 */
public interface ConstraintValidator {

    boolean isValid(StudyPlan plan, EvolutionContext context);

    /**
     * How badly the plan violates this constraint, in [0,1]: 0 when satisfied, 1 at the worst
     * violation the constraint can express.
     * <p>
     * A graded severity exists so the optimizer can see which way is out. The previous design
     * multiplied the whole fitness by a flat 0.5 on any violation, which meant missing one subject's
     * floor by a single day cost exactly as much as missing every subject's by ten — no gradient to
     * follow back to feasibility, and a cost proportional to the plan's own quality rather than to
     * the damage (docs/revisao-ag/01-auditoria-fitness.md §2.2.3).
     * <p>
     * The default keeps the binary behaviour for constraints that have no natural magnitude.
     */
    default double violationSeverity(StudyPlan plan, EvolutionContext context) {
        return isValid(plan, context) ? 0.0 : 1.0;
    }

    /** Fitness subtracted per unit of severity. */
    default double getPenaltyWeight() {
        return FitnessWeights.CONSTRAINT_VIOLATION;
    }
}
