package com.ia.project.dynamicstudyplanner.benchmark.metric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The <b>single sanctioned way</b> to summarise a fitness/business-metric correlation across
 * benchmark instances. Every report that quotes one number for "how well does the fitness track the
 * business metric overall" must produce it here.
 *
 * <h2>What this replaces, and why</h2>
 *
 * Reports from etapas 03 and 05 pooled all observations — 8 instances x 6 planners — into a single
 * Spearman. That statistic is invalid for this data, and the codebase already said so:
 * {@link PlanMetrics} documents fitness as <em>"not comparable across instances, because the fitness
 * has no normalised unit"</em>. Pooling ranks {@code I4}'s ~0.90 above {@code I8}'s ~0.55 because
 * they are different problems, not because one plan is better, so the pooled coefficient mostly
 * measures which instance is easy. It reversed the sign of the conclusion: pooled, etapa 05 read
 * {@code +0.161}; aggregated correctly it is {@code -0.358}
 * (docs/revisao-ag/06-verificacao-pos-rodada.md §5.2).
 *
 * <h2>Why Fisher z rather than a plain mean</h2>
 *
 * Correlation coefficients are not on an additive scale: the distance from 0.90 to 0.95 carries far
 * more information than 0.10 to 0.15, and the sampling distribution of r is skewed and bounded.
 * Fisher's transform {@code z = artanh(r)} is variance-stabilising — z is approximately normal with
 * variance {@code 1/(n-3)} — so averaging in z space and transforming back with {@code tanh} is the
 * standard way to combine correlations. The alternative considered and rejected was the arithmetic
 * mean of r, which the etapa-06 verification used ad hoc; it understates strong correlations and has
 * no defensible confidence interval.
 *
 * <h2>An honest note on the weights</h2>
 *
 * Weighting by {@code n-3} is the textbook choice, but in <b>this</b> design it does nothing: every
 * instance correlates the same 6 planners, so every weight is {@code 6-3 = 3} and the weighted mean
 * equals the unweighted mean of the z values. The weighting is implemented anyway because the sweeps
 * in {@code HighLoadInstanceLibrary} could one day compare a different number of planners, and a
 * summary statistic that silently assumed equal n would then be wrong. <b>The gain over a plain mean
 * of r here is the variance stabilisation, not the weighting</b> — claiming otherwise would overstate
 * what the method buys.
 *
 * <h2>How to read the result</h2>
 *
 * The aggregate never replaces the per-instance table; it summarises it. Instances whose correlation
 * is undefined — every planner saturates the business metric, so there is nothing to correlate — are
 * excluded and counted separately, because averaging them in as "zero" would fabricate agreement
 * that was never measured.
 */
public final class CorrelationAggregate {

    /**
     * Approximate variance inflation for Spearman's rho relative to Pearson's r under Fisher's
     * transform. The classical {@code 1/(n-3)} applies to Pearson; for rank correlation the variance
     * is about 6% larger (Fieller, Hartley &amp; Pearson, 1957). It cancels out of the weighted mean,
     * so it affects only the confidence interval — where ignoring it would report a narrower interval
     * than the data supports.
     */
    private static final double SPEARMAN_VARIANCE_INFLATION = 1.06;

    /** 95% two-sided normal quantile, for the interval reported in z space. */
    private static final double Z_95 = 1.959963985;

    /**
     * Guards {@code artanh} against a perfect correlation, which is attainable with 6 planners and
     * would otherwise transform to infinity and poison the mean.
     */
    private static final double MAX_ABS_R = 1.0 - 1e-12;

    private CorrelationAggregate() {
    }

    /**
     * One instance's contribution.
     *
     * @param instanceId  the instance the coefficient came from, for reporting
     * @param correlation Spearman's rho on that instance, or {@code NaN} when undefined
     * @param sampleSize  how many planners were correlated on that instance
     */
    public record InstanceCorrelation(String instanceId, double correlation, int sampleSize) {
    }

