package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lays the chromosome's allocation onto the calendar, forward only.
 *
 * <h2>What the chromosome decides and what this decides</h2>
 *
 * The macro chromosome allocates a number of <b>sessions</b> to each topic and has no calendar. This
 * turns that into wall-clock blocks: the first session of a topic is new ground for
 * {@code estimatedMinutes}, and every session after it is a revision for
 * {@link #revisionMinutes(int)}. The order comes from {@link SinapseStudyOrder}.
 *
 * <p>{@code estimatedMinutes} is used unscaled because it is the one number on a topic the platform
 * has already fitted to this student ({@code docs/CORE_CONTRACT_SURVEY.md} §1.8). Deriving a length
 * from the difficulty band instead would replace a calibrated estimate with a mapped one, and the
 * band is already doing its job inside the fitness.
 *
 * <h2>Whole topics, and truncation rather than skipping</h2>
 *
 * A topic is placed with all of its sessions or none of them, and the walk <b>stops</b> at the first
 * topic that does not fit rather than skipping to whatever still would. Both rules exist to keep the
 * scheduled set a prefix of a topological order, which is what makes it closed under prerequisites:
 * a student never receives a topic whose hard prerequisite is missing from the plan. Skipping would
 * fill the calendar more fully and could produce an invalid plan, which
 * {@code PlanOutputInvariants} would then refuse with a 500.
 *
 * <h2>Why the allocator is shared with the baseline and the ordering is not</h2>
 *
 * {@link AvailabilityAllocator} clips the windows to the horizon, sorts them, and hands out
 * forward-only instants. Those are properties of the <b>protocol</b> — inside the horizon, inside a
 * window, never overlapping — and both engines owe them to the platform identically, so sharing one
 * implementation is what keeps the two conditions comparable on the axes that are not under test.
 * The ordering is the opposite case and is deliberately not shared; see {@link SinapseStudyOrder}.
 *
 * <h2>The methodology on each block carries the session kind and nothing else</h2>
 *
 * {@link TacticalStudyBlock} requires a {@link StudyMethodology}, and the contract's
 * {@code SessionKind} has two values. {@code ACTIVE_RECALL} marks new ground and
 * {@code SPACED_REPETITION_REVIEW} marks a revision, purely so the distinction survives to
 * {@link TacticalSessions}. Nothing on this path reads the methodology's load, retention or energy
 * multipliers: the terms that would have — the two multiplicative penalties — are not in this path's
 * composition, for the reason {@code FitnessCompositionConfig} gives. No claim is being made that
 * active recall is the right method for a first pass.
 */
public final class SessionPlacement {

    private SessionPlacement() {
    }

    /**
     * The result of laying out a plan: the schedule, and how much of the syllabus fitted.
     *
     * @param plan             the placed blocks, as the tactical chromosome
     * @param topicsScheduled  how many topics received at least their first session
     * @param topicsTotal      how many topics were in scope
     * @param scheduledMinutes total minutes placed
     */
    public record Result(TacticalStudyPlan plan, int topicsScheduled, int topicsTotal,
            long scheduledMinutes) {

        /** @return whether the calendar could not hold the whole syllabus */
        public boolean partial() {
            return topicsScheduled < topicsTotal;
        }
    }

    /**
     * Minutes a revision of a topic gets: half its study length, rounded up, never zero.
     *
     * <p>The same rule the baseline applies, and the same reasoning — a revision is shorter than
     * first contact and a zero-length session is not a session, which the platform refuses outright.
     * It is duplicated rather than shared because it is a planning judgement, not a protocol
     * property, and the two engines must be free to differ on it without one changing the other.
     */
    public static int revisionMinutes(int estimatedMinutes) {
        return Math.max(1, (estimatedMinutes + 1) / 2);
    }

    /**
     * Places the plan.
     *
     * @param request     the request, for the windows and the horizon
     * @param order       the topics in placement order
     * @param chromosome  the allocation, read as sessions per topic
     * @param itemsByTopic the planning item of each topic, to read the chromosome
     * @return the schedule and what fitted
     */
    public static Result place(PlanRequest request, List<PlanRequest.Topic> order,
            StudyPlan chromosome, Map<PlanRequest.Topic, PlanningItem> itemsByTopic) {

        AvailabilityAllocator allocator = AvailabilityAllocator.over(request);
        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();
        int scheduled = 0;
        long minutes = 0;

        for (PlanRequest.Topic topic : order) {
            PlanningItem item = itemsByTopic.get(topic);
            List<Block> blocks = placeTopic(topic, item, chromosome.getDaysForItem(item), allocator);
            if (blocks.isEmpty()) {
                break;
            }
            for (Block block : blocks) {
                schedule.put(block.slot(), block.block());
                minutes += block.block().durationMinutes();
            }
            scheduled++;
        }

        return new Result(new TacticalStudyPlan(schedule), scheduled, order.size(), minutes);
    }

    /**
     * Places one topic's sessions, or none of them.
     *
     * <p>At least one session is always attempted even when the chromosome allocated zero: a topic
     * in scope with no session at all would be reported as planned and never studied. The floor is
     * also asserted by the evolution context, which gives every item a minimum of one.
     */
    private static List<Block> placeTopic(PlanRequest.Topic topic, PlanningItem item, int sessions,
            AvailabilityAllocator allocator) {

        List<Block> blocks = new ArrayList<>();
        Optional<Instant> first = allocator.place(topic.estimatedMinutes());
        if (first.isEmpty()) {
            return List.of();
        }
        blocks.add(block(item, first.get(), topic.estimatedMinutes(),
                StudyMethodology.ACTIVE_RECALL));

        int revisions = Math.max(0, sessions - 1);
        int revisionMinutes = revisionMinutes(topic.estimatedMinutes());
        for (int index = 0; index < revisions; index++) {
            Optional<Instant> again = allocator.place(revisionMinutes);
            if (again.isEmpty()) {
                // The topic's first session fitted and a revision did not. The revisions are the
                // discretionary part of the allocation, so the topic keeps what it has rather than
                // being given back: dropping it would strand every topic that depends on it.
                break;
            }
            blocks.add(block(item, again.get(), revisionMinutes,
                    StudyMethodology.SPACED_REPETITION_REVIEW));
        }
        return blocks;
    }

    private static Block block(PlanningItem item, Instant start, int minutes,
            StudyMethodology methodology) {

        TimeSlot slot = new TimeSlot(AvailabilityWindows.toLocal(start),
                AvailabilityWindows.toLocal(start.plusSeconds(minutes * 60L)));
        return new Block(slot, new TacticalStudyBlock(item, methodology, minutes));
    }

    /** One placed block, before it becomes a map entry. */
    private record Block(TimeSlot slot, TacticalStudyBlock block) {
    }
}
