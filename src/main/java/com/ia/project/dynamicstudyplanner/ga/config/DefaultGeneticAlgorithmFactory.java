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

    // Os valores abaixo sao os mesmos literais que create() passava ao construtor, apenas nomeados
    // e publicos, para que o log de parametros efetivos (Variante 1, D4) os LEIA em vez de repeti-los.
    // Nenhum valor, ordem de chamada ou sorteio mudou.

    /** Elitismo ligado. */
    public static final boolean ELITISM = true;

    /** Probabilidade de cruzamento por par de pais. */
    public static final double CROSSOVER_RATE = 0.95;

    /** Probabilidade de mutacao por gene. */
    public static final double MUTATION_RATE = 0.05;

    /** Geracoes sem melhora antes da hipermutacao. */
    public static final int STAGNATION_PATIENCE = 25;

    /** Taxa de mutacao aplicada durante a hipermutacao. */
    public static final double HYPERMUTATION_RATE = 0.20;

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
                .withElitism(ELITISM)
                .withCrossoverRate(CROSSOVER_RATE)
                .withMutationRate(MUTATION_RATE)
                .withStagnationPatience(STAGNATION_PATIENCE)
                .withHypermutationRate(HYPERMUTATION_RATE)
                .build();
    }
}
