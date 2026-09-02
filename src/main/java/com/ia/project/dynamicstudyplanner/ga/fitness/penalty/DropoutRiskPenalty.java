package com.ia.project.dynamicstudyplanner.ga.fitness.penalty;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.domain.engagement.DropoutRiskAlgorithm;
import org.springframework.stereotype.Component;

/**
 * Penalizes schedules that exacerbate the risk of a student abandoning the platform.
 * If risk is high, this penalty forces the GA to seek "Recovery Days" or lighter schedules.
 * <p>
 * <b>Tactical path only</b> — it needs a {@code TacticalStudyPlan} to have anything to measure, so
 * it returns 1.0 in the macro path by design. Engagement is a fourth concern, not one of the three
 * learning-science foundations; see {@code docs/revisao-ag/05-fitness-function.md} §2.
 */
@Component
public class DropoutRiskPenalty implements FitnessPenalty {

    private final DropoutRiskAlgorithm riskPredictor;

    public DropoutRiskPenalty(DropoutRiskAlgorithm riskPredictor) {
        this.riskPredictor = riskPredictor;
    }

    @Override
    public double calculatePenaltyFactor(StudyPlan plan, EvolutionContext context) {
        if (!(plan instanceof TacticalStudyPlan tacticalPlan) || context.engagementProfile() == null) {
            return 1.0; // Not applicable for macro-plans
        }

        // Calculate the risk score (0.0 to 1.0)
        double riskScore = riskPredictor.calculateRiskScore(tacticalPlan, context.engagementProfile(), context.studentState());

        if (riskScore < 0.4) {
            return 1.0; // Healthy student, no penalty for intense schedules.
        }

        // If risk is high, we must evaluate if the proposed schedule helps or hurts.
        // A schedule helps if it is noticeably lighter (e.g., includes recovery days or low total load).

        double totalCognitiveLoad = tacticalPlan.calculateTotalCognitiveLoad();

        // Assume an average safe load is ~30.0 for a week (heuristic).
        boolean isRecoverySchedule = totalCognitiveLoad < 20.0;

        if (isRecoverySchedule) {
            // Reward (or rather, don't penalize) recovery schedules when risk is high.
            return 1.0;
        } else {
            // Heavy penalty: The student is at risk of churning, but the GA proposed a heavy schedule!
            // The higher the risk, the harder we penalize the heavy schedule.
            double penalty = riskScore * 0.8; // Max 80% reduction
            return Math.max(0.1, 1.0 - penalty);
        }
    }
}
