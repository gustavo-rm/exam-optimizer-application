package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Baseline 2 — greedy by priority: allocate strictly in proportion to the subject's weight in the
 * exam syllabus, with no spaced repetition and no cognitive-load balancing.
 * <p>
 * This is the rule a competent human planner would apply with a spreadsheet: give each subject a
 * share of the time proportional to how much it is worth on the exam. It is the <b>contract
 * baseline</b> — the automated regression test in
 * {@code GeneticAlgorithmVsBaselinesTest} fails if the production GA cannot stay within a defined
 * margin of it, because a GA that loses to a spreadsheet has no defensible reason to exist.
 * <p>
 * Integer rounding uses the largest-remainder (Hare) method so the allocation sums exactly to the
 * budget without the drift that naive per-subject rounding would introduce.
 */
public final class GreedyPriorityBaseline implements PlanningStrategy {

    @Override
    public String id() {
        return "guloso-prioridade";
    }

    @Override
    public String displayName() {
        return "Guloso por prioridade (peso de edital)";
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
        if (remaining == 0) {
            return new StudyPlan(days);
        }

        Map<PlanningItem, Double> importance = context.importanceScores();
        double totalImportance = items.stream()
                .mapToDouble(s -> Math.max(0.0, importance.getOrDefault(s, 0.0)))
                .sum();

        if (totalImportance <= 0.0) {
            // Degenerate payload (every item orphaned): fall back to an even split so the
            // baseline still returns a valid plan rather than throwing.
            return new UniformSplitBaseline().plan(instance, context, seed);
        }

        // Largest-remainder apportionment: floor everyone, then hand out the leftover days to the
        // items with the biggest fractional parts.
        record Share(PlanningItem item, int whole, double remainder) {
        }
        List<Share> shares = items.stream()
                .map(s -> {
                    double exact = remaining * Math.max(0.0, importance.getOrDefault(s, 0.0)) / totalImportance;
                    int whole = (int) Math.floor(exact);
                    return new Share(s, whole, exact - whole);
                })
                .toList();

        int handedOut = 0;
        for (Share share : shares) {
            days.merge(share.item(), share.whole(), Integer::sum);
            handedOut += share.whole();
        }

        List<Share> byRemainder = shares.stream()
                .sorted(Comparator.comparingDouble(Share::remainder).reversed()
                        .thenComparing(s -> s.item().name()))
                .toList();

        for (int i = 0; handedOut < remaining; i++, handedOut++) {
            days.merge(byRemainder.get(i % byRemainder.size()).item(), 1, Integer::sum);
        }

        return new StudyPlan(days);
    }
}
