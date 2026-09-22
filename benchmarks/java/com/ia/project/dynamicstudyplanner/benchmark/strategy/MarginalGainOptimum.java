package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.LearningModel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Reference solver — the exact optimum of the syllabus-mastery objective {@code O1} alone.
 * <p>
 * This is not one of the three baselines the task asked for. It exists to keep an analytical claim
 * measurable. {@code O1} is
 *
 * <pre>
 *   maximise  sum_s  normalizedImportance(s) * (1 - exp(-d_s / tau_s))
 *   s.t.      sum_s d_s = D,   d_s &gt;= m_s,   d_s integer
 * </pre>
 *
 * a separable concave maximisation under a budget constraint with box lower bounds. For that shape,
 * handing each remaining day to the subject with the largest marginal gain is provably optimal — the
 * gains decrease in {@code d_s}, so the standard exchange argument applies. Cost is O(D log n),
 * microseconds against the GA's tens of milliseconds.
 * <p>
 * <b>It is the optimum of O1, not of the full fitness.</b> While O1 was the only objective, the GA
 * could at best tie this solver, which is what docs/revisao-ag/03-validacao.md measured and what
 * made the GA hard to justify. Once objectives that are not separable in {@code d_s} join the
 * aggregate, this solver stops being an upper bound and the gap between it and the GA becomes the
 * measurable value of running a search at all.
 */
public final class MarginalGainOptimum implements PlanningStrategy {

    @Override
    public String id() {
        return "otimo-exato";
    }

    @Override
    public String displayName() {
        return "Otimo exato da fitness atual (guloso marginal)";
    }

    @Override
    public boolean deterministic() {
        return true;
    }

    @Override
    public StudyPlan plan(BenchmarkInstance instance, EvolutionContext context, long seed) {
        List<PlanningItem> items = Allocations.orderedItems(context);
        Map<PlanningItem, Integer> days = Allocations.atMinimums(items, context);
        int remaining = Allocations.remainingBudget(days, instance.totalStudyDays());

        Map<PlanningItem, Double> importance = context.normalizedImportance();

        // Max-heap on marginal gain. Ties break on item name so the result is reproducible.
        record Candidate(PlanningItem item, double gain) {
        }
        PriorityQueue<Candidate> heap = new PriorityQueue<>(
                Comparator.comparingDouble(Candidate::gain).reversed()
                        .thenComparing(c -> c.item().name()));

        for (PlanningItem item : items) {
            heap.add(new Candidate(item, marginalGain(importance, item, days.get(item))));
        }

        for (int i = 0; i < remaining; i++) {
            Candidate best = heap.poll();
            if (best == null) {
                break;
            }
            int updated = days.merge(best.item(), 1, Integer::sum);
            heap.add(new Candidate(best.item(), marginalGain(importance, best.item(), updated)));
        }

        return new StudyPlan(days);
    }

    /**
     * Gain from moving item {@code s} from {@code d} days to {@code d + 1}, under the syllabus
     * mastery objective {@code O1}.
     * <p>
     * Mirrors {@code ScoreGainObjective}: {@code importance * (1 - exp(-d/tau))}, so the marginal
     * gain is {@code importance * (exp(-d/tau) - exp(-(d+1)/tau))}. The curve is concave and
     * separable, so greedy on this quantity is the exact optimum <b>of O1 alone</b>. It stops being
     * the optimum of the full fitness as soon as non-separable terms join the aggregate — which is
     * the point at which the GA stops being redundant.
     */
    private static double marginalGain(Map<PlanningItem, Double> importance, PlanningItem item, int currentDays) {
        double weight = importance.getOrDefault(item, 0.0);
        return weight * (LearningModel.mastery(item.difficultyBand(), currentDays + 1)
                - LearningModel.mastery(item.difficultyBand(), currentDays));
    }
}
