package com.ia.project.dynamicstudyplanner.ga.fitness.penalty;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.springframework.stereotype.Component;

/**
 * Penalizes plans that are unsustainable given the student's psychological and physical state.
 */
@Component
public class FatigueAndSustainabilityPenalty implements FitnessPenalty {

    @Override
    public double calculatePenaltyFactor(StudyPlan plan, EvolutionContext context) {
        com.ia.project.dynamicstudyplanner.domain.StudentState state = context.studentState();
        if (state == null) {
            return 1.0; // No penalty if state is unknown
        }

        int totalDays = plan.getTotalDays();

        // Calculate a basic "sustainability factor".
        // We consider the optimal state to be stress=1, fatigue=1, motivation=5.
        // The worse the state, the lower the sustainability factor.
        double stressFactor = 1.0 - ((state.stressLevel() - 1.0) / 8.0); // 1.0 -> 1.0, 5.0 -> 0.5
        double fatigueFactor = 1.0 - ((state.fatigueLevel() - 1.0) / 8.0); // 1.0 -> 1.0, 5.0 -> 0.5
        double motivationFactor = 0.5 + ((state.motivationLevel() - 1.0) / 8.0); // 1.0 -> 0.5, 5.0 -> 1.0

        double sustainabilityFactor = stressFactor * fatigueFactor * motivationFactor;

        // If the plan is very long, a low sustainability factor hits harder.
        // E.g., if totalDays > 10, we reduce the score based on sustainability.
        if (totalDays > 10) {
            // The more days, the more the state affects the score
            double penalty = 1.0 - sustainabilityFactor;
            // Cap penalty between 0.0 and 0.5
            penalty = Math.max(0.0, Math.min(0.5, penalty));
            return 1.0 - penalty; // Multiplier: 0.5 to 1.0
        }

        return 1.0;
    }
}
