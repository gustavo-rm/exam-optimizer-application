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

    /**
     * Fitness subtracted per unit of severity for a violated <b>preference</b>, as opposed to a
     * violated requirement.
     *
     * <h2>Why it is an order of magnitude below {@link #CONSTRAINT_VIOLATION}</h2>
     *
     * The contract separates {@code HARD} from {@code SOFT} and says what the difference means: a
     * hard edge is a rule, a soft edge is a preference. That distinction has to survive into the
     * arithmetic, or the two strengths would differ in name only.
     *
     * <p>The value is fixed by an <b>ordering argument</b>, not by a measurement, and the ordering
     * is: a violated preference must cost less than the smallest objective can pay back
     * ({@link #COGNITIVE_LOAD}, 0.20), so honouring a preference can never be worth sacrificing a
     * whole objective; and far less than a violated requirement (0.50), so a plan that respects
     * every preference and misses a day floor can never outrank one that does the reverse. 0.10
     * sits below both with room to spare, and is large enough that a fully inverted plan is visible
     * in an aggregate reported to three decimals.
     *
     * <p>It is an argument and not evidence, which is why the term reports its own bite —
     * {@code soft-prerequisite-inversions} and the repaired count — in {@code fitness}. If
     * inversions turn out to be rare after repair, the honest move is to drop the term rather than
     * to keep a decorative one; if they are common and the plans are worse for it, the weight is
     * the thing to raise, from data.
     */
    public static final double SOFT_PREREQUISITE_ORDER = 0.10;

    private FitnessWeights() {
    }
}
