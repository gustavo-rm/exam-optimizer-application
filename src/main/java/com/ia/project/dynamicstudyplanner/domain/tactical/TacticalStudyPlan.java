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
        super(extractDaysPerSubject(schedule));
        this.schedule = schedule == null ? Map.of() : Collections.unmodifiableMap(schedule);
    }

    private static Map<com.ia.project.dynamicstudyplanner.domain.exam.Subject, Integer> extractDaysPerSubject(Map<TimeSlot, TacticalStudyBlock> schedule) {
        if (schedule == null) return Map.of();

        // Count how many unique days each subject is studied
        Map<com.ia.project.dynamicstudyplanner.domain.exam.Subject, java.util.Set<Integer>> subjectDays = new java.util.HashMap<>();

        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : schedule.entrySet()) {
            subjectDays.computeIfAbsent(entry.getValue().subject(), k -> new java.util.HashSet<>())
                       .add(entry.getKey().startTime().getDayOfYear());
        }

        Map<com.ia.project.dynamicstudyplanner.domain.exam.Subject, Integer> daysPerSubject = new java.util.HashMap<>();
        for (Map.Entry<com.ia.project.dynamicstudyplanner.domain.exam.Subject, java.util.Set<Integer>> entry : subjectDays.entrySet()) {
            daysPerSubject.put(entry.getKey(), entry.getValue().size());
        }
        return daysPerSubject;
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
