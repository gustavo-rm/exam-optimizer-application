package com.ia.project.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.*;
import com.ia.project.dynamicstudyplanner.ga.config.GeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.generator.PopulationGenerator;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service layer that orchestrates the entire optimization process.
 * It acts as a Facade, hiding the complexity of the genetic algorithm
 * from the main application.
 */
@Service
public class StudyOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(StudyOptimizerService.class);

    private final BaselineCalculator baselineCalculator;
    private final ImportanceCalculator importanceCalculator;
    private final GeneticAlgorithmFactory gaFactory;
    private final PopulationGenerator populationGenerator;

    // Custom Micrometer Metrics
    private final Counter optimizationRunsCounter;
    private final Timer optimizationTimer;

    public StudyOptimizerService(BaselineCalculator baselineCalculator,
                                 ImportanceCalculator importanceCalculator,
                                 GeneticAlgorithmFactory gaFactory,
                                 PopulationGenerator populationGenerator,
                                 MeterRegistry meterRegistry) {
        this.baselineCalculator = baselineCalculator;
        this.importanceCalculator = importanceCalculator;
        this.gaFactory = gaFactory;
        this.populationGenerator = populationGenerator;

        this.optimizationRunsCounter = Counter.builder("dynamicstudyplanner.optimization.runs")
                .description("Total number of study plan optimization runs executed")
                .register(meterRegistry);

        this.optimizationTimer = Timer.builder("dynamicstudyplanner.optimization.duration")
                .description("Time taken to execute the full genetic algorithm optimization")
                .register(meterRegistry);
    }

    /**
     * Runs the genetic algorithm to find an optimal study plan.
     * This method orchestrates all steps: preparing the context, configuring the GA,
     * creating the initial population, running the evolution, and packaging the final result.
     *
     * @param exam The complete Exam object, defining all rules and subjects.
     * @param profile The StudentProfile object, defining all personal factors.
     * @param totalDays The total number of "ideal" days the GA can allocate.
     * @param numGenerations The number of generations the algorithm will run.
     * @param populationSize The size of the population in each generation.
     * @return An OptimizationResult containing the best plan found and its fitness.
     */
    public OptimizationResult optimize(
            Exam exam,
            StudentProfile profile,
            int totalDays,
            int numGenerations,
            int populationSize
    ) {
        optimizationRunsCounter.increment();
        long startTime = System.nanoTime(); // Use nanoTime for more accurate metric duration

        try {
            // Step 1: Prepare all necessary data for the evolution.
            EvolutionContext context = prepareContext(exam, profile);

            // Step 2: Configure the GA engine with the chosen strategies via DI.
            GeneticAlgorithm ga = gaFactory.create();

            // Step 3: Create the randomized initial population.
            Population population = populationGenerator.generate(exam, totalDays, populationSize, context);

            // Step 4: Run the evolution process.
            population = runEvolution(population, ga, numGenerations, context);

            // Step 5: Package and return the final, optimized result.
            long endTime = System.nanoTime();
            long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

            Individual bestIndividual = population.getFittest();
            return new OptimizationResult(
                    bestIndividual.getPlan(),
                    bestIndividual.getFitness(),
                    numGenerations,
                    executionTimeMs
            );
        } finally {
            // Record the metric regardless of success or failure
            optimizationTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Prepares all contextual data needed for the genetic algorithm to run.
     * It calculates minimum day constraints and subject importance scores.
     *
     * @param exam The exam configuration.
     * @param profile The student's profile.
     * @return An EvolutionContext object containing all prepared data.
     */
    private EvolutionContext prepareContext(Exam exam, StudentProfile profile) {
        Map<Subject, Integer> minimumDaysPerSubject = baselineCalculator.calculateMinimumDays(exam, profile);
        Map<Subject, Double> importanceScores = importanceCalculator.calculatePersonalizedImportance(exam, profile);

        return new EvolutionContext(importanceScores, minimumDaysPerSubject);
    }

    /**
     * Runs the main evolution loop for a specified number of generations.
     *
     * @param initialPopulation The starting population.
     * @param ga The configured genetic algorithm engine.
     * @param numGenerations The number of generations to run.
     * @param context The evolution context.
     * @return The final, most evolved population.
     */
    private Population runEvolution(Population initialPopulation, GeneticAlgorithm ga, int numGenerations, EvolutionContext context) {
        Population population = initialPopulation;
        for (int i = 0; i < numGenerations; i++) {
            population = ga.evolvePopulation(population, context);

            if (i % 5 == 0) {
                double bestFitness = population.getFittest().getFitness();
                double averageFitness = population.getAverageFitness();
                double worstFitness = population.getWorst().getFitness();

                log.info(
                        "Generation {} | Best Fitness: {} | Avg Fitness: {} | Worst Fitness: {}",
                        String.format("%-4d", i),
                        String.format("%-8.2f", bestFitness),
                        String.format("%-8.2f", averageFitness),
                        String.format("%-8.2f", worstFitness)
                );
            }
        }
        log.info("Evolution complete.");
        return population;
    }
}