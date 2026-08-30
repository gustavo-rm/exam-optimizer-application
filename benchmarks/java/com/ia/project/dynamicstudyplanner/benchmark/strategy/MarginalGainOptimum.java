package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Reference solver — the exact optimum of the objective the production fitness actually maximises.
 * <p>
 * This is not one of the three baselines the task asked for. It is included because the audit
 * (docs/revisao-ag/01-auditoria-fitness.md §1.3, §3.4) derived that, once the inert components are
 * removed, the production fitness reduces to
 *
 * <pre>
 *   maximise  sum_s  I_s * ln(1 + d_s)
 *   s.t.      sum_s d_s = D,   d_s &gt;= m_s,   d_s integer
 * </pre>
 *
 * which is a separable concave maximisation under a budget constraint with box lower bounds. For
 * that shape, allocating each remaining day to the subject with the largest marginal gain
 * {@code I_s * (ln(2 + d_s) - ln(1 + d_s))} is provably optimal — the marginal gains are decreasing
 * in {@code d_s}, so the standard exchange argument applies. Cost is O(D log n).
 * <p>
 * Including it turns an analytical claim into a measurable one. If the GA never beats this solver,
 * the audit's central finding is confirmed empirically: the GA is approximating, stochastically, a
 * problem that has a closed-form answer. If the GA <em>does</em> beat it on some instance, then some
 * component the audit judged inert is in fact active, and the audit needs revising. Either outcome
 * is informative, which is why this column belongs in the report.
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
        List<Subject> subjects = Allocations.orderedSubjects(context);
        Map<Subject, Integer> days = Allocations.atMinimums(subjects, context);
        int remaining = Allocations.remainingBudget(days, instance.totalStudyDays());

        Map<Subject, Double> importance = context.importanceScores();

        // Max-heap on marginal gain. Ties break on subject name so the result is reproducible.
        record Candidate(Subject subject, double gain) {
        }
        PriorityQueue<Candidate> heap = new PriorityQueue<>(
                Comparator.comparingDouble(Candidate::gain).reversed()
                        .thenComparing(c -> c.subject().name()));

        for (Subject subject : subjects) {
            heap.add(new Candidate(subject, marginalGain(importance, subject, days.get(subject))));
        }

        for (int i = 0; i < remaining; i++) {
            Candidate best = heap.poll();
            if (best == null) {
                break;
            }
            int updated = days.merge(best.subject(), 1, Integer::sum);
            heap.add(new Candidate(best.subject(), marginalGain(importance, best.subject(), updated)));
        }

        return new StudyPlan(days);
    }

    /** Gain from moving subject {@code s} from {@code d} days to {@code d + 1}. */
    private static double marginalGain(Map<Subject, Double> importance, Subject subject, int currentDays) {
        double weight = importance.getOrDefault(subject, 0.0);
        return weight * (Math.log(2.0 + currentDays) - Math.log(1.0 + currentDays));
    }
}
