package com.ia.project.dynamicstudyplanner.ga.fitness.objective;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * Represents a specific objective to maximize or evaluate as part of a MOOP.
 */
public interface FitnessObjective {
    double calculateReward(StudyPlan plan, EvolutionContext context);
    double getWeight();
}
