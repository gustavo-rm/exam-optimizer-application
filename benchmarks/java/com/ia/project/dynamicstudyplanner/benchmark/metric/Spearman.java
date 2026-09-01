package com.ia.project.dynamicstudyplanner.benchmark.metric;

import java.util.List;

/**
 * Spearman rank correlation, used to test whether maximising the fitness helps or hurts the business
 * metric it is supposed to serve.
 * <p>
 * Rank correlation rather than Pearson because the question is about <em>ordering</em>: does the
 * planner the fitness ranks first also deliver the best retention? The absolute scales are different
 * and irrelevant to that question. Ties get average ranks.
 * <p>
 * This is the measurement behind docs/revisao-ag/03-validacao.md §5 and
 * docs/revisao-ag/06-regime-alta-carga.md.
 */
public final class Spearman {

    private Spearman() {
    }

    /**
     * @return the coefficient in [-1,1], or {@code Double.NaN} when either series is constant — the
     *         case where the correlation is genuinely undefined rather than zero, which happens
     *         whenever every planner saturates the retention metric
     */
    public static double correlation(List<Double> xs, List<Double> ys) {
        if (xs.size() != ys.size() || xs.size() < 2) {
            return Double.NaN;
        }

        double[] rx = ranks(xs);
        double[] ry = ranks(ys);

        double meanX = mean(rx);
        double meanY = mean(ry);

        double numerator = 0.0;
        double sumSqX = 0.0;
        double sumSqY = 0.0;
        for (int i = 0; i < rx.length; i++) {
            double dx = rx[i] - meanX;
            double dy = ry[i] - meanY;
            numerator += dx * dy;
            sumSqX += dx * dx;
            sumSqY += dy * dy;
        }

        double denominator = Math.sqrt(sumSqX * sumSqY);
        return denominator == 0.0 ? Double.NaN : numerator / denominator;
    }

    /**
     * Tolerance for treating two values as tied.
     * <p>
     * <b>Not cosmetic.</b> Tie detection used exact {@code Double.equals}, and the series being
     * ranked are means over repetitions: seven repetitions each scoring exactly {@code 0.975}
     * average to {@code 0.9749999999999999}, one ulp away from a deterministic planner's exact
     * {@code 0.975}. The tie was missed, the three tied planners got distinct ranks, and
     * {@code I8-escala} read {@code -0.928} instead of its true {@code -0.880} - a difference that
     * moved the canonical aggregate from {@code -0.106} to {@code -0.171}.
     * <p>
     * Worse, the artifact was path-dependent: reports that round-tripped through the CSV parsed
     * {@code "0.9750"} back to an exact {@code 0.975} and saw the tie, while reports computing in
     * memory did not. Two code paths, same data, different published coefficient.
     * <p>
     * {@code 1e-9} is far below any real difference in these series - the business metric is a count
     * over a total, so genuine gaps are at least {@code 1/40} - and far above accumulated rounding.
     */
    private static final double TIE_TOLERANCE = 1e-9;

    /** Average ranks, so tied values share a rank instead of getting an arbitrary order. */
    private static double[] ranks(List<Double> values) {
        int n = values.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(values.get(a), values.get(b)));

        double[] result = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && tied(values.get(order[j + 1]), values.get(order[i]))) {
                j++;
            }
            double averageRank = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) {
                result[order[k]] = averageRank;
            }
            i = j + 1;
        }
        return result;
    }

    /** Whether two values are equal to within {@link #TIE_TOLERANCE}. */
    private static boolean tied(double a, double b) {
        return Math.abs(a - b) <= TIE_TOLERANCE;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }
}