    /**
     * The aggregate and everything needed to report it honestly.
     *
     * @param correlation      the combined coefficient, back-transformed to the r scale
     * @param lowerBound       lower end of the 95% interval, on the r scale
     * @param upperBound       upper end of the 95% interval, on the r scale
     * @param instancesUsed    how many instances contributed
     * @param instancesSkipped how many were excluded as undefined or too small
     */
    public record Result(
            double correlation,
            double lowerBound,
            double upperBound,
            int instancesUsed,
            int instancesSkipped
    ) {
        /** {@code true} when no instance could contribute, so {@link #correlation()} is {@code NaN}. */
        public boolean isUndefined() {
            return instancesUsed == 0;
        }

        /** Report-ready rendering, always carrying the instance count so the number cannot be quoted bare. */
        public String format() {
            if (isUndefined()) {
                return String.format(Locale.ROOT,
                        "n/d (nenhuma das %d instancias tinha correlacao definida)", instancesSkipped);
            }
            return String.format(Locale.ROOT, "%+.3f [IC95%% %+.3f, %+.3f], n = %d instancias%s",
                    correlation, lowerBound, upperBound, instancesUsed,
                    instancesSkipped == 0 ? "" : " (" + instancesSkipped + " excluidas: correlacao indefinida)");
        }
    }

    /**
     * Combines per-instance correlations via the weighted Fisher z transform.
     *
     * @param perInstance one entry per benchmark instance; undefined entries are counted and skipped
     * @return the aggregate, its 95% interval and how many instances it rests on
     */
    public static Result aggregate(List<InstanceCorrelation> perInstance) {
        if (perInstance == null || perInstance.isEmpty()) {
            return new Result(Double.NaN, Double.NaN, Double.NaN, 0, 0);
        }

        double weightedZ = 0.0;
        double totalWeight = 0.0;
        int used = 0;
        int skipped = 0;

        for (InstanceCorrelation entry : perInstance) {
            // n <= 3 leaves no degrees of freedom for the transform's variance, so such an instance
            // carries no information here — skipping it is not the same as scoring it zero.
            double weight = entry.sampleSize() - 3.0;
            if (Double.isNaN(entry.correlation()) || weight <= 0.0) {
                skipped++;
                continue;
            }

            weightedZ += weight * fisherZ(entry.correlation());
            totalWeight += weight;
            used++;
        }

        if (used == 0) {
            return new Result(Double.NaN, Double.NaN, Double.NaN, 0, skipped);
        }

        double meanZ = weightedZ / totalWeight;
        double standardError = Math.sqrt(SPEARMAN_VARIANCE_INFLATION / totalWeight);

        return new Result(
                Math.tanh(meanZ),
                Math.tanh(meanZ - Z_95 * standardError),
                Math.tanh(meanZ + Z_95 * standardError),
                used,
                skipped);
    }

    /**
     * Convenience overload for the common case where every instance correlates the same number of
     * planners.
     *
     * @param correlations one coefficient per instance, {@code NaN} where undefined
     * @param sampleSize   planners correlated on each instance
     */
    public static Result aggregate(List<Double> correlations, int sampleSize) {
        List<InstanceCorrelation> entries = new ArrayList<>(correlations.size());
        for (int i = 0; i < correlations.size(); i++) {
            entries.add(new InstanceCorrelation("#" + i, correlations.get(i), sampleSize));
        }
        return aggregate(entries);
    }

    /**
     * Fisher's transform {@code z = artanh(r) = 0.5 * ln((1+r)/(1-r))}, clamped so a perfect
     * correlation does not become infinite. Written out because {@code java.lang.Math} has the
     * hyperbolic functions but not their inverses.
     */
    private static double fisherZ(double r) {
        double clamped = Math.clamp(r, -MAX_ABS_R, MAX_ABS_R);
        return 0.5 * Math.log((1.0 + clamped) / (1.0 - clamped));
    }
}
