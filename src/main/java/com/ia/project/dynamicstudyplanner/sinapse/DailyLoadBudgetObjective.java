package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.domain.PlanningItemIndex;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The SINAPSE path's daily-load term: a sustainability ceiling, no longer a cognitive-load model.
 *
 * <h2>Why this is a separate term and not {@code CognitiveLoadObjective} reused</h2>
 *
 * <b>It lost two of its three inputs, so it stopped being the thing the old name described.</b> The
 * concurso ceiling folds in the student's self-assessed knowledge gap and their stress, fatigue and
 * motivation; neither exists on this path, and both are <b>removed from the formula rather than
 * defaulted</b> ({@link SinapseLoadBudget}). What remains compares the plan's expected daily effort
 * against a ceiling derived from declared availability and the syllabus's mean difficulty.
 *
 * <p>That is a narrower claim, and keeping the name {@code cognitiveLoad} — with its citation of
 * Sweller — would describe a model this is not. Sweller's construct is about load within a single
 * learning episode; this bounds an <em>expected daily</em> figure computed from two structural
 * inputs. So the term is called {@value #NAME}, which says what it does. A stored run has to be
 * self-describing a year later: reading {@code cognitiveLoad} in an archived
 * {@code FitnessBreakdown} would invite the reader to assume inputs that were never there.
 *
 * <h2>The scale, which is the easy mistake here</h2>
 *
 * Demand and ceiling have to be measured on the same scale, and on this path they are not the ones
 * the concurso constants assume. {@code CognitiveLoadCalculator} is calibrated for
 * {@code cognitiveLoad} on <b>1..5</b>, midpoint 3, span 4. The SINAPSE demand comes from
 * {@code effortTier} mapped to <b>1..4</b>, midpoint 2.5, span 3.
 *
 * <p>Using the 1..5 constants against 1..4 demand makes the ceiling systematically generous.
 * Measured at the scale's own midpoint, with three study hours a day: demand 7.50, ceiling 9.23 —
 * <b>23% of slack where there should be none</b>. The term would be active in the code and almost
 * never binding in practice, which is the same failure this path exists to avoid, entering through a
 * different door.
 *
 * <p><b>Decision: the real scale's midpoint and span are used ({@value #AVERAGE_BAND} and
 * {@value #BAND_SPAN}), not a remapping of the tiers onto 1..5.</b> Four ordinal positions do not
 * map onto five without skipping one, and skipping one invents a distinction the curator never made
 * — the same argument {@link EffortTierBands} makes for keeping that mapping an order-preserving
 * identity. Fixing the constants is arithmetic; stretching the tiers would be modelling.
 *
 * <h2>It is measured, not assumed to matter</h2>
 *
 * {@link #overloadRatio} exposes the unclamped overload so the published fitness can report
 * <b>whether the ceiling bound and by how much</b>. If the rate at which it binds across real
 * instances is near zero, the term is decorative and the correct decision is to remove it and
 * renormalise the remaining two weights — which {@code plan.fitness.sinapse.daily-load-budget}
 * makes a configuration change rather than a code change. {@code DailyLoadBudgetBindingRateTest}
 * measures the rate.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class DailyLoadBudgetObjective implements FitnessObjective {

    /** The published name of this term. Deliberately not {@code cognitiveLoad}; see the class doc. */
    public static final String NAME = "dailyLoadBudget";

    /**
     * Midpoint of the difficulty scale this path's demand is measured on.
     *
     * <p>2.5, because {@code effortTier} gives four ordinal positions (1..4). The concurso path uses
     * 3.0 for its five. See the class comment for what happens when the two are crossed.
     */
    public static final double AVERAGE_BAND = 2.5;

    /** Span of that scale: {@code 4 - 1}. The concurso path's is 4. */
    public static final double BAND_SPAN = 3.0;

    /**
     * Multiple of the ceiling at which the term bottoms out at zero.
     *
     * <p>Same value and same reasoning as the concurso term: at twice the sustainable load the plan
     * already scores zero here, and further overload is indistinguishable — a deliberate floor, so
     * one catastrophic term cannot swamp the others in the weighted sum.
     */
    private static final double OVERLOAD_RATIO_AT_ZERO = 1.0;

    @Override
    public double calculateReward(StudyPlan plan, EvolutionContext context) {
        return 1.0 - Math.clamp(overloadRatio(plan, context), 0.0, OVERLOAD_RATIO_AT_ZERO);
    }

    /**
     * How far past the ceiling the plan's expected daily load sits, unclamped.
     *
     * <p>Negative means the plan fits inside the ceiling with room to spare; zero means it sits
     * exactly on it; positive is the fraction by which it exceeds it. The raw figure is what makes
     * "did this term bind, and by how much" reportable — {@link #calculateReward} clamps it away.
     *
     * @param plan    the plan being judged
     * @param context the evolution context, for the ceiling and the gene order
     * @return the overload ratio, or {@link Double#NaN} when there is nothing to compare because the
     *         plan allocates no days or the ceiling is not positive
     */
    public static double overloadRatio(StudyPlan plan, EvolutionContext context) {
        int totalDays = plan.getTotalDays();
        int budget = context.maxDailyCognitiveLoad();
        if (totalDays <= 0 || budget <= 0) {
            return Double.NaN;
        }

        // Percurso por posicao, sem entrada de mapa criada por gene (pendencia P18).
        PlanningItemIndex ordem = context.geneVectors().index();
        double weightedLoad = 0.0;
        for (int i = 0; i < ordem.size(); i++) {
            weightedLoad += plan.daysAt(ordem, i) * (double) ordem.item(i).difficultyBand();
        }

        double expectedDailyLoad = context.hoursPerStudyDay() * weightedLoad / totalDays;
        return (expectedDailyLoad - budget) / budget;
    }

    /** Whether the ceiling actually constrained this plan. */
    public static boolean binding(StudyPlan plan, EvolutionContext context) {
        double overload = overloadRatio(plan, context);
        return !Double.isNaN(overload) && overload > 0.0;
    }

    /** The published name of the term. */
    @Override
    public String name() {
        return NAME;
    }

    /**
     * The same 0.20 the concurso path gives its load term.
     *
     * <p>Unchanged on purpose: the term is narrower but occupies the same role — a feasibility guard
     * rather than a goal — and changing its share at the same time as changing its inputs would make
     * the two paths' fitness values differ for two reasons at once.
     */
    @Override
    public double getWeight() {
        return FitnessWeights.COGNITIVE_LOAD;
    }
}
