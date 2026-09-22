package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;

import java.util.List;

/**
 * The sustainable daily load {@link DailyLoadBudgetObjective} scores a plan against, built only from
 * data this path actually holds.
 *
 * <h2>Why not {@code CognitiveLoadCalculator}</h2>
 *
 * The concurso path's budget has three factors, and <b>two of them have no source in
 * {@code PlanRequest}</b>:
 *
 * <table border="1">
 *   <caption>Where each factor comes from</caption>
 *   <tr><th>Factor</th><th>Concurso source</th><th>Available here</th></tr>
 *   <tr><td>base capacity</td><td>weekly study hours</td><td><b>yes</b>, from availability</td></tr>
 *   <tr><td>pressure</td><td>mean subject difficulty</td><td><b>yes</b>, from effortTier</td></tr>
 *   <tr><td>fatigue</td><td>self-assessed knowledge gap and psychological state</td>
 *       <td><b>no</b></td></tr>
 * </table>
 *
 * <p>{@code CognitiveLoadCalculator} does not fail on the missing third factor — it defaults the
 * absent gap to {@code 3.0}, which collapses that factor to a constant {@code 0.95}, and drops the
 * three psychological modifiers when the state is null. Reusing it would therefore have produced a
 * budget with a neutral constant baked in and no sign that anything was missing. <b>That is exactly
 * the failure mode this path exists to avoid</b>, one layer below where it was expected: a term
 * whose inputs are absent still contributing, and looking as if it had considered them.
 *
 * <p>So the factor is <b>removed</b>, not defaulted. The budget here is the two factors that have
 * data, and the fitness term built on it makes a narrower claim honestly instead of a broader claim
 * falsely.
 *
 * <h2>What this budget does and does not say</h2>
 *
 * It says: a student with these windows, studying material of this average difficulty, can sustain
 * about this much load per day. It does <b>not</b> say anything about how stressed, tired or
 * motivated they are — the platform collects none of that, and collecting self-declared
 * psychological state tied to an identity is a decision under the LGPD, with minors in the
 * secondary-school pilot, and not an engineering decision. See the README and
 * {@code docs/SINAPSE_ADAPTER.md} §2.
 *
 * <h2>The constants are re-scaled, not copied</h2>
 *
 * The <b>shape</b> of the formula is the concurso path's, so that a ceiling means a comparable kind
 * of thing on both. The <b>constants are not</b>: they are restated for a 1..4 difficulty scale
 * instead of 1..5, because that is the scale this path's demand is measured on. Copying them
 * unchanged was the first version of this class and it was wrong — see
 * {@link DailyLoadBudgetObjective} for the measurement.
 *
 * <p>The formula's shape remains a plausibility choice on both paths, and is on the calibration list
 * in {@code docs/SINAPSE_ADAPTER.md} §4.
 */
public final class SinapseLoadBudget {

    /**
     * The load of an average-difficulty topic <b>on this path's scale</b>.
     *
     * <p>2.5, not the concurso path's 3.0. {@code CognitiveLoadCalculator} is calibrated for
     * {@code cognitiveLoad} on 1..5, whose midpoint is 3; this path's demand comes from
     * {@code effortTier} mapped to 1..4, whose midpoint is 2.5. Crossing the two makes the ceiling
     * systematically generous — measured, 23% of slack at the scale's own midpoint — and the term
     * active in the code while almost never binding in practice. See
     * {@link DailyLoadBudgetObjective} for the measurement and for why the tiers are not stretched
     * onto 1..5 instead.
     */
    private static final double AVERAGE_LOAD_FACTOR = DailyLoadBudgetObjective.AVERAGE_BAND;

    /** The band treated as average, for the pressure interpolation. The same 2.5. */
    private static final double AVERAGE_BAND = DailyLoadBudgetObjective.AVERAGE_BAND;

    /** Bands run 1..4 on this path, so the interpolation spans three steps, not four. */
    private static final double BAND_SPAN = DailyLoadBudgetObjective.BAND_SPAN;

    /** Pressure at the easiest material; 0.9 at the hardest. */
    private static final double PRESSURE_AT_EASIEST = 1.1;

