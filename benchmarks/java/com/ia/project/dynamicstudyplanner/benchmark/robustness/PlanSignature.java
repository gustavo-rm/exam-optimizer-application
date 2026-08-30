package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Canonical, order-independent representation of a {@link StudyPlan}, used to answer
 * "is this the same plan?" and "how different are these two plans?".
 * <p>
 * {@code StudyPlan} wraps a {@code HashMap}, so two equal plans can iterate in different orders and
 * {@code toString()} is not a reliable identity. The signature sorts by subject name, which makes
 * comparison independent of map internals — a plan is identified by its allocation, nothing else.
 */
public final class PlanSignature {

    private PlanSignature() {
    }

    /**
     * A stable textual identity for a plan: {@code subjectA=3|subjectB=7|...}, sorted by name.
     * Two plans have the same signature exactly when they allocate the same days to the same
     * subjects.
     */
    public static String of(StudyPlan plan) {
        return plan.getDaysPerSubject().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Subject::name)))
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .reduce((a, b) -> a + "|" + b)
                .orElse("<vazio>");
    }

    /**
     * Manhattan distance between two allocations, in days.
     * <p>
     * Because both plans allocate the same total budget, this counts each moved day twice — a plan
     * differing by one day transferred between two subjects has distance 2. Use
     * {@link #daysMoved} for the interpretable figure.
     */
    public static int l1Distance(StudyPlan left, StudyPlan right) {
        Set<Subject> subjects = new TreeSet<>(Comparator.comparing(Subject::name));
        subjects.addAll(left.getDaysPerSubject().keySet());
        subjects.addAll(right.getDaysPerSubject().keySet());

        int distance = 0;
        for (Subject subject : subjects) {
            distance += Math.abs(left.getDaysForSubject(subject) - right.getDaysForSubject(subject));
        }
        return distance;
    }

    /**
     * Number of study days that would have to be moved to turn one plan into the other — half the
     * L1 distance, since every day removed from one subject is added to another.
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
