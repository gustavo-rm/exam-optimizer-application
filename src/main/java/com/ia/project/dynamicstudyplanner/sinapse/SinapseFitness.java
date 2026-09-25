package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteProvenance;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteReport;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code fitness} map for the genetic path: the terms that ran, and only those.
 *
 * <h2>A disabled term does not appear with a zero</h2>
 *
 * The map is built by walking the {@link FitnessBreakdown} the evaluator produced, and the breakdown
 * is built by walking this path's {@link FitnessComposition}. So the two multiplicative penalties
 * that the SINAPSE path drops are <b>absent from the report</b>, not present at their neutral value.
 *
 * <p>That is the whole point of the arrangement. A reader of a stored plan can tell that fatigue and
 * dropout risk were not considered, because there is no key for them. Reporting
 * {@code "fatigue-penalty": 1.0} would have been true arithmetic and a false statement: it would say
 * the function looked at fatigue and found nothing wrong, when the function has no fatigue data at
 * all. {@code SinapseFitnessTermsTest} asserts the keys are absent rather than zero.
 *
 * <h2>What the keys mean</h2>
 *
 * The objective and constraint terms are named by their own {@code name()}, prefixed so that a
 * reader can tell a summand from a subtracted severity without knowing the aggregation. The
 * coverage counts are the allocation's own, in the same shape the baseline reports them, so the two
 * conditions can be compared on coverage without a translation step.
 *
 * <p>{@code path} names which composition ran, which is a finer statement than {@code engine}: two
 * engines could in principle share a composition, and one engine could be reconfigured. The
 * selector stamps {@code engine} separately, and it stamps it last, so an engine cannot mislabel
 * itself here.
 */
public final class SinapseFitness {

    /** Prefix for a weighted summand. */
    private static final String OBJECTIVE = "objective.";

    /** Prefix for a subtracted severity. */
    private static final String CONSTRAINT = "constraint.";

    private SinapseFitness() {
    }

    /**
     * Sums the run into the reported terms.
     *
     * @param composition the terms this path declared
     * @param breakdown   the decomposition of the winning plan's fitness
     * @param placed      what the calendar could hold
     * @param importance  which meaning of importance ran, echoed so the run is reconstructable
     * @param prerequisites what the prerequisite stage saw and did, echoed for the same reason
     * @return the map to report, read-only
     */
    public static Map<String, Object> of(FitnessComposition composition,
            FitnessBreakdown breakdown, SessionPlacement.Result placed,
            StudyPlan plan, EvolutionContext context, ImportanceStrategy importance,
            PrerequisiteReport prerequisites) {

        Map<String, Object> fitness = new LinkedHashMap<>();
        fitness.put("path", composition.path());
        // Sem isto, uma execucao registrada nao e reproduzivel: nada diz o que "importance"
        // significava naquela execucao, e ela alimenta o termo que carrega metade da fitness.
        fitness.put(ImportanceStrategy.FITNESS_KEY, importance.id());
        fitness.put("aggregate", breakdown.aggregate());

        for (FitnessBreakdown.Term term : breakdown.terms()) {
            String prefix = term.kind() == FitnessBreakdown.TermKind.OBJECTIVE
                    ? OBJECTIVE : CONSTRAINT;
            fitness.put(prefix + term.name(), term.weightedContribution());
            fitness.put(prefix + term.name() + ".weight", term.weight());
        }

        // Absent when the composition declares none, which is the case on this path. An empty
        // "penalties" key would be the same false reassurance as a penalty reported at 1.0.
        if (!composition.penalties().isEmpty()) {
            breakdown.penalties().forEach(penalty ->
                    fitness.put("penalty." + penalty.name(), penalty.factor()));
        }

        reportDailyLoadBudget(fitness, composition, plan, context);

        fitness.put("topics-total", placed.topicsTotal());
        fitness.put("topics-scheduled", placed.topicsScheduled());
        fitness.put("topics-unscheduled", placed.topicsTotal() - placed.topicsScheduled());
        fitness.put("partial", placed.partial());
        fitness.put("scheduled-minutes", placed.scheduledMinutes());
        reportPrerequisites(fitness, prerequisites);
        return Collections.unmodifiableMap(fitness);
    }

