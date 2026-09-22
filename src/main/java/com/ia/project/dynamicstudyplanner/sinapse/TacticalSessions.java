package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * The tactical chromosome as the wire's list of sessions.
 *
 * <h2>Sort, then number</h2>
 *
 * {@code TacticalStudyPlan} is a {@code Map<TimeSlot, TacticalStudyBlock>} — keyed by slot, which is
 * what makes overlap structurally impossible, and unordered, which is what makes this conversion
 * necessary. The whole of it is: <b>sort by start instant and number from zero</b>. The index is a
 * count over a sorted list, so it is unique and contiguous without either having to be arranged, and
 * it agrees with chronology, which is the only ordering key the platform gets.
 *
 * <p>Ties on the start instant are broken by the slot's end and then by the topic identifier's text.
 * Two blocks cannot in fact share a start — the allocator's cursor only moves forward — so the
 * tie-break is unreachable by construction. It is specified anyway: an unstable sort here would
 * produce a plan that differed between two runs of the same seed, and this path promises it does
 * not. A comparator that never fires costs nothing; a nondeterminism that fires once in a thousand
 * runs costs a day of debugging.
 *
 * <h2>Duration and kind</h2>
 *
 * {@code durationMinutes} comes from the block, which the allocator sized, and is positive by
 * construction: {@code SessionPlacement} never places a zero-length block, and a non-positive
 * duration is one of the eight things the platform refuses a response over.
 *
 * <p>{@code kind} is read off the methodology: {@code SPACED_REPETITION_REVIEW} is a
 * {@code REVISION}, everything else is {@code STUDY}. The mapping is exhaustive over the enum rather
 * than a lookup table, so a methodology added later fails to compile here instead of silently
 * becoming a study session.
 */
public final class TacticalSessions {

    private TacticalSessions() {
    }

    /**
     * Converts a placed plan into the response's sessions.
     *
     * @param plan         the placed blocks
     * @param topicsByItem the topic identifier behind each planning item
     * @return the sessions, chronological, with {@code sequenceIndex} running from zero
     */
    public static List<PlanResponse.ScheduledSession> of(TacticalStudyPlan plan,
            Map<PlanningItem, UUID> topicsByItem) {

        List<Map.Entry<TimeSlot, TacticalStudyBlock>> ordered = plan.getSchedule().entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<TimeSlot, TacticalStudyBlock> entry)
                                -> entry.getKey().startTime())
                        .thenComparing(entry -> entry.getKey().endTime())
                        .thenComparing(entry -> topicsByItem.get(entry.getValue().item()).toString()))
                .toList();

        return IntStream.range(0, ordered.size())
                .mapToObj(index -> toSession(ordered.get(index), index, topicsByItem))
                .toList();
    }

    private static PlanResponse.ScheduledSession toSession(
            Map.Entry<TimeSlot, TacticalStudyBlock> entry, int index,
            Map<PlanningItem, UUID> topicsByItem) {

        TacticalStudyBlock block = entry.getValue();
        return new PlanResponse.ScheduledSession(
                topicsByItem.get(block.item()),
                kindOf(block.methodology()),
                AvailabilityWindows.toInstant(entry.getKey().startTime()),
                (int) block.durationMinutes(),
                index);
    }

    /** The contract has two kinds; the tactical layer has five methodologies. */
    private static SessionKind kindOf(StudyMethodology methodology) {
        return switch (methodology) {
            case SPACED_REPETITION_REVIEW -> SessionKind.REVISION;
            case ACTIVE_RECALL, PASSIVE_READING, VIDEO_LECTURE, PRACTICE_EXAM -> SessionKind.STUDY;
        };
    }
}
