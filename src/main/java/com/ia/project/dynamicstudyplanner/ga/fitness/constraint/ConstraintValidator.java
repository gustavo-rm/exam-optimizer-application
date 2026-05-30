package com.ia.project.dynamicstudyplanner.ga.fitness.constraint;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * Validates hard constraints that an individual must meet.
 */
public interface ConstraintValidator {
    boolean isValid(StudyPlan plan, EvolutionContext context);
}
