package com.ia.project.dynamicstudyplanner.benchmark.metric;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;

/**
 * Group (c): what the answer cost to produce.
 *
 * <h2>Why the harness times the call itself</h2>
 *
 * {@code metadata.elapsedMillis} is <b>zero by construction</b> on both engines, and deliberately
 * so: a measured duration in the answer would make two runs of the same seeded request differ and
 * break the reproducibility the whole measurement rests on. The number is therefore not available
 * from the answer and has to be taken from outside it — around the call, on the harness's own
 * clock.
 *
 * <p>The figure that follows is wall time on one machine, including the placement stage and the
 * invariant checks, and it is comparable <b>between the engines in the same run</b> and nowhere
 * else. It is not a benchmark of the algorithm in isolation and it does not survive a change of
 * machine, which is why the report states the machine next to it.
 *
 * <p>It is recorded in <b>microseconds</b>, not milliseconds, because a millisecond clock cannot
 * resolve the greedy baseline: measured at this size it answers in well under one, so a millisecond
 * column reports its median as zero and makes the cost of the search unquotable as a ratio. The
 * first runs in a JVM also carry the JIT's warm-up and sit far above the median; the report reads
 * medians rather than means for that reason and says so.
 *
 * <h2>Why evaluations are derived rather than counted</h2>
 *
 * Counting them would mean a counter inside {@code Population}, on the production path, existing
 * only for the harness. The count is not in doubt: {@code Population.calculateFitness} is called in
 * exactly two places — once by {@code DefaultPopulationGenerator.generate} and once per generation
 * by {@code GeneticAlgorithm.evolvePopulation} — while selection and the crossover operators read
 * the cached value on {@code Individual}. So
 *
 * <pre>
 *   evaluations = populationSize * (1 + generations)
 * </pre>
 *
 * exactly, plus the single {@code explain} the engine runs on the winning plan. The greedy baseline
 * evolves nothing and evaluates nothing, and reports zero on both counts — a true zero, not a
 * missing value.
 *
 * @param generations    generations the engine reported running; 0 for a non-evolutionary engine
 * @param populationSize individuals per generation; 0 for a non-evolutionary engine
 * @param evaluations    fitness evaluations the search performed, derived as above
 * @param elapsedMicros  wall time around the engine call, measured by the harness
 */
public record CostMetrics(int generations, int populationSize, long evaluations,
        long elapsedMicros) {

    /**
     * Reads the cost of one run.
     *
     * @param response       the answer, for the generation count the engine reports
     * @param populationSize the configured population, or 0 when the engine evolves nothing
     * @param elapsedMicros  the harness's own measurement
     * @return the metrics
     */
    public static CostMetrics of(PlanResponse response, int populationSize, long elapsedMicros) {
        int generations = response.metadata().generations();
        long evaluations = populationSize == 0 ? 0L : (long) populationSize * (1L + generations);
        return new CostMetrics(generations, populationSize, evaluations, elapsedMicros);
    }
}
