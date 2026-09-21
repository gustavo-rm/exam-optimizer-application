package com.ia.project.dynamicstudyplanner.domain.tactical;

import java.util.Collections;
import java.util.Map;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import java.util.Set;
import java.util.HashMap;

/**
 * The Chromosome for the Tactical Intelligent Tutoring System.
 * Unlike the strategic StudyPlan (which maps PlanningItem -> Days),
 * this maps specific TimeSlots -> TacticalStudyBlocks.
 * <p>
 * By using TimeSlots as the keys (loci), we guarantee that blocks cannot overlap,
 * fundamentally enforcing a constraint-preserving architecture at the base level.
 */
public class TacticalStudyPlan extends StudyPlan {

    private final Map<TimeSlot, TacticalStudyBlock> schedule;

    public TacticalStudyPlan(Map<TimeSlot, TacticalStudyBlock> schedule) {
        super(extractDaysPerItem(schedule));
        this.schedule = schedule == null ? Map.of() : Collections.unmodifiableMap(schedule);
    }

    private static Map<PlanningItem, Integer> extractDaysPerItem(Map<TimeSlot,
            TacticalStudyBlock> schedule) {
        if (schedule == null) {
            return Map.of();
        }

        // Count how many unique days each subject is studied
        Map<PlanningItem, Set<Integer>> itemDays = new HashMap<>();

        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : schedule.entrySet()) {
            itemDays.computeIfAbsent(entry.getValue().item(), k -> new java.util.HashSet<>())
                       .add(entry.getKey().startTime().getDayOfYear());
        }

        Map<PlanningItem, Integer> daysPerItem = new HashMap<>();
        for (Map.Entry<PlanningItem,
                Set<Integer>> entry : itemDays.entrySet()) {
            daysPerItem.put(entry.getKey(), entry.getValue().size());
        }
        return daysPerItem;
    }

    public Map<TimeSlot, TacticalStudyBlock> getSchedule() {
        return schedule;
    }

    /**
     * Calculates the total cumulative cognitive load of this specific plan.
     */
    public double calculateTotalCognitiveLoad() {
        return schedule.values().stream()
                .mapToDouble(block -> block.item().difficultyBand() * block.methodology().getCognitiveLoadMultiplier(
                        ) * (block.durationMinutes() / 60.0))
                .sum();
    }
}
