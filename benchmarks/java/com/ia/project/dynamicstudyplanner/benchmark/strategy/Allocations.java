package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared allocation primitives for the baselines.
 * <p>
 * Every baseline starts from the same feasible floor — the minimum days per subject computed by the
 * production {@code BaselineCalculator} — and then distributes the remaining budget according to its
 * own rule. Keeping the floor logic in one place guarantees that the baselines differ only in their
 * distribution policy, which is what the comparison is meant to isolate.
 */
final class Allocations {

    private Allocations() {
    }

    /**
     * Subjects in a stable, deterministic order.
     * <p>
     * {@code EvolutionContext.importanceScores()} is a {@code HashMap}, whose iteration order depends
     * on hash codes. Sorting by subject name makes every baseline reproducible run to run.
     */
    static List<PlanningItem> orderedItems(EvolutionContext context) {
        List<PlanningItem> items = new ArrayList<>(context.importanceScores().keySet());
        items.sort(java.util.Comparator.comparing(PlanningItem::name));
        return items;
    }

    /**
     * Allocates each subject its minimum required days — the hard constraint every planner must
     * satisfy, mirroring {@code StudyPlanFactory.createRandomPlan} and
     * {@code StudyPlan.meetsMinimumConstraints}, which both default a missing entry to 1.
     *
     * @return a mutable map seeded at the feasible floor, in the deterministic subject order
     */
    static Map<PlanningItem, Integer> atMinimums(List<PlanningItem> items, EvolutionContext context) {
        Map<PlanningItem, Integer> days = new LinkedHashMap<>();
        for (PlanningItem item : items) {
            days.put(item, context.minimumDaysPerItem().getOrDefault(item, 1));
        }
        return days;
    }

    /**
     * Days left to distribute after the minimum floor is satisfied.
     *
     * @throws IllegalStateException if the floor already exceeds the budget, which would mean the
     *                               instance is infeasible and should never have been built
     */
    static int remainingBudget(Map<PlanningItem, Integer> days, int totalStudyDays) {
        int allocated = days.values().stream().mapToInt(Integer::intValue).sum();
        int remaining = totalStudyDays - allocated;
        if (remaining < 0) {
            throw new IllegalStateException(
                    "Instancia infactivel: piso de dias minimos (" + allocated
                            + ") excede o orcamento total (" + totalStudyDays + ").");
        }
        return remaining;
    }
}
