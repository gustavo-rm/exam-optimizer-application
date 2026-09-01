package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanMetrics;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleStatus;

import java.util.List;

/**
 * Aggregated result of running one planner on one instance across all repetitions.
 * <p>
 * Deterministic planners contribute a single repetition with zero spread. The production GA is
 * non-deterministic (see {@code ProductionGeneticAlgorithm}), so its spread across repetitions is
 * reported rather than hidden — and it is what calibrates the regression threshold in the test.
 */
public record StrategyOutcome(
        String instanceId,
        String strategyId,
        String strategyName,
        int repetitions,
        double meanFitness,
        double stdDevFitness,
        double minFitness,
        double maxFitness,
        double meanElapsedMillis,
        double meanRetentionAtExam,
        double meanPctInRetentionWindow,
        double meanCognitiveOverloadDays,
        double meanScheduledHoursRatio,
        double meanTopSubjectShare,
        ScheduleStatus scheduleStatus
) {

    /** Coefficient of variation of fitness, in percent. The run-to-run noise floor of the planner. */
    public double fitnessCoefficientOfVariationPct() {
        return meanFitness == 0.0 ? 0.0 : 100.0 * stdDevFitness / Math.abs(meanFitness);
    }

    /**
     * Builds the aggregate from the individual repetitions.
     *
     * @param samples one entry per repetition; must not be empty
     */
    public static StrategyOutcome of(String instanceId, String strategyId, String strategyName,
                                     List<PlanMetrics> samples) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma repeticao para " + strategyId + " em " + instanceId);
        }

        double meanFitness = mean(samples.stream().mapToDouble(PlanMetrics::fitness).toArray());
        double std = stdDev(samples.stream().mapToDouble(PlanMetrics::fitness).toArray(), meanFitness);

        return new StrategyOutcome(
                instanceId,
                strategyId,
                strategyName,
                samples.size(),
                meanFitness,
                std,
                samples.stream().mapToDouble(PlanMetrics::fitness).min().orElseThrow(),
                samples.stream().mapToDouble(PlanMetrics::fitness).max().orElseThrow(),
                mean(samples.stream().mapToDouble(PlanMetrics::elapsedMillis).toArray()),
                mean(samples.stream().mapToDouble(PlanMetrics::meanRetentionAtExam).toArray()),
                mean(samples.stream().mapToDouble(PlanMetrics::pctInRetentionWindow).toArray()),
                mean(samples.stream().mapToDouble(PlanMetrics::cognitiveOverloadDays).toArray()),
                mean(samples.stream().mapToDouble(PlanMetrics::scheduledHoursRatio).toArray()),
                mean(samples.stream().mapToDouble(PlanMetrics::topSubjectShare).toArray()),
                samples.get(0).scheduleStatus()
        );
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /** Sample standard deviation (n-1). Returns 0 for a single observation. */
    private static double stdDev(double[] values, double mean) {
        if (values.length < 2) {
            return 0.0;
        }
        double acc = 0.0;
        for (double v : values) {
            acc += (v - mean) * (v - mean);
        }
        return Math.sqrt(acc / (values.length - 1));
    }
}
