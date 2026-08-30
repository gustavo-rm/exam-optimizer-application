package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * A method for turning a {@link BenchmarkInstance} into a macro study plan.
 * <p>
 * The production genetic algorithm and every baseline implement this interface, so the harness can
 * evaluate all of them with the identical production {@code FitnessEvaluator} and the identical
 * downstream scheduler. Any difference in the reported metrics is therefore attributable to the
 * planning method alone.
 */
public interface PlanningStrategy {

    /** Stable short identifier used as a column key in reports and CSV output. */
    String id();

    /** Human-readable name for report tables. */
    String displayName();

    /**
     * Whether repeated invocations with the same seed produce the same plan.
     * <p>
     * The production GA reports {@code false}: the audit (docs/revisao-ag/00-diagnostico.md §7.4)
     * established that its mutation operator draws from {@code Math.random()} and
     * {@code ThreadLocalRandom}, neither of which is seedable, so seeding {@code RandomProvider}
     * pins only selection, crossover and initial-population generation. The harness compensates by
     * running non-deterministic strategies over multiple repetitions.
     */
    boolean deterministic();

    /**
     * Produces a macro plan for the given instance.
     *
     * @param instance the problem to solve
     * @param context  the evolution context, built exactly as {@code StudyOptimizerService} builds it
     * @param seed     seed for whatever randomness the strategy controls; ignored when deterministic
     * @return a study plan allocating {@code instance.totalStudyDays()} days across the subjects
     */
    StudyPlan plan(BenchmarkInstance instance, EvolutionContext context, long seed);
}
