package com.ia.project.dynamicstudyplanner.ga.config;

import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithmBuilder;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.CrossoverStrategy;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.MutationStrategy;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.TournamentSelection;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the GeneticAlgorithmFactory.
 * Responsible for assembling the GA strategies and returning a configured instance.
 * By making this a Spring Component, we invert the dependency from the service layer.
 */
@Component
public class DefaultGeneticAlgorithmFactory implements GeneticAlgorithmFactory {

    @Override
    public GeneticAlgorithm create() {
        // In a more complex scenario, these strategies could themselves be injected as beans.
        // For now, this isolates the instantiation logic away from the orchestration service.
        CrossoverStrategy weightedAverage = new WeightedAverageCrossover();
        CrossoverStrategy repairing = new RepairingCrossover();
        CrossoverStrategy hybridCrossover = new HybridCrossover(weightedAverage, repairing, 0.75);
        SelectionStrategy selection = new TournamentSelection(3);
        MutationStrategy mutation = new CreepMutation(3);

        return new GeneticAlgorithmBuilder()
                .withSelectionStrategy(selection)
                .withCrossoverStrategy(hybridCrossover)
                .withMutationStrategy(mutation)
                .withElitism(true)
                .withCrossoverRate(0.95)
                .withMutationRate(0.05)
                .withStagnationPatience(25)
                .withHypermutationRate(0.20)
                .build();
    }
}