    /**
     * What the prerequisite stage saw, did, and could not do.
     *
     * <h2>Why the condition is echoed and not assumed</h2>
     *
     * The provenance condition is chosen per request and defaults from configuration, so nothing in
     * a stored plan would otherwise say which edges it was built from. Two runs of the same request
     * under {@code curated} and under {@code all} can produce different plans for no reason visible
     * in either of them — the same failure {@code importance-strategy} is echoed to prevent.
     *
     * <h2>Why the repair reports both counts</h2>
     *
     * {@code inversions-before-repair} and {@code inversions} together say whether the repair pass
     * is earning its place. If the two are always equal, the pass never removes anything and should
     * go; if the second is always zero, the fitness term never bites and <b>it</b> should go. Either
     * way the decision is a measurement rather than an opinion, which is the same standard
     * {@code dailyLoadBudget} is held to above.
     *
     * <h2>Why unscheduled topics are named</h2>
     *
     * A count says a partial plan happened; the names say to whom. The scheduled set is a prefix of
     * the study order, so the names are exactly the tail the student never reaches, and the platform
     * can tell a student that those topics were left out instead of silently showing a shorter plan.
     */
    private static void reportPrerequisites(Map<String, Object> fitness,
            PrerequisiteReport prerequisites) {

        fitness.put(PrerequisiteProvenance.FITNESS_KEY, prerequisites.provenance().id());
        fitness.put("prerequisite-edges-hard", prerequisites.hardEdges());
        fitness.put("prerequisite-edges-soft", prerequisites.softEdges());
        fitness.put("soft-prerequisite-inversions", prerequisites.inversionsAfter());
        fitness.put("soft-prerequisite-inversions-before-repair", prerequisites.inversionsBefore());
        fitness.put("topics-unscheduled-ids", prerequisites.unscheduled());
    }

    /**
     * Whether the daily-load ceiling bound this plan, and by how much.
     *
     * <h2>Why the term reports its own bite</h2>
     *
     * A term can be present in the composition and still never constrain anything — the ceiling sits
     * so far above the demand that {@code calculateReward} returns 1.0 for every plan the search
     * considers. In the published fitness that is indistinguishable from a term that looked and
     * found nothing wrong. So the term reports the raw figure it otherwise clamps away:
     * {@code binding} says whether the ceiling was exceeded, {@code excess-ratio} by what fraction,
     * {@code ceiling} what it was.
     *
     * <p>This is what turns "keep the term or drop it" into a measurement rather than an opinion. If
     * {@code binding} is false across real instances, the term is decorative, and dropping it is a
     * configuration change ({@code plan.fitness.sinapse.daily-load-budget}) rather than a code
     * change. {@code DailyLoadBudgetBindingRateTest} aggregates the rate over a spread of instances.
     *
     * <p>Nothing is reported when the term is not in the composition: an absent term does not get a
     * key, for the same reason a disabled penalty does not.
     */
    private static void reportDailyLoadBudget(Map<String, Object> fitness,
            FitnessComposition composition, StudyPlan plan, EvolutionContext context) {

        boolean active = composition.objectives().stream()
                .anyMatch(objective -> DailyLoadBudgetObjective.NAME.equals(objective.name()));
        if (!active) {
            return;
        }

        double overload = DailyLoadBudgetObjective.overloadRatio(plan, context);
        if (Double.isNaN(overload)) {
            return;
        }
        String key = OBJECTIVE + DailyLoadBudgetObjective.NAME;
        fitness.put(key + ".binding", overload > 0.0);
        fitness.put(key + ".excess-ratio", Math.round(Math.max(0.0, overload) * 10_000d) / 10_000d);
        fitness.put(key + ".ceiling", context.maxDailyCognitiveLoad());
    }
}
