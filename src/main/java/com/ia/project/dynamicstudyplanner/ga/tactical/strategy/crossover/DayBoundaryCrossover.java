package com.ia.project.dynamicstudyplanner.ga.tactical.strategy.crossover;

import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Splices parents at midnight boundaries to preserve intraday fatigue and constraint integrity.
 * E.g., takes Mon-Wed from Parent1, Thu-Sun from Parent2.
 */
public class DayBoundaryCrossover implements TacticalCrossoverStrategy {

    private final Random random = com.ia.project.dynamicstudyplanner.util.RandomProvider.getInstance();

    @Override
    public TacticalStudyPlan crossover(TacticalStudyPlan parent1, TacticalStudyPlan parent2, double crossoverRate, EvolutionContext context) {
        if (random.nextDouble() > crossoverRate) {
            return parent1; // Return clone of parent1 if no crossover
        }

        Map<TimeSlot, TacticalStudyBlock> childSchedule = new HashMap<>();

        // Pick a random cutoff day (simplified for illustration - assumes blocks are within a known week range)
        int minDay = Integer.MAX_VALUE;
        int maxDay = Integer.MIN_VALUE;

        for (TimeSlot slot : parent1.getSchedule().keySet()) {
            int day = slot.startTime().getDayOfYear();
            if (day < minDay) minDay = day;
            if (day > maxDay) maxDay = day;
        }

        int cutoffDayOfYear = minDay;
        if (maxDay > minDay) {
            cutoffDayOfYear = minDay + random.nextInt(maxDay - minDay + 1);
        }

        // Inherit from parent 1 before cutoff, parent 2 after cutoff
        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : parent1.getSchedule().entrySet()) {
            if (entry.getKey().startTime().getDayOfYear() <= cutoffDayOfYear) {
                childSchedule.put(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : parent2.getSchedule().entrySet()) {
            if (entry.getKey().startTime().getDayOfYear() > cutoffDayOfYear) {
                childSchedule.put(entry.getKey(), entry.getValue());
            }
        }

        return new TacticalStudyPlan(childSchedule);
    }
}
