package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Baseline 1 — random allocation respecting only the hard constraints.
 * <p>
 * Every subject receives its minimum required days, and the remaining budget is scattered uniformly
 * at random. The result always satisfies the two invariants the production operators preserve:
 * the day total equals the budget, and no subject falls below its floor.
 * <p>
 * This is the reference point that answers "how much does the search actually buy?". A GA that does
 * not clearly beat random allocation is not searching; it is sampling. Note that this is an
 * independent implementation rather than a call to the production {@code StudyPlanFactory}: the
 * baseline must not share code with the system under test.
 */
public final class RandomBaseline implements PlanningStrategy {

    @Override
    public String id() {
        return "aleatorio";
    }

    @Override
    public String displayName() {
        return "Aleatorio (so restricoes rigidas)";
    }

    @Override
    public boolean deterministic() {
        return false;
    }

    @Override
    public StudyPlan plan(BenchmarkInstance instance, EvolutionContext context, long seed) {
        Random random = new Random(seed);
        List<Subject> subjects = Allocations.orderedSubjects(context);
        Map<Subject, Integer> days = Allocations.atMinimums(subjects, context);

        int remaining = Allocations.remainingBudget(days, instance.totalStudyDays());
        for (int i = 0; i < remaining; i++) {
            Subject picked = subjects.get(random.nextInt(subjects.size()));
            days.merge(picked, 1, Integer::sum);
        }
        return new StudyPlan(days);
    }
}
