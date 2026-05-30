package com.ia.project.dynamicstudyplanner.ga.fitness.constraint;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.springframework.stereotype.Component;

/**
 * Validates that the study plan meets the minimum day requirements for each subject.
 */
@Component
public class MinimumDaysConstraint implements ConstraintValidator {

    @Override
    public boolean isValid(StudyPlan plan, EvolutionContext context) {
        return plan.meetsMinimumConstraints(context.minimumDaysPerSubject());
    }
}
