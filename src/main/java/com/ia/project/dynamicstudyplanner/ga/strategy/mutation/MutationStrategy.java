package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;

import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;

/**
 * Defines the contract for a mutation strategy.
 * Mutation introduces small, random changes into an individual's chromosome
 * to maintain genetic diversity in the population.
 */
@FunctionalInterface
public interface MutationStrategy {
    /**
     * Applies mutation to an individual.
     * @param individual The individual to mutate.
     * @param mutationRate The probability of mutation occurring.
     * @return The mutated Individual. It might be the same individual if no mutation occurred.
     */
    Individual mutate(Individual individual, double mutationRate, EvolutionContext context);
}
