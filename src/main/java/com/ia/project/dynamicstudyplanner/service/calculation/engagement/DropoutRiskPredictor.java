package com.ia.project.dynamicstudyplanner.service.calculation.engagement;

import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.engagement.EngagementProfile;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import org.springframework.stereotype.Service;

/**
 * Service that predicts the likelihood of a student disengaging from the platform
 * (dropping out or ignoring the schedule) based on behavioral history and proposed schedule intensity.
 */
@Service
public class DropoutRiskPredictor {

    /**
     * Calculates a continuous risk score between 0.0 (perfectly safe) and 1.0 (imminent churn).
     *
     * @param plan The proposed tactical study schedule.
     * @param engagement The student's historical engagement metrics.
     * @param state The student's current psychological and physical state.
     * @return Risk score from 0.0 to 1.0.
     */
    public double calculateRiskScore(TacticalStudyPlan plan, EngagementProfile engagement, StudentState state) {
        if (engagement == null) {
            return 0.0;
        }

        // 1. Historical Risk Baseline (0.0 to 1.0)
        // If they missed many days recently, base risk is already high.
        double failurePenalty = (1.0 - engagement.recentCompletionRate()) * 0.5;
        double streakPenalty = Math.min(0.3, engagement.consecutiveFailedDays() * 0.1);
        double baselineRisk = failurePenalty + streakPenalty;

        if (engagement.hasExpressedFrustration()) {
            baselineRisk += 0.2;
        }

        // 2. Proposed Schedule Stressors
        double scheduleDensityPenalty = 0.0;
        double emotionalLoadPenalty = 0.0;

        if (plan != null && !plan.getSchedule().isEmpty()) {
            double totalEmotionalLoad = plan.getSchedule().values().stream()
                    .mapToDouble(TacticalStudyBlock::calculateEmotionalLoad)
                    .sum();

            // Normalize arbitrary emotional load value to a 0-1 scale penalty
            emotionalLoadPenalty = Math.min(0.2, totalEmotionalLoad / 50.0);

            // High load on top of high stress state acts as a multiplier
            if (state != null && state.stressLevel() > 3.5) {
                emotionalLoadPenalty *= 1.5;
            }

            // Lack of rest days (schedule density)
            int totalDays = plan.getTotalDays();
            if (totalDays >= 7) {
                scheduleDensityPenalty = 0.15; // Penalize 7-day grinds
            }
        }

        // Combine factors and cap at 1.0
        double totalRisk = baselineRisk + emotionalLoadPenalty + scheduleDensityPenalty;
        return Math.max(0.0, Math.min(1.0, totalRisk));
    }
}
