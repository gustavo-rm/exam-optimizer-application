package com.ia.project.dynamicstudyplanner.ga.strategy.selection;

import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.Population;

/**
 * A functional interface that defines the contract for a selection strategy.
 * A selection strategy is responsible for choosing an individual from a population
 * to be a parent for the next generation.
 */
@FunctionalInterface
public interface SelectionStrategy {
    /**
     * Selects an individual from the given population.
     * @param population The population to select from.
     * @return The selected Individual.
     */
    Individual select(Population population);
}
