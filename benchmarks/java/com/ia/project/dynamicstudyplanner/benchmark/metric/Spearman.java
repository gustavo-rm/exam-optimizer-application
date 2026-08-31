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
            while (j + 1 < n && values.get(order[j + 1]).equals(values.get(order[i]))) {
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

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }
}
