package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithmBuilder;
import com.ia.project.dynamicstudyplanner.ga.Population;
import com.ia.project.dynamicstudyplanner.ga.generator.DefaultPopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.TournamentSelection;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Runs the production GA with hyperparameters supplied per call, for the sensitivity sweep.
 * <p>
 * {@code StudyOptimizerService} always builds its engine through {@code DefaultGeneticAlgorithmFactory},
 * whose crossover rate, mutation rate, stagnation patience and hypermutation rate are hardcoded
 * (docs/revisao-ag/00-diagnostico.md §4.2). Varying them therefore requires assembling the engine
 * directly. This runner mirrors {@code DefaultGeneticAlgorithmFactory} and
 * {@code StudyOptimizerService.runEvolution} exactly, deviating <b>only</b> in the parameters passed
 * to {@link #run}; every production default is reproduced verbatim below so the sweep measures the
 * effect of the parameter and nothing else.
 */
public final class ConfigurableGaRunner {

    /** Production defaults from DefaultGeneticAlgorithmFactory, reproduced for the sweep baseline. */
    public static final double PROD_CROSSOVER_RATE = 0.95;
    public static final double PROD_MUTATION_RATE = 0.05;
    public static final int PROD_STAGNATION_PATIENCE = 25;
    public static final double PROD_HYPERMUTATION_RATE = 0.20;

    private final DefaultPopulationGenerator populationGenerator = new DefaultPopulationGenerator();

    /** Outcome of one configured run. */
    public record Result(StudyPlan plan, double fitness, long elapsedMillis) {
    }

    /**
     * Runs one evolution with the given configuration.
     *
     * @param instance       problem to solve; its own populationSize/numGenerations are overridden
     * @param context        evolution context built by the harness
     * @param populationSize population size to test
     * @param generations    generation count to test
     * @param mutationRate   base mutation rate to test
     * @param seed           seed for everything routed through {@code RandomProvider}
     */
    public Result run(BenchmarkInstance instance, EvolutionContext context,
                      int populationSize, int generations, double mutationRate, long seed) {

        RandomProvider.setInstance(new Random(seed));
        try {
            GeneticAlgorithm ga = new GeneticAlgorithmBuilder()
                    .withSelectionStrategy(new TournamentSelection())
                    .withCrossoverStrategy(new HybridCrossover(
                            new WeightedAverageCrossover(), new RepairingCrossover()))
                    .withMutationStrategy(new CreepMutation())
                    .withElitism(true)
                    .withCrossoverRate(PROD_CROSSOVER_RATE)
                    .withMutationRate(mutationRate)
                    .withStagnationPatience(PROD_STAGNATION_PATIENCE)
                    .withHypermutationRate(PROD_HYPERMUTATION_RATE)
                    .build();

            long start = System.nanoTime();
            Population population = populationGenerator.generate(
                    instance.exam(), instance.totalStudyDays(), populationSize, context);
            for (int g = 0; g < generations; g++) {
                population = ga.evolvePopulation(population, context);
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000L;

            var fittest = population.getFittest();
            return new Result(fittest.getPlan(), fittest.getFitness(), elapsed);
        } finally {
            RandomProvider.setInstance(new SecureRandom());
        }
    }
}
