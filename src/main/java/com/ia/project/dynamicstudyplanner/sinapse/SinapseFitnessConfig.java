package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.fitness.WeightedObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.SoftPrerequisiteOrderConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * The SINAPSE path's fitness composition, declared next to the adapter that knows its data.
 *
 * <h2>Why here and not with the concurso one</h2>
 *
 * "Declared per path" means the path states its own terms. This one is the path that is <b>missing</b>
 * inputs, and the reasoning for every omission is in this package — so the declaration belongs where
 * a reader can check it against {@link SinapseEvolutionContexts} and {@link SinapseLoadBudget}
 * without changing files.
 *
 * <p>It also keeps the dependency one-way. {@code sinapse} depends on {@code ga}; declaring this bean
 * in {@code ga.config} would point {@code ga} back at {@code sinapse} and close a module cycle that
 * {@code ModuleBoundaryTest} refuses.
 *
 * <h2>Gated by the profile, like the rest of this path</h2>
 *
 * Without {@code baseline-core} no bean of this package exists, so the default context holds exactly
 * the three concurso objectives — which is what
 * {@code DynamicStudyPlannerApplicationTests.aComposicaoDeFitnessFiadaPeloSpringEhACanonica} pins.
 * An ungated {@link DailyLoadBudgetObjective} would have appeared as a fourth {@code FitnessObjective}
 * bean in every context and pushed that composition's weight sum to 1.2.
 */
@Configuration
@Profile(PlanProtocol.PROFILE)
public class SinapseFitnessConfig {

    /** The published name of this path in {@code FitnessBreakdown} and in {@code fitness.path}. */
    public static final String PATH = "sinapse";

    /**
     * The SINAPSE composition: the same summands, <b>no multiplicative penalties</b>, and a load
     * term that can be switched off.
     *
     * <h2>What comes out unconditionally, and why</h2>
     *
     * {@code FatigueAndSustainabilityPenalty} and {@code DropoutRiskPenalty} were removed from this
     * composition because the platform collects no psychological state and no engagement history.
     * EOA-4b then removed the two classes outright, along with the concurso path that was their only
     * caller; what follows is the reasoning that decided it, kept because it is the reasoning that
     * would have to be reversed to bring them back. Collecting self-declared
     * psychological state bound to an identity is a decision under the LGPD — with minors in the
     * secondary-school pilot — and not one engineering gets to make by filling in a field.
     *
     * <p>Removing them is not cosmetic. On this path the plan <b>is</b> a {@code TacticalStudyPlan},
     * so both would pass their {@code instanceof} guard and then return {@code 1.0} anyway —
     * {@code FatigueAndSustainabilityPenalty} on a null state, {@code DropoutRiskPenalty} on a null
     * engagement profile. They would be inert and indistinguishable from active.
     *
     * <h2>No renormalisation follows from those two, and that was checked</h2>
     *
     * Both are <b>multiplicative factors applied after the clamp</b>, not members of the convex
     * combination. The three objective weights already sum to 1 — 0.50 + 0.30 + 0.20 — so dropping
     * the penalties changes no weight. <b>This is the "no renormalisation to do" case</b>, and
     * inventing one would misdescribe the change.
     *
     * <h2>The load term, and the renormalisation that does happen when it goes</h2>
     *
     * {@link DailyLoadBudgetObjective} replaces {@code CognitiveLoadObjective} here: it lost two of
     * its three inputs, so it is renamed and re-scaled for a 1..4 difficulty scale rather than 1..5.
     * See its Javadoc for the measurement behind that.
     *
     * <p>It is switchable by {@code plan.fitness.sinapse.daily-load-budget}, through the same
     * configuration mechanism as the engine choice, and switching it off <b>renormalises the
     * remaining two weights automatically</b>:
     *
     * <pre>
     *   on  : syllabusMastery 0.500  retention 0.300  dailyLoadBudget 0.200   sum 1.000
     *   off : syllabusMastery 0.625  retention 0.375                          sum 1.000
     * </pre>
     *
     * The renormalisation has to be automatic, not a second edit: without it, turning the term off
     * would leave the weights summing to 0.80, {@code FitnessEvaluator} would refuse to start, and
     * the obvious workaround — leaving them as they are — would silently rescale every fitness value
     * the system reports and make earlier runs incomparable. With it, "no load ceiling" is an
     * executable, comparable condition instead of a code change.
     *
     * @param enableDailyLoadBudget whether the load ceiling is part of this path's fitness
     */
    @Bean
    public FitnessComposition sinapseFitnessComposition(
            ScoreGainObjective scoreGain,
            RetentionObjective retention,
            DailyLoadBudgetObjective dailyLoadBudget,
            MinimumDaysConstraint minimumDays,
            MandatoryReviewConstraint mandatoryReview,
            SoftPrerequisiteOrderConstraint softPrerequisiteOrder,
            @Value("${plan.fitness.sinapse.daily-load-budget:true}") boolean enableDailyLoadBudget) {

        List<FitnessObjective> objectives = enableDailyLoadBudget
                ? List.of(scoreGain, retention, dailyLoadBudget)
                : renormalisedWithoutLoadBudget(scoreGain, retention);

        // Third constraint, and the only one subtracted at a weight of its own: SOFT is a
        // preference and HARD is a rule, and the arithmetic has to keep them apart. See
        // FitnessWeights.SOFT_PREREQUISITE_ORDER for the ordering argument behind 0.10.
        return new FitnessComposition(PATH, objectives,
                List.of(minimumDays, mandatoryReview, softPrerequisiteOrder), List.of());
    }

    /**
     * The two remaining objectives, rescaled to sum to 1.
     *
     * <p>Each weight is divided by the total that remains, which is the only rescaling that keeps
     * the terms' <b>relative</b> importance as declared while restoring the sum the aggregate needs.
     * 0.50/0.80 = 0.625 and 0.30/0.80 = 0.375 — computed rather than written down, so the two
     * weights cannot drift apart from {@code FitnessWeights}.
     */
    private static List<FitnessObjective> renormalisedWithoutLoadBudget(
            ScoreGainObjective scoreGain, RetentionObjective retention) {

        double remaining = scoreGain.getWeight() + retention.getWeight();
        return List.of(
                new WeightedObjective(scoreGain, scoreGain.getWeight() / remaining),
                new WeightedObjective(retention, retention.getWeight() / remaining));
    }
}
