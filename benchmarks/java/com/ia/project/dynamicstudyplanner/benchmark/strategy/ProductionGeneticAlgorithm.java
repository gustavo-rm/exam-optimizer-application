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
 * <b>On reproducibility.</b> Seeding {@link RandomProvider} pins selection, both crossovers and the
 * initial population, but <em>not</em> mutation: {@code AbstractMutationStrategy} decides whether to
 * mutate with {@code Math.random()} and picks subjects with {@code ThreadLocalRandom}, neither of
 * which is seedable (docs/revisao-ag/00-diagnostico.md §7.4, R18). Fixing that requires changing
 * production code, which this measurement stage must not do. The harness therefore treats the GA as
 * a stochastic method and reports mean, standard deviation and range across repetitions instead of
 * pretending a single run is representative.
 */
public final class ProductionGeneticAlgorithm implements PlanningStrategy {

    private final StudyOptimizerService optimizerService;

    /**
     * Assembles the production service graph by hand, mirroring what Spring wires at runtime.
     * The {@code @Qualifier} choices in {@link DefaultGeneticAlgorithmFactory} — {@code hybridCrossover}
     * and {@code creepMutation} — are reproduced explicitly here.
     */
    public ProductionGeneticAlgorithm(FitnessEvaluator fitnessEvaluator) {
        ImportanceCalculator importanceCalculator = new ImportanceCalculator();
        BaselineCalculator baselineCalculator = new BaselineCalculator(importanceCalculator);

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
