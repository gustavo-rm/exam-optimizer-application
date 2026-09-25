package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Greedy by importance: allocate strictly in proportion to each item's importance, with no spaced
 * repetition and no cognitive-load balancing.
 * <p>
 * This is the rule a competent human planner would apply with a spreadsheet: give each item a share
 * of the time proportional to how much it is worth. It reads
 * {@code EvolutionContext.importanceScores()}, so it follows whichever meaning of importance the
 * context was built with and names none of its own — it said "exam syllabus weight" while that was
 * the only meaning there was, which was a description of the domain and not of this class.
 * <p>
 * It used to be a contract baseline: a regression test failed if the production GA could not stay
 * within a defined margin of it. That test measured the removed concurso path and went with it, so
 * nothing enforces the margin today. See this package's {@code package-info} for why none of these
 * planners is in the engine matrix.
 * <p>
 * Integer rounding uses the largest-remainder (Hare) method so the allocation sums exactly to the
 * budget without the drift that naive per-item rounding would introduce.
 */
public final class GreedyPriorityBaseline implements PlanningStrategy {

    @Override
    public String id() {
        return "guloso-prioridade";
    }

    @Override
    public String displayName() {
        return "Guloso por importancia";
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
