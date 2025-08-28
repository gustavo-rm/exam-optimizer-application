package com.ia.project.dynamicstudyplanner.ga.strategy.crossover;

import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;

/**
 * Defines the contract for a crossover (or recombination) strategy.
 * Crossover combines the genetic information of two parents to create a child.
 */
@FunctionalInterface
public interface CrossoverStrategy {
    /**
     * Performs crossover on two parent individuals to produce a child.
     *
     * @param parent1 The first parent.
     * @param parent2 The second parent.
     * @param crossoverRate The probability that crossover will occur.
     * @return A new Individual (the child).
     */
    Individual crossover(Individual parent1, Individual parent2, double crossoverRate, EvolutionContext context);
}
