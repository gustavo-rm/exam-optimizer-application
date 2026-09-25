package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.metric.CostMetrics;
import com.ia.project.dynamicstudyplanner.benchmark.metric.Invariants;
import com.ia.project.dynamicstudyplanner.benchmark.metric.MeasurementRow;
import com.ia.project.dynamicstudyplanner.benchmark.metric.OutcomeMetrics;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategies;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the condition matrix and produces one row per run.
 *
 * <h2>The replication unit is a distinct seed, and nothing is averaged here</h2>
 *
 * A seeded run has no measurement noise: run it twice and it returns the same plan to the last bit —
 * which the harness verifies rather than assumes, on every row. Repeating the same seed therefore
 * measures nothing, and averaging over such repeats would report a spread of zero as if it were a
 * spread. What varies is <b>which</b> seed, so the replication unit is a set of distinct seeds per
 * instance, and the comparison between engines is <b>paired by instance</b>: the same instance, the
 * same seed, two engines.
 *
 * <p>This class emits rows. It computes no mean, no ratio between engines and no aggregate, because
 * every such number needs its aggregation method stated next to it, and that belongs in the report.
 *
 * <h2>Two runs per row</h2>
 *
 * Every row is produced by running the same request twice and comparing the two answers as text. It
 * doubles the cost and it is the cheapest possible form of the guarantee the whole measurement rests
 * on: until reproducibility holds for every condition, no number in the report is valid. Checking it
 * once per condition and assuming it for the rest would leave exactly the seeds nobody checked as
 * the place a lost seed restoration could hide.
 *
 * <h2>The greedy baseline is run on every seed too</h2>
 *
 * It consumes no randomness — {@code GreedyBaselineScheduler} echoes {@code randomSeed} into the
 * metadata and never reads it — so its rows are identical across seeds by construction. Running them
 * anyway costs little and turns "the baseline has no seed variance" from an argument from the source
 * into a measured zero, which is what the threshold proposal in the report needs.
 */
public final class MeasurementHarness {

    /**
     * The seeds. Five distinct values, fixed here so a measurement can be repeated.
     *
     * <p>Chosen as a run of consecutive integers from the library seed rather than as "random-looking"
     * numbers: {@code java.util.Random} scrambles its seed on construction, so consecutive seeds give
     * unrelated streams, and a reader can check that nothing was cherry-picked.
     */
    public static final long[] SEEDS = {20260925L, 20260926L, 20260927L, 20260928L, 20260929L};

    private final PlanEngineSelector selector;
    private final PlanScoring scoring;
    private final ImportanceStrategies importanceStrategies;
    private final GeneticSearchBudget budget;

    /**
     * @param selector             the production entry point every run goes through
     * @param scoring              the one evaluator every plan is scored by, after the fact
     * @param importanceStrategies to resolve the fixed importance condition for re-scoring
     * @param budget               the configured search budget, for the derived evaluation count
     */
    public MeasurementHarness(PlanEngineSelector selector, PlanScoring scoring,
            ImportanceStrategies importanceStrategies, GeneticSearchBudget budget) {

        this.selector = selector;
        this.scoring = scoring;
        this.importanceStrategies = importanceStrategies;
        this.budget = budget;
    }

    /**
     * Runs every instance under every condition on every seed.
     *
     * @param instances the library
     * @return one row per run, in instance then condition then seed order
     * @throws MeasurementAbortedException on the first invariant or reproducibility failure
     */
    public List<MeasurementRow> runAll(List<BenchmarkInstance> instances) {
        List<Condition> matrix = Condition.matrix();
        List<MeasurementRow> rows = new ArrayList<>();
        for (BenchmarkInstance instance : instances) {
            for (Condition condition : matrix) {
                for (long seed : SEEDS) {
                    rows.add(measure(instance, condition, seed));
                }
            }
        }
        return rows;
    }

    /**
     * One run: two engine calls, the invariants, then the metrics.
     *
     * @param instance  the instance
     * @param condition the engine and the ablation condition
     * @param seed      the replication unit
     * @return the row
     * @throws MeasurementAbortedException when the answer is invalid or not reproducible
     */
    public MeasurementRow measure(BenchmarkInstance instance, Condition condition, long seed) {
        PlanRequest request = condition.applyTo(instance.request(), seed);

        long startedAt = System.nanoTime();
        PlanResponse response = selector.plan(request);
        long elapsedMicros = (System.nanoTime() - startedAt) / 1_000L;

        checkReproducible(instance, condition, seed, request, response);
        checkValid(instance, condition, seed, request, response);

        EvolutionContext context = scoring.contextFor(request, condition.provenance(),
                importance(request));
        TacticalStudyPlan plan = scoring.rebuild(request, response);
        FitnessBreakdown objective = scoring.score(plan, context);
        checkScoringFaithful(instance, condition, seed, response, objective);

        return new MeasurementRow(instance, engineOf(response), condition.provenance().id(),
                Condition.IMPORTANCE, seed,
                count(response, "prerequisite-edges-hard"),
                count(response, "prerequisite-edges-soft"),
                OutcomeMetrics.of(response, plan, context, instance.availableMinutes()),
                CostMetrics.of(response, populationSizeFor(response), elapsedMicros),
                objective);
    }

