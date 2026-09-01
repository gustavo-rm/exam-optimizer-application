package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.config.DefaultGeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.generator.DefaultPopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.TournamentSelection;
import com.ia.project.dynamicstudyplanner.service.StudyOptimizerService;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Random;

/**
 * The system under test — the production genetic algorithm, run through its real entry point.
 * <p>
 * This class deliberately calls {@link StudyOptimizerService#optimize} rather than reimplementing
 * the evolution loop. Every production component is exercised as shipped: the hyperparameters fixed
 * in {@link DefaultGeneticAlgorithmFactory} (elitism, crossover 0.95, mutation 0.05, stagnation
 * patience 25, hypermutation 0.20), the {@code HybridCrossover} / {@code CreepMutation} /
 * {@code TournamentSelection} operator set, and the same dummy retention and engagement profiles
 * the service injects. Nothing here is a transcription that could drift from production.
 * <p>
 * <b>On reproducibility.</b> Seeding {@link RandomProvider} now pins the whole evolution. It did not
 * when this class was written: {@code AbstractMutationStrategy} decided whether to mutate with
 * {@code Math.random()} and picked subjects with {@code ThreadLocalRandom}, neither of which is
 * seedable (docs/revisao-ag/00-diagnostico.md §7.4, R18), and only 2 of 8 instances reproduced.
 * Etapa 04 routed both through {@code RandomProvider} and {@code RobustnessMain} now reports 8 of 8.
 * <p>
 * {@link #deterministic()} still answers {@code false}, deliberately. The harness then runs the GA
 * over several seeds and reports mean, standard deviation and range — which is what calibrates the
 * regression threshold in {@code GeneticAlgorithmVsBaselinesTest} and what would surface a
 * reintroduced unseeded draw as spread rather than as a silent single-run number.
 */
public final class ProductionGeneticAlgorithm implements PlanningStrategy {

    private final StudyOptimizerService optimizerService;

    /**
     * Assembles the production service graph by hand, mirroring what Spring wires at runtime.
     * The {@code @Qualifier} choices in {@link DefaultGeneticAlgorithmFactory} — {@code hybridCrossover}
     * and {@code creepMutation} — are reproduced explicitly here.
     */
    public ProductionGeneticAlgorithm(FitnessEvaluator fitnessEvaluator) {
        this(fitnessEvaluator, new BaselineCalculator(new ImportanceCalculator()));
    }

    /**
     * Same wiring with the minimum-days calculator supplied by the caller.
     * <p>
     * Exists for {@code WeightTradeoffMain}, which needs to price a change to the coverage floor
     * without making one: the floor is computed inside {@code BaselineCalculator}, so it cannot be
     * overridden through the {@code EvolutionContext} the service builds for itself. Every other
     * caller gets the production calculator through the constructor above.
     */
    public ProductionGeneticAlgorithm(FitnessEvaluator fitnessEvaluator,
                                      BaselineCalculator baselineCalculator) {
        ImportanceCalculator importanceCalculator = new ImportanceCalculator();

        DefaultGeneticAlgorithmFactory gaFactory = new DefaultGeneticAlgorithmFactory(
                new TournamentSelection(),
                new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover()),
                new CreepMutation()
        );

        this.optimizerService = new StudyOptimizerService(
                baselineCalculator,
                importanceCalculator,
                gaFactory,
                new DefaultPopulationGenerator(),
                fitnessEvaluator,
                new SimpleMeterRegistry()
        );
    }

    @Override
    public String id() {
        return "ag-producao";
    }

    @Override
    public String displayName() {
        return "AG de producao";
    }

    @Override
    public boolean deterministic() {
        return false;
    }

    /**
     * Runs the production optimizer.
     * <p>
     * The {@code context} argument is ignored on purpose: the service builds its own context through
     * its private {@code prepareContext}, and letting it do so is what keeps this an end-to-end
     * measurement. The harness builds an equivalent context for scoring, using the same production
     * calculators on the same exam and profile, so the two agree on {@code importanceScores} and
     * {@code minimumDaysPerSubject} — the only context fields any active fitness component reads.
     */
    @Override
    public StudyPlan plan(BenchmarkInstance instance, EvolutionContext context, long seed) {
        // Global static mutation: pins every RNG that routes through RandomProvider. Not thread-safe
        // across concurrent harness runs, so the harness runs strategies sequentially.
        RandomProvider.setInstance(new Random(seed));
        try {
            return optimizerService.optimize(
                    instance.exam(),
                    instance.profile(),
                    instance.totalStudyDays(),
                    instance.numGenerations(),
                    instance.populationSize()
            ).plan();
        } finally {
            RandomProvider.setInstance(new java.security.SecureRandom());
        }
    }
}
