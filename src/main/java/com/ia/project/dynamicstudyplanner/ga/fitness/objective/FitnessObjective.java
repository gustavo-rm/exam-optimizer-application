package com.ia.project.dynamicstudyplanner.ga.fitness.objective;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * One objective of the aggregated fitness.
 * <p>
 * <b>Contract:</b> {@link #calculateReward} must return a value in {@code [0,1]}, and the weights of
 * all registered objectives must sum to 1 ({@code FitnessEvaluator} asserts this at construction).
 * Both rules exist so the aggregate stays in {@code [0,1]} and comparable across exams, students and
 * releases — an objective returning the exam's own units would dominate or vanish depending on the
 * payload (docs/revisao-ag/01-auditoria-fitness.md §2.1.4).
 * <p>
 * Every objective, its formula, its weight and the learning-science result behind it are documented
 * in {@code docs/revisao-ag/05-fitness-function.md}. Adding one here means adding it there.
 */
public interface FitnessObjective {
    double calculateReward(StudyPlan plan, EvolutionContext context);
    double getWeight();
}
