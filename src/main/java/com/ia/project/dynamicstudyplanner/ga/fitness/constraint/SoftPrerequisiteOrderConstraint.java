package com.ia.project.dynamicstudyplanner.ga.fitness.constraint;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Prices a plan that studies a topic before one of its <b>soft</b> prerequisites.
 *
 * <h2>The formula</h2>
 *
 * Let {@code P} be the soft preferences that apply to the request — both endpoints in scope, the
 * run's provenance condition admitting them, duplicates collapsed. For a preference
 * {@code p -> d} ("p would ideally come before d"):
 *
 * <pre>
 *   gravity(p -> d) = 0                                  when d is not scheduled at all
 *                   = 0                                  when first(p) &lt;= first(d)
 *                   = 1                                  when d is scheduled and p is not
 *                   = (first(p) - first(d)) / span       otherwise, clamped into (0, 1]
 *
 *   severity        = sum over P of gravity  /  |P|          in [0, 1]
 * </pre>
 *
 * where {@code first(t)} is the start of the earliest session of {@code t}, and {@code span} is the
 * distance from the first start to the last start in the whole plan.
 *
 * <p>Each of the three parts answers a question the task's wording asks of it. <b>Number</b> is the
 * sum: every inverted preference adds. <b>Gravity</b> is the addend: an inversion by one afternoon
 * costs a fraction of one that puts the dependent at the start and the prerequisite at the end.
 * <b>Comparability</b> is the denominator: dividing by {@code |P|} keeps the term in {@code [0,1]}
 * whatever the syllabus size, so two instances with different edge counts produce figures that can
 * be read side by side — without it, a large syllabus would be penalised for being large.
 *
 * <p>The two edge rules are the ones that would otherwise be arbitrary. An <b>unscheduled
 * dependent</b> contributes nothing: the student never reaches it, so no order was imposed on them
 * and there is nothing to have got wrong. A <b>scheduled dependent whose prerequisite is absent</b>
 * contributes the maximum: the plan ran to the end and the student was handed {@code d} with
 * {@code p} nowhere in it, which is the worst outcome this preference can describe, and the same
 * reading {@code PlanOutputInvariants} gives the hard case.
 *
 * <h2>This term does not steer the search, and that is the point</h2>
 *
 * During evolution the fitness is evaluated over the macro chromosome: an {@code int[]} of sessions
 * per item with no calendar, which cannot express "p before d" and therefore cannot violate this.
 * The term returns zero there and only bites when the evaluator is handed the <b>placed</b> plan.
 * So it reports what the tactical stage produced; it does not push the genetic search toward it.
 *
 * <p>That is not a gap in the implementation, it is the shape of v1. Precedence is imposed after
 * the search, by ordering and repair, and priced afterwards. The claim v2 will have to beat is
 * exactly this one: that a timeline chromosome, in which the search itself can see order, plans
 * better than a search that cannot plus a heuristic stage that can. A v1 whose search already saw
 * order would not be a control group, it would be a worse v2.
 *
 * <p>{@code MandatoryReviewConstraint} is read the same way and has been since before this term
 * existed: it too can only speak about a plan that has a calendar.
 */
@Component
public class SoftPrerequisiteOrderConstraint implements ConstraintValidator {

    /** Worst gravity a single inversion can carry. */
    private static final double MAXIMUM_GRAVITY = 1.0;

    @Override
    public boolean isValid(StudyPlan plan, EvolutionContext context) {
        return violationSeverity(plan, context) == 0.0;
    }

    @Override
    public double violationSeverity(StudyPlan plan, EvolutionContext context) {
        Map<PlanningItem, Set<PlanningItem>> preferences = context.softPrerequisitesPerItem();
        if (preferences == null || preferences.isEmpty()
                || !(plan instanceof TacticalStudyPlan tactical)) {
            return 0.0;
        }

        Map<PlanningItem, LocalDateTime> first = firstSessionStarts(tactical);
        if (first.isEmpty()) {
            return 0.0;
        }

        double span = spanMinutes(first.values());
        double total = 0.0;
        int applicable = 0;
        for (Map.Entry<PlanningItem, Set<PlanningItem>> entry : preferences.entrySet()) {
            for (PlanningItem prerequisite : entry.getValue()) {
                applicable++;
                total += gravity(first.get(entry.getKey()), first.get(prerequisite), span);
            }
        }
        return applicable == 0 ? 0.0 : total / applicable;
    }

    /**
     * How badly one preference is inverted.
     *
     * @param dependentAt    when the dependent first starts, or {@code null} if never scheduled
     * @param prerequisiteAt when the prerequisite first starts, or {@code null} if never scheduled
     * @param span           minutes from the plan's first start to its last
     */
    private static double gravity(LocalDateTime dependentAt, LocalDateTime prerequisiteAt,
            double span) {

        if (dependentAt == null) {
            return 0.0;
        }
        if (prerequisiteAt == null) {
            return MAXIMUM_GRAVITY;
        }
        if (!prerequisiteAt.isAfter(dependentAt)) {
            return 0.0;
        }
        if (span <= 0.0) {
            // Every session starts at the same instant, so the inversion is real but has no
            // distance to measure. Charging the maximum is the reading that does not reward a
            // degenerate calendar for being degenerate.
            return MAXIMUM_GRAVITY;
        }
        double inverted = Duration.between(dependentAt, prerequisiteAt).toMinutes() / span;
        return Math.min(MAXIMUM_GRAVITY, inverted);
    }

    /** Start of the earliest session of each item. Insertion-ordered, never a hash. */
    private static Map<PlanningItem, LocalDateTime> firstSessionStarts(TacticalStudyPlan plan) {
        Map<PlanningItem, LocalDateTime> first = new LinkedHashMap<>();
        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : plan.getSchedule().entrySet()) {
            LocalDateTime start = entry.getKey().startTime();
            first.merge(entry.getValue().item(), start,
                    (existing, candidate) -> candidate.isBefore(existing) ? candidate : existing);
        }
        return first;
    }

    /** Minutes between the earliest and the latest first-session start in the plan. */
    private static double spanMinutes(Iterable<LocalDateTime> starts) {
        LocalDateTime earliest = null;
        LocalDateTime latest = null;
        for (LocalDateTime start : starts) {
            if (earliest == null || start.isBefore(earliest)) {
                earliest = start;
            }
            if (latest == null || start.isAfter(latest)) {
                latest = start;
            }
        }
        return earliest == null ? 0.0 : Duration.between(earliest, latest).toMinutes();
    }

    @Override
    public double getPenaltyWeight() {
        return FitnessWeights.SOFT_PREREQUISITE_ORDER;
    }
}