    /**
     * Stop rule (b): a second run of the same seeded request must produce the same answer.
     *
     * <p>Checked <b>before</b> the invariants, because an answer that is not reproducible cannot be
     * diagnosed: a violation found in one of two differing runs does not say which run has it.
     */
    private void checkReproducible(BenchmarkInstance instance, Condition condition, long seed,
            PlanRequest request, PlanResponse first) {

        PlanResponse second = selector.plan(request);
        String signature = Invariants.signature(first);
        String repeated = Invariants.signature(second);
        if (!signature.equals(repeated)) {
            throw new MeasurementAbortedException(instance, condition, seed,
                    "two runs of the same seeded request produced different plans",
                    List.of("first : " + signature, "second: " + repeated));
        }
    }

    /**
     * Stop rule (a): the invariants of group (a). A violation is a defect, not a worse score.
     *
     * <p>A {@code HARD} inversion in particular is a v1 defect and not a measurement result: it says
     * EOA-7's acceptance criterion does not hold, and nothing downstream of it is worth measuring.
     */
    private static void checkValid(BenchmarkInstance instance, Condition condition, long seed,
            PlanRequest request, PlanResponse response) {

        List<String> violations =
                Invariants.violations(request, response, condition.provenance());
        if (!violations.isEmpty()) {
            throw new MeasurementAbortedException(instance, condition, seed,
                    "the answer violates " + violations.size() + " invariant(s) of group (a)",
                    violations);
        }
    }

    /**
     * The instrument checks itself against the one engine that publishes its own score.
     *
     * <h2>Why this is the check that makes the re-scoring credible</h2>
     *
     * Every plan is re-scored here so that both engines are judged on identical terms, and that
     * rests on one claim: the round trip through {@code PlanResponse} and back into a tactical
     * chromosome loses nothing the fitness reads. The claim is checkable exactly once — on the
     * genetic engine, which already published the score it computed on its own in-memory plan. If
     * the two agree <b>bit for bit</b>, the rebuild is faithful; if they do not, the re-scoring is
     * measuring something the engine never produced, and every objective column is wrong.
     *
     * <p>Exact equality and not a tolerance. The two numbers come from the same evaluator over the
     * same terms in the same order; anything other than an identical double means an input differs,
     * and a tolerance would hide exactly the drift this exists to catch.
     *
     * <p>An engine that publishes no {@code aggregate} — the greedy baseline computes none, and
     * reporting one would describe a run that did not happen — is not checked here. There is nothing
     * to check it against, which is the whole reason the re-scoring exists.
     */
    private static void checkScoringFaithful(BenchmarkInstance instance, Condition condition,
            long seed, PlanResponse response, FitnessBreakdown objective) {

        if (!(response.fitness().get("aggregate") instanceof Number published)) {
            return;
        }
        if (Double.compare(published.doubleValue(), objective.aggregate()) != 0) {
            throw new MeasurementAbortedException(instance, condition, seed,
                    "re-scoring the answer did not reproduce the score the engine published, so "
                            + "the rebuilt plan is not the plan the engine scored",
                    List.of("engine published: " + published,
                            "harness re-scored: " + objective.aggregate()));
        }
    }

    /**
     * The importance strategy for re-scoring, resolved by production's own registry.
     *
     * <p>Resolved rather than constructed, so the harness cannot score a plan under a different
     * meaning of importance than the engine planned it with.
     */
    private ImportanceStrategy importance(PlanRequest request) {
        return importanceStrategies.resolve(request);
    }

    /**
     * How many individuals the run evaluated per generation, or zero when it evolved nothing.
     *
     * <p>Read off a reported property rather than off the engine's identity: an engine that reports
     * no generations ran no population, whichever engine it is. A third evolutionary engine gets its
     * evaluation count from this with no change here.
     */
    private int populationSizeFor(PlanResponse response) {
        return response.metadata().generations() == 0 ? 0 : budget.populationSize();
    }

    /** The engine as the selector stamped it — the production path's own attribution. */
    private static String engineOf(PlanResponse response) {
        Object engine = response.fitness()
                .get(com.ia.project.dynamicstudyplanner.plan.PlanEngine.FITNESS_ENGINE_KEY);
        return String.valueOf(engine);
    }

    private static int count(PlanResponse response, String key) {
        Map<String, Object> fitness = response.fitness();
        return fitness.get(key) instanceof Number number ? number.intValue() : 0;
    }
}