    /** Total swing of the pressure factor across the band range. */
    private static final double PRESSURE_SWING = 0.2;

    /**
     * The smallest ceiling that is not degenerate on this path's scale.
     *
     * <p>1, the easiest band — not the concurso path's 5. That 5 is a load figure on a 1..5 scale
     * and, carried over unchanged, it <b>pinned the ceiling in every instance where the student has
     * under about two hours a day</b>: the computed ceiling came out below 5, the floor replaced it,
     * and the term could not bind however hard the material was. Measured across a spread of
     * calendars, the ceiling was 5 in all seven.
     *
     * <p>1 is the minimum that avoids a zero or negative ceiling and claims nothing beyond that. Any
     * larger value is a judgement about how much load a minimally-available student can bear, and
     * there is no data here for one — so it is on the calibration list in
     * {@code docs/SINAPSE_ADAPTER.md} §4 rather than guessed.
     */
    private static final int MINIMUM_BUDGET = 1;

    private SinapseLoadBudget() {
    }

    /**
     * The daily load ceiling for this request.
     *
     * <h2>It takes the same hours figure the demand side uses, and that is the point</h2>
     *
     * {@code DailyLoadBudgetObjective} computes the plan's expected daily load as
     * {@code hoursPerStudyDay x weightedLoad / totalDays}, where {@code hoursPerStudyDay} is the
     * <b>integer</b> the context carries. An earlier version of this method built the ceiling from
     * the unrounded hours per calendar day instead, and the two are not the same number: the integer
     * is rounded <i>up</i>, so at 2.14 hours a day the demand was computed against 3 and the ceiling
     * against 2.14 — <b>a 40% inflation of the demand relative to its own ceiling</b>, which made the
     * term bind for reasons that had nothing to do with the plan.
     *
     * <p>Taking the same integer makes the comparison reduce to what the term is actually for:
     * whether the plan's allocation-weighted difficulty exceeds the average-difficulty reference.
     * The hours cancel, so a student with more time is not penalised for having it.
     *
     * @param hoursPerStudyDay the whole hours the context carries; the same value the demand uses
     * @param items            the topics in scope, for their mean difficulty band
     * @return the ceiling, at least {@value #MINIMUM_BUDGET}
     */
    public static int of(int hoursPerStudyDay, List<PlanningItem> items) {
        double baseCapacity = hoursPerStudyDay * AVERAGE_LOAD_FACTOR;
        return Math.max(MINIMUM_BUDGET,
                (int) Math.round(baseCapacity * pressureFactor(items)));
    }

    /**
     * Study hours per calendar day of the horizon.
     *
     * <p>Total available minutes spread over the horizon, which is the same shape as the concurso
     * path's "weekly hours divided by seven" — an average over calendar days, not over study days,
     * so a student who studies three evenings a week is not credited with three full days.
     *
     * @param windows          the availability
     * @param planningHorizonDays the horizon in days; at least 1
     * @return average study hours per calendar day, never negative
     */
    public static double hoursPerDay(List<AvailabilityWindow> windows, int planningHorizonDays) {
        long minutes = windows.stream().mapToLong(AvailabilityWindow::getDurationMinutes).sum();
        return minutes / 60.0 / Math.max(1, planningHorizonDays);
    }

    /**
     * Whole study hours per day, for {@code EvolutionContext.hoursPerStudyDay}.
     *
     * <p>Rounded up and floored at 1, matching the concurso assembler: a study day with zero hours
     * is not a study day, and the value divides in the load term.
     */
    public static int wholeHoursPerDay(List<AvailabilityWindow> windows, int planningHorizonDays) {
        return Math.max(1, (int) Math.ceil(hoursPerDay(windows, planningHorizonDays)));
    }

    /** Harder material is more draining: 1.1 at band 1, 1.0 at band 3, 0.9 at band 5. */
    private static double pressureFactor(List<PlanningItem> items) {
        double meanBand = items.stream()
                .mapToInt(PlanningItem::difficultyBand)
                .average()
                .orElse(AVERAGE_BAND);
        return PRESSURE_AT_EASIEST - ((meanBand - 1) / BAND_SPAN) * PRESSURE_SWING;
    }
}
