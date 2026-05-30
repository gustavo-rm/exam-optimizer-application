package com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * Interface for mutating a TacticalStudyPlan.
 * Mutations may temporarily break complex constraints (like energy budgets),
 * which will be resolved by the ChromosomeRepairer.
 */
public interface TacticalMutationStrategy {
    TacticalStudyPlan mutate(TacticalStudyPlan plan, double mutationRate, EvolutionContext context);
}
