package com.ia.project.dynamicstudyplanner.service.calculation.fatigue;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Calculates non-linear human energy constraints, interday fatigue accumulation,
 * and assesses the risk of burnout for a given schedule.
 * Future roadmap: Replace heuristic curves with ML inference engine.
 */
@Service
public class FatigueAndEnergyModel {

    // A theoretical maximum fatigue level before the student crashes.
    private static final double BURNOUT_THRESHOLD = 50.0;
    // Fatigue carried over from the previous day.
    private static final double FATIGUE_CARRYOVER_RATE = 0.2;

    /**
     * Calculates an overall burnout risk penalty for the entire plan.
     * 1.0 means perfectly sustainable. Lower values indicate higher risk,
     * dropping exponentially if the burnout threshold is breached.
     */
    public double calculateBurnoutRisk(TacticalStudyPlan plan, StudentState state) {
        if (state == null || plan.getSchedule().isEmpty()) {
            return 1.0;
        }

        double cumulativeFatigue = state.fatigueLevel() * 5.0; // Base fatigue mapped to scale
        double worstDayFatigue = 0.0;

        // Group blocks by day to calculate interday accumulation
        // Note: For simplicity in this heuristic, we assume blocks are chronologically ordered.
        List<Map.Entry<TimeSlot, TacticalStudyBlock>> sortedBlocks = plan.getSchedule().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().startTime()))
                .toList();

        int currentDay = -1;
        double dailyFatigue = 0.0;

        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : sortedBlocks) {
            TimeSlot slot = entry.getKey();
            TacticalStudyBlock block = entry.getValue();

            if (currentDay == -1) {
                currentDay = slot.startTime().getDayOfYear();
            } else if (slot.startTime().getDayOfYear() != currentDay) {
                // Day changed. Calculate recovery and carryover.
                cumulativeFatigue = (cumulativeFatigue + dailyFatigue) * FATIGUE_CARRYOVER_RATE;
                worstDayFatigue = Math.max(worstDayFatigue, dailyFatigue);
                dailyFatigue = 0.0;
                currentDay = slot.startTime().getDayOfYear();
            }

            // Calculate block fatigue considering intraday energy
            double expectedEnergy = getExpectedEnergyLevel(slot.startTime().toLocalTime(), state.chronotype());
            double blockLoad = block.calculateEmotionalLoad() + block.calculateRequiredEnergy();

            // If load > energy, fatigue generates faster.
            double fatigueMultiplier = blockLoad > expectedEnergy ? 1.5 : 0.8;
            dailyFatigue += (block.durationMinutes() / 60.0) * fatigueMultiplier;

            // Check for acute burnout (e.g., studying 10 hours straight)
            if (dailyFatigue > BURNOUT_THRESHOLD / 2) {
                 return 0.1; // Massive penalty for acute daily burnout
            }
        }

        cumulativeFatigue += dailyFatigue; // Add the last day

        if (cumulativeFatigue > BURNOUT_THRESHOLD) {
            return 0.2; // Massive penalty for chronic burnout
        }

        // Return a gradual penalty based on how close they are to burnout
        return Math.max(0.5, 1.0 - (cumulativeFatigue / (BURNOUT_THRESHOLD * 1.5)));
    }

    /**
     * Mathematical heuristic for the Biphasic energy curve based on chronotype.
     * Returns a multiplier between ~0.5 and ~1.5.
     */
    public double getExpectedEnergyLevel(LocalTime time, Chronotype chronotype) {
        double hour = time.getHour() + (time.getMinute() / 60.0);

        return switch (chronotype) {
            case MORNING_LARK -> calculateBiphasicCurve(hour, 9.0, 15.0);
            case NIGHT_OWL -> calculateBiphasicCurve(hour, 14.0, 22.0);
            case INTERMEDIATE -> calculateBiphasicCurve(hour, 10.0, 16.0);
            default -> 1.0;
        };
    }

    /**
     * Generates a biphasic curve with a primary peak and a secondary peak (after a dip).
     */
    private double calculateBiphasicCurve(double hour, double primaryPeakHour, double secondaryPeakHour) {
        // Base energy
        double energy = 1.0;

        // Primary peak (strong)
        double distToPrimary = Math.abs(hour - primaryPeakHour);
        if (distToPrimary < 4.0) {
            energy += 0.5 * Math.cos((distToPrimary / 4.0) * (Math.PI / 2));
        }

        // Secondary peak (weaker)
        double distToSecondary = Math.abs(hour - secondaryPeakHour);
        if (distToSecondary < 3.0) {
            energy += 0.3 * Math.cos((distToSecondary / 3.0) * (Math.PI / 2));
        }

        // Late night penalty (unless night owl, handled by peak shift)
        if (hour < 6.0 || hour > 23.0) {
            energy -= 0.4;
        }

        return Math.max(0.5, Math.min(1.5, energy));
    }
}
