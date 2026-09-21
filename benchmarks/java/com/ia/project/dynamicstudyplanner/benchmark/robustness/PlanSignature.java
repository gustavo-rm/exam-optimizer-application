package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Canonical, order-independent representation of a {@link StudyPlan}, used to answer
 * "is this the same plan?" and "how different are these two plans?".
 * <p>
 * {@code StudyPlan} wraps a {@code HashMap}, so two equal plans can iterate in different orders and
 * {@code toString()} is not a reliable identity. The signature sorts by item name, which makes
 * comparison independent of map internals — a plan is identified by its allocation, nothing else.
 */
public final class PlanSignature {

    private PlanSignature() {
    }

    /**
     * A stable textual identity for a plan: {@code itemA=3|itemB=7|...}, sorted by name.
     * Two plans have the same signature exactly when they allocate the same days to the same
     * items.
     */
    public static String of(StudyPlan plan) {
        return plan.getDaysPerItem().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(PlanningItem::name)))
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .reduce((a, b) -> a + "|" + b)
                .orElse("<vazio>");
    }

    /**
     * Manhattan distance between two allocations, in days.
     * <p>
     * Because both plans allocate the same total budget, this counts each moved day twice — a plan
     * differing by one day transferred between two items has distance 2. Use
     * {@link #daysMoved} for the interpretable figure.
     */
    public static int l1Distance(StudyPlan left, StudyPlan right) {
        Set<PlanningItem> items = new TreeSet<>(Comparator.comparing(PlanningItem::name));
        items.addAll(left.getDaysPerItem().keySet());
        items.addAll(right.getDaysPerItem().keySet());

        int distance = 0;
        for (PlanningItem item : items) {
            distance += Math.abs(left.getDaysForItem(item) - right.getDaysForItem(item));
        }
        return distance;
    }

    /**
     * Number of study days that would have to be moved to turn one plan into the other — half the
     * L1 distance, since every day removed from one item is added to another.
     */
    public static double daysMoved(StudyPlan left, StudyPlan right) {
        return l1Distance(left, right) / 2.0;
    }

    /**
     * Days moved as a fraction of the total budget: 0.0 means identical plans, 1.0 means the two
     * plans share no allocation at all.
     */
    public static double normalizedDistance(StudyPlan left, StudyPlan right) {
        int budget = left.getTotalDays();
        return budget == 0 ? 0.0 : daysMoved(left, right) / budget;
    }
}
