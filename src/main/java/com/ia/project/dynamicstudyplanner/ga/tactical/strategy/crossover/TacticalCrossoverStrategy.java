package com.ia.project.dynamicstudyplanner.ga.tactical.strategy.crossover;

import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * Interface for crossing over two TacticalStudyPlans.
 */
public interface TacticalCrossoverStrategy {
    TacticalStudyPlan crossover(TacticalStudyPlan parent1, TacticalStudyPlan parent2, double crossoverRate,
            EvolutionContext context);
}
