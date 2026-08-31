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
     * Currently the only objective, so it carries the entire weight. The retention and
     * cognitive-load objectives introduced later in this review take their share from it.
     */
    public static final double SYLLABUS_MASTERY = 1.00;

    private FitnessWeights() {
    }
}
