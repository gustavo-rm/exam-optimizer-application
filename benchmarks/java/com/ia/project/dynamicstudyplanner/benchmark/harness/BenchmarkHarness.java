package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.metric.MetricsCalculator;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanMetrics;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.BestOfRandomBaseline;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.GreedyPriorityBaseline;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.MarginalGainOptimum;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.PlanningStrategy;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.ProductionGeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.RandomBaseline;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.UniformSplitBaseline;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.engagement.EngagementProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.ConstraintValidator;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FitnessPenalty;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor;
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs every planner over every instance and collects comparable metrics.
 * <p>
 * The harness is the only place that decides what "the same problem" means: it builds one
 * {@link EvolutionContext} per instance and scores every planner's output with the same
 * {@link FitnessEvaluator} and the same downstream scheduler. Planners are run sequentially because
 * {@code ProductionGeneticAlgorithm} mutates the global static {@code RandomProvider}.
 * <p>
 * This class lives entirely outside {@code src/main}. It reads production classes and never writes
 * to them.
 */
public final class BenchmarkHarness {

    /** Base seed; each repetition uses {@code BASE_SEED + repetitionIndex}. */
    public static final long BASE_SEED = 20260830L;

    private final FitnessEvaluator fitnessEvaluator = productionFitnessEvaluator();
    private final MetricsCalculator metricsCalculator = new MetricsCalculator(fitnessEvaluator);
    private final ImportanceCalculator importanceCalculator = new ImportanceCalculator();
    private final BaselineCalculator baselineCalculator = new BaselineCalculator(importanceCalculator);

    private final List<PlanningStrategy> strategies = List.of(
            new ProductionGeneticAlgorithm(fitnessEvaluator),
            new GreedyPriorityBaseline(),
            new MarginalGainOptimum(),
            new UniformSplitBaseline(),
            new RandomBaseline(),
            new BestOfRandomBaseline()
    );

    /**
     * Assembles the fitness pipeline exactly as Spring wires it: every {@code FitnessObjective},
     * {@code FitnessPenalty} and {@code ConstraintValidator} bean in the application context.
     * Adding a component to production without adding it here would silently invalidate the
     * comparison, so the lists are spelled out rather than discovered.
     */
    public static FitnessEvaluator productionFitnessEvaluator() {
        List<FitnessObjective> objectives = List.of(new ScoreGainObjective());
        List<FitnessPenalty> penalties = List.of(
                new DropoutRiskPenalty(new DropoutRiskPredictor()),
                new FatigueAndSustainabilityPenalty(new FatigueAndEnergyModel())
        );
        List<ConstraintValidator> constraints = List.of(
                new MinimumDaysConstraint(),
                new MandatoryReviewConstraint(new HybridRetentionEngine())
        );
        return new FitnessEvaluator(objectives, penalties, constraints);
    }

    public List<PlanningStrategy> strategies() {
        return strategies;
    }

    /**
     * Runs the full comparison.
     *
     * @param instances   the problems to solve
     * @param repetitions repetitions for non-deterministic planners; deterministic ones run once
     * @return one outcome per (instance, strategy) pair
     */
    public List<StrategyOutcome> run(List<BenchmarkInstance> instances, int repetitions) {
        List<StrategyOutcome> outcomes = new ArrayList<>();
        for (BenchmarkInstance instance : instances) {
            EvolutionContext context = buildContext(instance);
            for (PlanningStrategy strategy : strategies) {
                outcomes.add(runOne(instance, context, strategy, repetitions));
            }
        }
        return outcomes;
    }

    private StrategyOutcome runOne(BenchmarkInstance instance, EvolutionContext context,
                                   PlanningStrategy strategy, int repetitions) {
        int reps = strategy.deterministic() ? 1 : repetitions;
        List<PlanMetrics> samples = new ArrayList<>(reps);

        for (int r = 0; r < reps; r++) {
            long seed = BASE_SEED + r;
            long start = System.nanoTime();
            StudyPlan plan = strategy.plan(instance, context, seed);
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

            verifyFeasible(plan, instance, context, strategy);
            samples.add(metricsCalculator.measure(instance, context, plan, elapsedMillis));
        }

        return StrategyOutcome.of(instance.id(), strategy.id(), strategy.displayName(), samples);
    }

    /**
     * Guards the comparison itself: a planner that quietly breaks the budget or the minimum-days
     * floor would post an inflated fitness and make the whole table meaningless.
     */
    private void verifyFeasible(StudyPlan plan, BenchmarkInstance instance,
                                EvolutionContext context, PlanningStrategy strategy) {
        if (plan.getTotalDays() != instance.totalStudyDays()) {
            throw new IllegalStateException(String.format(
                    "%s violou o orcamento em %s: alocou %d dias, esperado %d",
                    strategy.id(), instance.id(), plan.getTotalDays(), instance.totalStudyDays()));
        }
        if (!plan.meetsMinimumConstraints(context.minimumDaysPerSubject())) {
            throw new IllegalStateException(String.format(
                    "%s violou o piso de dias minimos em %s", strategy.id(), instance.id()));
        }
    }

    /**
     * Builds the evolution context exactly as {@code StudyOptimizerService.prepareContext} does,
     * including the two placeholder profiles that method injects. Reproducing them matters: the
     * empty {@code RetentionProfile} and the perfect {@code EngagementProfile} are what keep
     * {@code MandatoryReviewConstraint} and {@code DropoutRiskPenalty} neutral (auditoria §2.3.4,
     * §2.4.4), and a benchmark that quietly supplied richer profiles would not be measuring
     * production behaviour.
     */
    public EvolutionContext contextFor(BenchmarkInstance instance) {
        return buildContext(instance);
    }

    private EvolutionContext buildContext(BenchmarkInstance instance) {
        Map<Subject, Integer> minimumDays =
                baselineCalculator.calculateMinimumDays(instance.exam(), instance.profile());
        Map<Subject, Double> importance =
                importanceCalculator.calculatePersonalizedImportance(instance.exam(), instance.profile());

        // Mirrors StudyOptimizerService.prepareContext field for field, including the planning data
        // the corrected fitness terms read. The one deliberate difference is planStartDate: the
        // service reads the wall clock, the harness uses the instance's fixed anchor so that
        // schedule-derived metrics stay reproducible on any day (pendencia P7 in 04-robustez.md).
        int horizonDays = Math.max(1, (int) instance.horizonDays());
        int hoursPerStudyDay = Math.max(1,
                (int) Math.ceil(instance.profile().getTotalWeeklyHours() / 7.0));
        int maxDailyLoad = new CognitiveLoadCalculator().calculate(instance.profile(), instance.exam());

        return EvolutionContext.of(
                importance,
                minimumDays,
                instance.profile().getState(),
                fitnessEvaluator,
                new RetentionProfile(Map.of()),
                instance.planStartDate(),
                EngagementProfile.baseline(),
                horizonDays,
                hoursPerStudyDay,
                maxDailyLoad
        );
    }
}
