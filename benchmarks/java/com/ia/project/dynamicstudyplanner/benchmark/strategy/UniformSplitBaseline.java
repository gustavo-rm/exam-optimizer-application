package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.List;
import java.util.Map;

/**
 * Baseline 3 — uniform split: after the minimum floor, every subject receives the same number of
 * additional days, with any leftover distributed round-robin in the deterministic subject order.
 * <p>
 * This baseline ignores the syllabus entirely. It is the naive default a student falls back on when
 * they have no information about relative importance, and it serves as the lower bound on
 * syllabus-awareness: the gap between this and {@link GreedyPriorityBaseline} measures how much the
 * edital weighting is worth on its own, before any search is involved.
 */
public final class UniformSplitBaseline implements PlanningStrategy {

    @Override
    public String id() {
        return "uniforme";
    }

    @Override
    public String displayName() {
        return "Divisao uniforme";
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

        int each = remaining / subjects.size();
        int leftover = remaining % subjects.size();

        for (int i = 0; i < subjects.size(); i++) {
            int extra = each + (i < leftover ? 1 : 0);
            days.merge(subjects.get(i), extra, Integer::sum);
        }
        return new StudyPlan(days);
    }
}
