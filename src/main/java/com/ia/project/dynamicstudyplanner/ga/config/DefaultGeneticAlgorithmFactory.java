package com.ia.project.dynamicstudyplanner.ga.config;

import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithmBuilder;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.CrossoverStrategy;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.MutationStrategy;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the GeneticAlgorithmFactory.
 * Responsible for assembling the GA strategies and returning a configured instance.
 * By injecting the strategies directly via Spring, we satisfy the Open-Closed Principle.
 */
@Component
public class DefaultGeneticAlgorithmFactory implements GeneticAlgorithmFactory {

    private final SelectionStrategy selectionStrategy;
    private final CrossoverStrategy crossoverStrategy;
    private final MutationStrategy mutationStrategy;

    public DefaultGeneticAlgorithmFactory(
            SelectionStrategy selectionStrategy,
            @Qualifier("hybridCrossover") CrossoverStrategy crossoverStrategy,
            @Qualifier("creepMutation") MutationStrategy mutationStrategy) {
        this.selectionStrategy = selectionStrategy;
        this.crossoverStrategy = crossoverStrategy;
        this.mutationStrategy = mutationStrategy;
    }

    @Override
    public GeneticAlgorithm create() {
        return new GeneticAlgorithmBuilder()
                .withSelectionStrategy(selectionStrategy)
                .withCrossoverStrategy(crossoverStrategy)
                .withMutationStrategy(mutationStrategy)
                .withElitism(true)
                .withCrossoverRate(0.95)
                .withMutationRate(0.05)
                .withStagnationPatience(25)
                .withHypermutationRate(0.20)
                .build();
    }
}
