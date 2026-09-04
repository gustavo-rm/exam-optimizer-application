package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.CrossoverStrategy;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.MutationStrategy;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;

import java.util.Objects;

/**
 * Implements the Builder design pattern for creating instances of GeneticAlgorithm.
 * This pattern simplifies the construction of complex objects, allowing for a readable,
 * step-by-step configuration, including adaptive parameters for stagnation handling.
 */
public class GeneticAlgorithmBuilder {

    // --- Core Strategy Parameters ---
    private SelectionStrategy selectionStrategy;
    private CrossoverStrategy crossoverStrategy;
    private MutationStrategy mutationStrategy;

    // --- Core GA Parameters ---
    private double crossoverRate = 0.9;
    private double mutationRate = 0.05;
    private boolean elitism = true;

    // --- Adaptive Strategy Parameters ---
    private int stagnationPatience = 30; // Default patience of 30 generations
    private double hypermutationRate = 0.25; // Default hypermutation rate of 25%

    /**
     * Sets the selection strategy to be used by the algorithm.
     * This is a required parameter.
     *
     * @param strategy The implementation of the selection strategy (e.g., TournamentSelection).
     * @return The builder instance itself to allow for method chaining.
     */
    public GeneticAlgorithmBuilder withSelectionStrategy(SelectionStrategy strategy) {
        this.selectionStrategy = strategy;
        return this;
    }

    /**
     * Sets the crossover (recombination) strategy to be used by the algorithm.
     * This is a required parameter.
     *
     * @param strategy The implementation of the crossover strategy (e.g., HybridCrossover).
     * @return The builder instance itself to allow for method chaining.
     */
    public GeneticAlgorithmBuilder withCrossoverStrategy(CrossoverStrategy strategy) {
        this.crossoverStrategy = strategy;
        return this;
    }

    /**
     * Sets the mutation strategy to be used by the algorithm.
     * This is a required parameter.
     *
     * @param strategy The implementation of the mutation strategy (e.g., SwapMutation).
     * @return The builder instance itself to allow for method chaining.
     */
    public GeneticAlgorithmBuilder withMutationStrategy(MutationStrategy strategy) {
        this.mutationStrategy = strategy;
        return this;
    }

    /**
     * Sets the crossover rate, which is the probability of two parents recombining.
     * The value must be between 0.0 and 1.0.
     *
     * @param rate The crossover probability (e.g., 0.9 for 90%).
     * @return The builder instance itself to allow for method chaining.
     */
    public GeneticAlgorithmBuilder withCrossoverRate(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Crossover rate must be between 0.0 and 1.0.");
        }
        this.crossoverRate = rate;
        return this;
    }

    /**
     * Sets the base mutation rate, which is the probability of an individual undergoing mutation
     * during normal evolution. The value must be between 0.0 and 1.0.
     *
     * @param rate The mutation probability (e.g., 0.05 for 5%).
     * @return The builder instance itself to allow for method chaining.
     */
    public GeneticAlgorithmBuilder withMutationRate(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Mutation rate must be between 0.0 and 1.0.");
        }
        this.mutationRate = rate;
        return this;
    }

    /**
     * Sets whether elitism should be applied. If true, the best individual from one
     * generation is guaranteed to pass to the next generation unmodified.
     *
     * @param elitism 'true' to enable elitism, 'false' to disable it.
     * @return The builder instance itself to allow for method chaining.
     */
    public GeneticAlgorithmBuilder withElitism(boolean elitism) {
        this.elitism = elitism;
        return this;
    }

    /**
     * Sets the number of generations without improvement in the best fitness before
     * triggering an adaptive stagnation escape strategy (e.g., hypermutation).
     *
     * @param generations The number of generations of patience.
     * @return The builder instance itself to allow for method chaining.
     */
    public GeneticAlgorithmBuilder withStagnationPatience(int generations) {
        if (generations < 1) {
            throw new IllegalArgumentException("Stagnation patience must be at least 1 generation.");
        }
        this.stagnationPatience = generations;
        return this;
    }

    /**
     * Sets the mutation rate to be used during a hypermutation event, which is
     * triggered to escape stagnation.
     *
     * @param rate The hypermutation rate (e.g., 0.25 for 25%).
     * @return The builder instance itself to allow for method chaining.
     */
    public GeneticAlgorithmBuilder withHypermutationRate(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Hypermutation rate must be between 0.0 and 1.0.");
        }
        this.hypermutationRate = rate;
        return this;
    }

    /**
     * Builds and returns an instance of GeneticAlgorithm with the configured parameters.
     * Before construction, this method validates that all required parameters have been provided.
     *
     * @return A new instance of GeneticAlgorithm, ready for use.
     * @throws IllegalStateException if any of the required strategies are not set.
     */
    public GeneticAlgorithm build() {
        Objects.requireNonNull(selectionStrategy, "Selection strategy cannot be null. Use withSelectionStrategy().");
        Objects.requireNonNull(crossoverStrategy, "Crossover strategy cannot be null. Use withCrossoverStrategy().");
        Objects.requireNonNull(mutationStrategy, "Mutation strategy cannot be null. Use withMutationStrategy().");

        // Calls the constructor of GeneticAlgorithm, passing all configured parameters
        return new GeneticAlgorithm(
                selectionStrategy,
                crossoverStrategy,
                mutationStrategy,
                crossoverRate,
                mutationRate,
                elitism,
                stagnationPatience,
                hypermutationRate
        );
    }
}
