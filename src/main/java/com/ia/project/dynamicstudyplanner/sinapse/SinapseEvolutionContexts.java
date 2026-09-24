package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Everything the genetic algorithm is told, assembled from a {@code PlanRequest}.
 *
 * <h2>This class is the boundary the whole path is built around</h2>
 *
 * The concurso path assembled its context from an {@code Exam} and a {@code StudentProfile}, by way
 * of {@code ImportanceCalculator}, {@code BaselineCalculator} and {@code CognitiveLoadCalculator}.
 * This one <b>never reached for any of them</b>, and EOA-4b removed all six. The rule that kept them
 * out is what follows, and it is not a stylistic preference: it is what keeps a missing field from
 * becoming a quiet constant.
 *
 * <p>The reasoning, in the order it has to be read:
 *
 * <ol>
 *   <li>{@code StudentProfile} required self-assessed knowledge gaps and a psychological state.
 *       The platform collects neither.</li>
 *   <li>Those two do not stay on the budget side. The gap reaches
 *       <b>{@code importanceScores}</b> through {@code ImportanceCalculator.applyKnowledgeGapFactor}
 *       — which weighs the mastery term and, through {@code temper}, the retention term — and it
 *       reaches <b>{@code minimumDaysPerItem}</b> through {@code BaselineCalculator}, whose
 *       "perceived difficulty" is the gap-scaled importance normalised by its own maximum, so the
 *       gap survives as a relative weight on the floor that {@code MinimumDaysConstraint} subtracts
 *       at 0.50.</li>
 *   <li>So a synthesised profile with a neutral gap would not merely soften one term. <b>One
 *       invented constant would reweigh the two heaviest objectives and the heaviest constraint at
 *       once</b>, and every one of them would keep reporting a number.</li>
 *   <li>Therefore the demand side is re-sourced rather than defaulted: importance from a
 *       {@link ImportanceStrategy} chosen per request, the day floor from
 *       {@code estimatedMinutes} over the student's measured study day
 *       ({@link SinapseMinimumDays}), and the load ceiling from availability and mean difficulty
 *       alone ({@link SinapseLoadBudget}).</li>
 * </ol>
 *
 * <p>{@code SinapseAdapterIsolationTest} reads the imports of this package and fails if any of those
 * types appears, so the rule is enforced rather than remembered. That test is the real form of "no
 * field is filled with a constant": it forbids the reach, not one value.
 *
 * <h2>What is deliberately not set on the builder</h2>
 *
 * Nothing, any more. {@code studentState} and {@code engagementProfile} were left null here while
 * they existed, because the two terms that read them were not in this path's composition; EOA-4b
 * removed the two fields and the two terms with the concurso path that filled them. Reintroducing
 * either would mean setting it from data the platform does not send — see
 * {@code SinapseFitnessConfig}.
 */
public final class SinapseEvolutionContexts {

    private SinapseEvolutionContexts() {
    }

    /**
     * Assembles the context.
     *
     * @param request   the request as the platform sent it
     * @param items     the topics as planning items, in arrival order
     * @param windows   the availability, already converted
     * @param evaluator this path's fitness, from its own composition
     * @param retention the recurrence used to rebuild the history
     * @param importance where the weight of the heaviest fitness term comes from; chosen per
     *                   request and echoed in the answer
     * @return the context, with no field derived from data the platform did not send
     */
    public static EvolutionContext of(PlanRequest request, List<PlanningItem> items,
            List<AvailabilityWindow> windows, FitnessEvaluator evaluator,
            RetentionAlgorithm retention, ImportanceStrategy importance) {

        LocalDate planStart = request.horizon().start();
        int horizonDays = horizonDays(request);
        // Um unico valor de horas, usado pela demanda E pelo teto. Ver SinapseLoadBudget.of.
        int hoursPerStudyDay = SinapseLoadBudget.wholeHoursPerDay(windows, horizonDays);

        return EvolutionContext.builder()
                .items(items)
                .importanceScores(importance.importanceOf(request))
                .minimumDaysPerItem(SinapseMinimumDays.of(request.topics(), windows))
                .retentionProfile(RetentionHistory.of(request.topics(), request.history(), retention))
                .planStartDate(planStart)
                .planningHorizonDays(horizonDays)
                .hoursPerStudyDay(hoursPerStudyDay)
                .maxDailyCognitiveLoad(SinapseLoadBudget.of(hoursPerStudyDay, items))
                .fitnessEvaluator(evaluator)
                .build();
    }

    /**
     * Days from the plan's first day to its last, inclusive, never below one.
     *
     * <p>Inclusive because the horizon's {@code end} is a last day and not an exclusive bound, and
     * floored at one because the value <b>divides</b> in the retention term's spacing estimate.
     */
    public static int horizonDays(PlanRequest request) {
        long days = ChronoUnit.DAYS.between(request.horizon().start(), request.horizon().end()) + 1;
        return (int) Math.max(1, days);
    }

}
