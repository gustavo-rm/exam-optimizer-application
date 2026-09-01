package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
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
        List<Subject> subjects = Allocations.orderedSubjects(context);
        Map<Subject, Integer> days = Allocations.atMinimums(subjects, context);
        int remaining = Allocations.remainingBudget(days, instance.totalStudyDays());
        if (remaining == 0) {
            return new StudyPlan(days);
        }

        Map<Subject, Double> importance = context.importanceScores();
        double totalImportance = subjects.stream()
                .mapToDouble(s -> Math.max(0.0, importance.getOrDefault(s, 0.0)))
                .sum();

        if (totalImportance <= 0.0) {
            // Degenerate payload (every subject orphaned): fall back to an even split so the
            // baseline still returns a valid plan rather than throwing.
            return new UniformSplitBaseline().plan(instance, context, seed);
        }

        // Largest-remainder apportionment: floor everyone, then hand out the leftover days to the
        // subjects with the biggest fractional parts.
        record Share(Subject subject, int whole, double remainder) {
        }
        List<Share> shares = subjects.stream()
                .map(s -> {
                    double exact = remaining * Math.max(0.0, importance.getOrDefault(s, 0.0)) / totalImportance;
                    int whole = (int) Math.floor(exact);
                    return new Share(s, whole, exact - whole);
                })
                .toList();

        int handedOut = 0;
        for (Share share : shares) {
            days.merge(share.subject(), share.whole(), Integer::sum);
            handedOut += share.whole();
        }

        List<Share> byRemainder = shares.stream()
                .sorted(Comparator.comparingDouble(Share::remainder).reversed()
                        .thenComparing(s -> s.subject().name()))
                .toList();

        for (int i = 0; handedOut < remaining; i++, handedOut++) {
            days.merge(byRemainder.get(i % byRemainder.size()).subject(), 1, Integer::sum);
        }

        return new StudyPlan(days);
    }
}
