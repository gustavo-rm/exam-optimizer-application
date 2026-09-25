package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared allocation primitives for the planners in this package.
 * <p>
 * Every one of them starts from the same feasible floor — {@code context.minimumDaysPerItem()},
 * which the production assembler fills — and then distributes the remaining budget according to its
 * own rule. Keeping the floor logic in one place guarantees that they differ only in their
 * distribution policy, which is what a comparison between them is meant to isolate.
 */
final class Allocations {

    private Allocations() {
    }

    /**
     * Items in a stable, deterministic order.
     * <p>
     * Sorting by item name rather than trusting the map's iteration order makes every planner here
     * reproducible run to run, whatever map {@code EvolutionContext.importanceScores()} happens to
     * be. It is the same rule the production adapter follows, for the same reason.
     */
    static List<PlanningItem> orderedItems(EvolutionContext context) {
        List<PlanningItem> items = new ArrayList<>(context.importanceScores().keySet());
        items.sort(java.util.Comparator.comparing(PlanningItem::name));
        return items;
    }

    /**
     * Allocates each item its minimum required sessions — the hard constraint every planner must
     * satisfy, mirroring {@code StudyPlanFactory.createRandomPlan} and
     * {@code StudyPlan.meetsMinimumConstraints}, which both default a missing entry to 1.
     *
     * @return a mutable map seeded at the feasible floor, in the deterministic item order
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
