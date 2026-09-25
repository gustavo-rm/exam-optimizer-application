package com.ia.project.dynamicstudyplanner.benchmark.instance;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

/**
 * One measured instance: a request, and the factorial coordinates that produced it.
 *
 * <h2>The coordinates travel with the request on purpose</h2>
 *
 * Every output row carries them, so a difference between two engines can be read against the axis
 * that produced it rather than against an opaque instance name. "The gap appears below tightness
 * 1.0" is a finding; "the gap appears on I4" is a lookup.
 *
 * @param id                stable identifier, derived from the coordinates so it cannot drift
 * @param topics            how many topics are in scope
 * @param density           prerequisite edges per topic, as generated
 * @param tightness         available over demanded minutes; below 1.0 the calendar cannot hold the
 *                          syllabus and any plan must be partial
 * @param horizonDays       calendar days the availability is spread over
 * @param request           the request itself, ready for {@code PlanEngineSelector}
 * @param demandedMinutes   total {@code estimatedMinutes} of every topic — tightness's denominator
 * @param availableMinutes  usable minutes inside the horizon — tightness's numerator
 */
public record BenchmarkInstance(
        String id,
        int topics,
        double density,
        double tightness,
        int horizonDays,
        PlanRequest request,
        long demandedMinutes,
        long availableMinutes) {

    /**
     * Sessions the allocation-level strategies distribute.
     *
     * <h2>This is the harness's budget, not production's</h2>
     *
     * {@code GeneticPlanEngine} derives its own session budget from availability and mean topic
     * length, and that derivation is private to it. This one is <b>not</b> a copy and makes no
     * claim to equal it: it exists so the allocation-level strategies in {@code strategy/**} all
     * distribute the <b>same</b> total and are therefore comparable with each other. Comparing one
     * of them against an engine would be comparing two different budgets — which is exactly why
     * they are not in the engine matrix.
     *
     * @return at least one session per topic, plus what the calendar can afford beyond that
     */
    public int totalStudyDays() {
        return Math.max(topics, (int) Math.floorDiv(availableMinutes, Math.max(1L, meanMinutes())));
    }

    /** Population for {@code BestOfRandomBaseline}, so its draw budget matches one generation. */
    public int populationSize() {
        return 40;
    }

    private long meanMinutes() {
        return Math.max(1L, demandedMinutes / Math.max(1, topics));
    }
}
