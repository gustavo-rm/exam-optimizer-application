package com.ia.project.dynamicstudyplanner.benchmark.metric;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.sinapse.DailyLoadBudgetObjective;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Group (b): what the schedule turned out like — properties of the plan, not of any objective.
 *
 * <h2>These are the metrics conclusions rest on</h2>
 *
 * Every figure here can be computed by someone who has never seen the fitness function: how many
 * preferences the plan inverts, how many topics the student actually receives, how much of the
 * declared time is used, whether the days are level or front-loaded. That independence is the whole
 * point — an engine cannot be built to look good on a number it was never shown.
 *
 * @param softInversions    soft preferences still inverted in the produced plan
 * @param inversionsRemoved inversions the repair pass removed, or {@code null} where no repair ran
 * @param topicsScheduled   topics that received at least their first session
 * @param topicsUnscheduled topics the calendar could not hold
 * @param scheduledMinutes  minutes actually placed
 * @param utilisation       scheduled minutes over available minutes, in [0,1]
 * @param loadCeilingBound  whether the daily-load ceiling was exceeded at all
 * @param loadExcessRatio   by what fraction it was exceeded; 0 when it was not
 * @param firstQuarterShare share of difficulty-weighted minutes falling in the first quarter of the
 *                          active days — the front-loading measure
 * @param peakOverMean      heaviest active day over the mean active day, by the same weight
 */
public record OutcomeMetrics(
        int softInversions,
        Integer inversionsRemoved,
        int topicsScheduled,
        int topicsUnscheduled,
        long scheduledMinutes,
        double utilisation,
        boolean loadCeilingBound,
        double loadExcessRatio,
        double firstQuarterShare,
        double peakOverMean) {

    /**
     * Reads the outcome of one run.
     *
     * @param response          the answer, for the counts the engines report
     * @param plan              the rebuilt chromosome, for the day-by-day shape
     * @param context           the context, for the load ceiling production itself applies
     * @param availableMinutes  the instance's usable minutes, the utilisation denominator
     * @return the metrics
     */
    public static OutcomeMetrics of(PlanResponse response, TacticalStudyPlan plan,
            EvolutionContext context, long availableMinutes) {

        Map<String, Object> fitness = response.fitness();
        long scheduled = asLong(fitness.get("scheduled-minutes"));
        List<Double> perDay = difficultyPerDay(plan);

        return new OutcomeMetrics(
                (int) asLong(fitness.get("soft-prerequisite-inversions")),
                removedBy(fitness),
                (int) asLong(fitness.get("topics-scheduled")),
                (int) asLong(fitness.get("topics-unscheduled")),
                scheduled,
                availableMinutes == 0 ? 0.0 : (double) scheduled / availableMinutes,
                DailyLoadBudgetObjective.binding(plan, context),
                Math.max(0.0, DailyLoadBudgetObjective.overloadRatio(plan, context)),
                firstQuarterShare(perDay),
                peakOverMean(perDay));
    }

    /**
     * How many inversions the repair removed.
     *
     * <p>{@code null} when the answer carries no before-repair figure, which is how the greedy
     * baseline says it never ran a repair. Reporting zero there would claim a pass that removed
     * nothing, which is a different statement.
     */
    private static Integer removedBy(Map<String, Object> fitness) {
        Object before = fitness.get("soft-prerequisite-inversions-before-repair");
        if (before == null) {
            return null;
        }
        return (int) (asLong(before) - asLong(fitness.get("soft-prerequisite-inversions")));
    }

    /**
     * Difficulty-weighted minutes on each day that has any session, in calendar order.
     *
     * <p>Weighted by {@code difficultyBand x minutes} rather than by minutes alone: two hours of an
     * {@code EXTENDED} topic and two hours of a {@code SHORT} one are not the same day, and
     * front-loading is about effort rather than about clock time.
     */
    private static List<Double> difficultyPerDay(TacticalStudyPlan plan) {
        Map<LocalDate, Double> byDay = new TreeMap<>();
        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : plan.getSchedule().entrySet()) {
            TacticalStudyBlock block = entry.getValue();
            byDay.merge(entry.getKey().startTime().toLocalDate(),
                    block.item().difficultyBand() * (double) block.durationMinutes(), Double::sum);
        }
        return new ArrayList<>(byDay.values());
    }

    /** Share of the total effort that lands in the first quarter of the active days. */
    private static double firstQuarterShare(List<Double> perDay) {
        if (perDay.isEmpty()) {
            return 0.0;
        }
        double total = perDay.stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0) {
            return 0.0;
        }
        int quarter = Math.max(1, (int) Math.ceil(perDay.size() / 4.0));
        double head = perDay.subList(0, quarter).stream().mapToDouble(Double::doubleValue).sum();
        return head / total;
    }

    /** Heaviest day over the mean day. 1.0 is perfectly level; higher is spikier. */
    private static double peakOverMean(List<Double> perDay) {
        if (perDay.isEmpty()) {
            return 0.0;
        }
        double mean = perDay.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double peak = perDay.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return mean <= 0.0 ? 0.0 : peak / mean;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
