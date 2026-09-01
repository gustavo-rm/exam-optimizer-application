package com.ia.project.dynamicstudyplanner.ga.factory;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

/**
 * Factory for creating instances of {@link StudyPlan}.
 * <p>
 * This class has a single, critical responsibility: to generate valid, random initial plans
 * that serve as the chromosomes for the starting population of the genetic algorithm. A diverse
 * initial population is essential for the success of the evolutionary process, and the simple
 * randomization used here is the most effective way to achieve that goal.
 */
public final class StudyPlanFactory {

    /**
     * Creates a random {@code StudyPlan} ensuring that all minimum day constraints are met.
     * <p>
     * The process is intentionally straightforward to promote maximum initial diversity:
     * <ol>
     * <li><b>Viability Check:</b> It first ensures the plan is mathematically possible.</li>
     * <li><b>Constraint Fulfillment:</b> It allocates the minimum required days for each subject.</li>
     * <li><b>Random Allocation:</b> It distributes all remaining days purely at random among all subjects.</li>
     * </ol>
     * This unbiased random distribution is crucial for preventing any initial assumptions from
     * limiting the search space that the genetic algorithm can explore.
     *
     * @param subjects The list of all subjects to be included in the plan.
     * @param totalDays The total number of days to be allocated in the plan.
     * @param minimumDaysPerSubject A map containing the calculated minimum days for each subject.
     * @return A new, randomly generated {@code StudyPlan}.
     * @throws IllegalArgumentException if the sum of minimum days exceeds the total available days.
     */
    public StudyPlan createRandomPlan(
            List<Subject> subjects,
            int totalDays,
            Map<Subject, Integer> minimumDaysPerSubject
    ) {
        // Guarded explicitly: without this the random distribution below reaches
        // Random.nextInt(0) and surfaces the JDK's "bound must be positive", which tells the
        // caller nothing about what is actually wrong with their exam.
        if (subjects == null || subjects.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot build a study plan: the exam has no subjects."
            );
        }

        if (totalDays < 0) {
            throw new IllegalArgumentException(
                    "Total available days cannot be negative (received " + totalDays + ")."
            );
        }

        int totalMinimumDays = minimumDaysPerSubject.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (totalMinimumDays > totalDays) {
            throw new IllegalArgumentException(
                    "Total minimum study days required (" + totalMinimumDays +
                            ") exceeds total available days (" + totalDays + ")."
            );
        }

        Map<Subject, Integer> daysPerSubject = new HashMap<>();

        for (Subject subject : subjects) {
            daysPerSubject.put(subject, minimumDaysPerSubject.getOrDefault(subject, 1));
        }

        int remainingDays = totalDays - totalMinimumDays;
        for (int i = 0; i < remainingDays; i++) {
            Subject randomSubject = subjects.get(RandomProvider.getInstance().nextInt(subjects.size()));
            daysPerSubject.computeIfPresent(randomSubject, (k, currentDays) -> currentDays + 1);
        }

        return new StudyPlan(daysPerSubject);
    }
}