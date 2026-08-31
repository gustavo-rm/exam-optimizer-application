package com.ia.project.dynamicstudyplanner.ga.fitness;

/**
 * The weights of the aggregated fitness, in one place.
 * <p>
 * <b>Read {@code docs/revisao-ag/05-fitness-function.md} before changing any value here.</b> That
 * document is the reference for what each term models, which learning-science result it comes from,
 * and why its weight has the value it has. A weight edited here without updating that page leaves
 * the product unable to justify its own planning decisions.
 * <p>
 * Two rules hold for every weight in this class:
 * <ol>
 *   <li><b>They sum to 1.</b> Every objective returns a value in [0,1], so the aggregate is also in
 *       [0,1] and comparable across exams and across releases. {@code FitnessEvaluator} asserts the
 *       sum at startup.</li>
 *   <li><b>They are product judgments, not empirical fits.</b> No dataset was used to calibrate
 *       them. What is measured is their <i>stability</i>: the sensitivity study in
 *       {@code benchmarks/…/robustness/WeightSensitivityMain} reports how far the generated plan
 *       moves when each weight is perturbed by ±20%. Honest reporting of that number is what makes
 *       the choice defensible, not a claim of optimality.</li>
 * </ol>
 */
public final class FitnessWeights {

    /**
     * O1 — syllabus mastery weighted by each subject's share of the exam score.
     * <p>
     * The largest share: the exam is what the student is preparing for, and a plan that scores well
     * on everything else while neglecting the syllabus is not a study plan.
     */
    public static final double SYLLABUS_MASTERY = 0.50;

    /**
     * O3 — fraction of the syllabus still above the forgetting threshold on exam day.
     * <p>
     * Second largest. Knowledge that is not retained until the exam is worth nothing on the exam,
     * so retention is not a refinement of mastery but a precondition for it counting at all. It
     * stays below O1 because the reverse failure is worse: a perfectly retained but narrow plan
     * loses more points than a broad plan with some decay.
     */
    public static final double RETENTION = 0.30;

    /**
     * O4 — how sustainable the plan's expected daily mental effort is.
     * <p>
     * Smallest of the three, for two reasons. It is the least faithful to its source theory: the
     * macro chromosome has no calendar, so the term can only bound an <em>expected</em> daily load,
     * not a real per-episode one. And it is a feasibility guard rather than a goal — its job is to
     * stop the optimizer proposing a plan the student cannot sustain, not to drive what the plan
     * teaches. A larger share would let it trade away syllabus coverage for comfort.
     */
    public static final double COGNITIVE_LOAD = 0.20;

    /**
     * Fitness subtracted per unit of constraint-violation severity.
     * <p>
     * Half the total objective mass: a fully violated hard constraint costs more than any single
     * objective can pay back, so an infeasible plan can never outrank a feasible one on the strength
     * of the other terms, while a marginal violation stays recoverable and still shows the search
     * which way is out.
     */
    public static final double CONSTRAINT_VIOLATION = 0.50;

    private FitnessWeights() {
    }
}
