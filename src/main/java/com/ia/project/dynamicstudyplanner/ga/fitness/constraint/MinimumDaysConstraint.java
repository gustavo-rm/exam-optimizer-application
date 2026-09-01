package com.ia.project.dynamicstudyplanner.ga.fitness.constraint;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Validates that the study plan meets the minimum day requirements for each subject.
 * <p>
 * See {@code docs/revisao-ag/05-fitness-function.md} for where this sits in the aggregated fitness.
 * <p>
 * <b>This constraint does not fire in practice.</b> Every operator that can produce a plan —
 * {@code StudyPlanFactory}, both crossovers with their repair step, and both mutations — preserves
 * the floor by construction, so by induction the whole population is feasible in every generation
 * (proof by enumeration in docs/revisao-ag/01-auditoria-fitness.md §1.2). It is kept as a guard: if
 * a future operator ever breaks the invariant, this turns a silently invalid plan into a visible
 * fitness loss rather than a plan the student receives with subjects below their coverage floor.
 */
@Component
public class MinimumDaysConstraint implements ConstraintValidator {

    @Override
    public boolean isValid(StudyPlan plan, EvolutionContext context) {
        return plan.meetsMinimumConstraints(context.minimumDaysPerSubject());
    }

    /**
     * Total missing days as a fraction of the total floor.
     * <p>
     * A plan one day short on a single subject scores near zero severity; one that ignores the floor
     * entirely approaches 1. That gradient is what lets the search walk back to feasibility instead
     * of only learning that it is somewhere outside it.
     */
    @Override
    public double violationSeverity(StudyPlan plan, EvolutionContext context) {
        Map<Subject, Integer> minimums = context.minimumDaysPerSubject();
        if (minimums == null || minimums.isEmpty()) {
            return 0.0;
        }

        int missing = 0;
        int floor = 0;
        for (Map.Entry<Subject, Integer> entry : minimums.entrySet()) {
            int required = entry.getValue();
            floor += required;
            missing += Math.max(0, required - plan.getDaysForSubject(entry.getKey()));
        }

        return floor <= 0 ? 0.0 : Math.min(1.0, missing / (double) floor);
    }
}
