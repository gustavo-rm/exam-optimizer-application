package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * A method for turning a {@link BenchmarkInstance} into a macro study plan.
 * <p>
 * Every implementation distributes {@code instance.totalStudyDays()} sessions across the planning
 * items and differs from the others only in its distribution policy, so a difference between two of
 * them is attributable to that policy alone.
 * <p>
 * <b>This is not the interface the measured engines implement.</b> Those implement
 * {@code plan.PlanEngine} and produce a whole placed {@code PlanResponse}; these produce a macro
 * allocation. See this package's {@code package-info} for why the two are not compared.
 */
public interface PlanningStrategy {

    /** Stable short identifier used as a column key in reports and CSV output. */
    String id();

    /** Human-readable name for report tables. */
    String displayName();

    /**
     * Whether repeated invocations with the same seed produce the same plan.
     * <p>
     * A strategy that draws no random number answers {@code true}; one that does answers
     * {@code false} even when it is reproducible under a fixed seed, because the useful question is
     * whether <b>different</b> seeds can move it. That is the replication unit the measurement uses:
     * repeating one seed on a reproducible planner measures nothing.
     */
    boolean deterministic();

    /**
     * Produces a macro plan for the given instance.
     *
     * @param instance the problem to solve
     * @param context  the evolution context, assembled by {@code SinapseEvolutionContexts}
     * @param seed     seed for whatever randomness the strategy controls; ignored when deterministic
     * @return a study plan allocating {@code instance.totalStudyDays()} days across the subjects
     */
    StudyPlan plan(BenchmarkInstance instance, EvolutionContext context, long seed);
}
