package com.ia.project.dynamicstudyplanner.domain.tactical;

import java.util.Collections;
import java.util.Map;

/**
 * The Chromosome for the Tactical Intelligent Tutoring System.
 * Unlike the strategic StudyPlan (which mapped Subject -> Days),
 * this maps specific TimeSlots -> TacticalStudyBlocks.
 * <p>
 * By using TimeSlots as the keys (loci), we guarantee that blocks cannot overlap,
 * fundamentally enforcing a constraint-preserving architecture at the base level.
 */
public class TacticalStudyPlan extends com.ia.project.dynamicstudyplanner.domain.StudyPlan {

    private final Map<TimeSlot, TacticalStudyBlock> schedule;

    public TacticalStudyPlan(Map<TimeSlot, TacticalStudyBlock> schedule) {
        super(Map.of()); // Call super with empty map, as Tactical overrides behavior
        this.schedule = schedule == null ? Map.of() : Collections.unmodifiableMap(schedule);
    }

    public Map<TimeSlot, TacticalStudyBlock> getSchedule() {
        return schedule;
    }

    /**
     * Calculates the total cumulative cognitive load of this specific plan.
     */
    public double calculateTotalCognitiveLoad() {
        return schedule.values().stream()
                .mapToDouble(block -> block.subject().cognitiveLoad() * block.methodology().getCognitiveLoadMultiplier() * (block.durationMinutes() / 60.0))
                .sum();
    }
}
