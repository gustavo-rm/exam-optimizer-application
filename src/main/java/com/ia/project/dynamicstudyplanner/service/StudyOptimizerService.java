package examoptimizer.service;

import examoptimizer.domain.StudentProfile;
import examoptimizer.domain.exam.Exam;
import examoptimizer.ga.*;
import examoptimizer.ga.factory.StudyPlanFactory;
import examoptimizer.ga.strategy.crossover.CrossoverStrategy;
import examoptimizer.ga.strategy.crossover.HybridCrossover;
import examoptimizer.ga.strategy.crossover.RepairingCrossover;
import examoptimizer.ga.strategy.crossover.WeightedAverageCrossover;
import examoptimizer.ga.strategy.mutation.CreepMutation;
import examoptimizer.ga.strategy.mutation.MutationStrategy;
import examoptimizer.ga.strategy.mutation.SwapMutation;
import examoptimizer.ga.strategy.selection.SelectionStrategy;
import examoptimizer.ga.strategy.selection.TournamentSelection;
import examoptimizer.domain.OptimizationResult;
import examoptimizer.domain.exam.Subject;
import examoptimizer.service.calculation.BaselineCalculator;
import examoptimizer.service.calculation.ImportanceCalculator;

import java.util.Map;

/**
 * Service layer that orchestrates the entire optimization process.
 * It acts as a Facade, hiding the complexity of the genetic algorithm
 * from the main application.
 */
public class StudyOptimizerService {

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
        long startTime = System.currentTimeMillis();

        // Step 1: Prepare all necessary data for the evolution.
        EvolutionContext context = prepareContext(exam, profile);

        // Step 2: Configure the GA engine with the chosen strategies.
        GeneticAlgorithm ga = configureGeneticAlgorithm();

        // Step 3: Create the randomized initial population.
        Population population = createInitialPopulation(exam, totalDays, populationSize, context);

        // Step 4: Run the evolution process.
        population = runEvolution(population, ga, numGenerations, context);

        // Step 5: Package and return the final, optimized result.
        long endTime = System.currentTimeMillis();
        Individual bestIndividual = population.getFittest();
        return new OptimizationResult(
                bestIndividual.getPlan(),
                bestIndividual.getFitness(),
                numGenerations,
                (endTime - startTime)
        );
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
        BaselineCalculator baselineCalculator = new BaselineCalculator();
        Map<Subject, Integer> minimumDaysPerSubject = baselineCalculator.calculateMinimumDays(exam, profile);

        ImportanceCalculator importanceCalculator = new ImportanceCalculator();
        Map<Subject, Double> importanceScores = importanceCalculator.calculatePersonalizedImportance(exam, profile);

        return new EvolutionContext(importanceScores, minimumDaysPerSubject);
    }

    /**
     * Configures and builds the GeneticAlgorithm engine with a set of pre-defined strategies.
     *
     * @return A fully configured GeneticAlgorithm instance.
     */
    private GeneticAlgorithm configureGeneticAlgorithm() {
        CrossoverStrategy weightedAverage = new WeightedAverageCrossover();
        CrossoverStrategy repairing = new RepairingCrossover();
        CrossoverStrategy hybridCrossover = new HybridCrossover(weightedAverage, repairing, 0.75);
        SelectionStrategy selection = new TournamentSelection(3);
        MutationStrategy mutation = new CreepMutation(3);
        //MutationStrategy mutation = new SwapMutation();

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

    /**
     * Creates the initial, randomized population for the genetic algorithm.
     *
     * @param exam The exam object, used to get the list of subjects.
     * @param totalDays The total days to be allocated in each plan.
     * @param populationSize The number of individuals to create.
     * @param context The evolution context, containing minimum day constraints.
     * @return A new Population object with its initial fitness calculated.
     */
    private Population createInitialPopulation(Exam exam, int totalDays, int populationSize, EvolutionContext context) {
        StudyPlanFactory planFactory = new StudyPlanFactory();
        Population population = new Population(populationSize);
        var allSubjects = exam.getAllSubjects();

        for (int i = 0; i < populationSize; i++) {
            population.addIndividual(new Individual(planFactory.createRandomPlan(allSubjects, totalDays, context.minimumDaysPerSubject())));
        }

        population.calculateFitness(context);
        System.out.println("Initial Population created. Best fitness: " + population.getFittest().getFitness());
        return population;
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

                System.out.printf(
                        "Generation %-4d | Best Fitness: %-8.2f | Avg Fitness: %-8.2f | Worst Fitness: %-8.2f%n",
                        i, bestFitness, averageFitness, worstFitness
                );
            }
        }
        System.out.println("Evolution complete.");
        return population;
    }
}