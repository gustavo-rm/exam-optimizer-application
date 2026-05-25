package com.ia.project.dynamicstudyplanner.ga.fitness;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.springframework.stereotype.Component;

import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.ConstraintValidator;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FitnessPenalty;

import java.util.List;

/**
 * Orchestrates the evaluation of an individual's fitness using a modular pipeline.
 * <p>
 * This class supports a Weighted Sum approach to Multi-Objective Optimization.
 * Tradeoffs:
 * - Weighted Sums are computationally efficient and easy to implement but struggle with non-convex Pareto fronts and require manual tuning of weights.
 * - Pareto Front optimization (like NSGA-II or SPEA2) maintains a diverse set of trade-off solutions without manual weighting, making it superior for complex MOOPs like ITS where "best" is subjective. However, it's significantly more complex and computationally expensive.
 * - For an educational scheduling system where speed is critical for UX, a Weighted Sum approach with dynamically adjusted weights (adaptive weighting based on emergency mode or student state) is the best pragmatic approach.
 */
@Component
public class FitnessEvaluator {

    private final List<FitnessObjective> objectives;
    private final List<FitnessPenalty> penalties;
    private final List<ConstraintValidator> constraints;

    public FitnessEvaluator(
            List<FitnessObjective> objectives,
            List<FitnessPenalty> penalties,
            List<ConstraintValidator> constraints
    ) {
        this.objectives = objectives;
        this.penalties = penalties;
        this.constraints = constraints;
    }

    public double evaluate(StudyPlan plan, EvolutionContext context) {
        // 1. Calculate weighted sum of rewards
        double totalReward = 0.0;
        for (FitnessObjective objective : objectives) {
            totalReward += objective.calculateReward(plan, context) * objective.getWeight();
        }

        // 2. Apply hard constraints (if violated, drastically reduce fitness)
        boolean meetsAllConstraints = true;
        for (ConstraintValidator constraint : constraints) {
            if (!constraint.isValid(plan, context)) {
                meetsAllConstraints = false;
                break;
            }
        }

        if (!meetsAllConstraints) {
            totalReward *= 0.5; // standard constraint violation penalty
        }

        // 3. Apply soft penalties (multiplicative)
        double finalFitness = totalReward;
        for (FitnessPenalty penalty : penalties) {
            finalFitness *= penalty.calculatePenaltyFactor(plan, context);
        }

        return finalFitness;
    }
}
