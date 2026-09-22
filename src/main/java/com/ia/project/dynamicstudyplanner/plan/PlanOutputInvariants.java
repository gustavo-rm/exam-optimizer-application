package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The twelve properties of the answer, asserted before it is returned.
 *
 * <h2>Eight the platform checks, and four it does not</h2>
 *
 * {@code RestSinapseCore.validated} applies eight checks to whatever comes back, listed with line
 * numbers in {@code docs/CORE_CONTRACT_SURVEY.md} §3: a non-empty body, a matching
 * {@code contractVersion}, at least one session, metadata naming a core version, the seed echoed,
 * every session carrying a topic and a kind and a start, every duration positive, and no repeated
 * {@code sequenceIndex}. Failing any of them raises {@code CoreProtocolException} on the platform.
 *
 * <p>The same survey lists what {@code validated} does <b>not</b> check, and those are the ones that
 * matter here, because nobody catches them if this side does not:
 *
 * <ul>
 *   <li>{@code scheduledStart} inside the horizon;</li>
 *   <li>{@code scheduledStart} inside one of the availability windows that were sent;</li>
 *   <li>sessions not overlapping one another;</li>
 *   <li>{@code topicId} being one of the topics that were sent.</li>
 * </ul>
 *
 * <p>They are treated as output invariants of this component, not as the consumer's problem. A plan
 * that overlaps itself or leaves the horizon would be accepted by the platform, written to the
 * database and shown to a student; the cheapest place to stop it is here, one method before the
 * response leaves. Two more are asserted on top: {@code sequenceIndex} contiguous rather than merely
 * distinct, and no session of a dependent topic before the sessions of its hard prerequisites.
 *
 * <h2>Why assert what the algorithm already guarantees</h2>
 *
 * Each of these follows from the construction — the cursor only moves forward, the indices are
 * counted last, the order is a prefix of a topological sort. That is exactly why they are worth
 * asserting: they are the properties a future change to the allocator would break silently. A
 * violation means this module has a defect, so it is answered with {@code 500}, never {@code 422}.
 * The cost is one pass over a list whose length is the number of sessions.
 */
public final class PlanOutputInvariants {

    private PlanOutputInvariants() {
    }

    /**
     * Asserts every invariant, and throws on the first violation.
     *
     * @param request  the request that was answered
     * @param response the answer about to be returned
     * @param graph    the applied {@code HARD} constraints
     * @throws PlanInvariantViolationException naming the invariant and where it broke
     */
    public static void check(PlanRequest request, PlanResponse response, HardPrerequisiteGraph graph) {
        checkEnvelope(request, response);
        checkSequence(response);
        checkSessions(request, response);
        checkChronology(response);
        checkPrerequisites(response, graph);
    }

    /** Invariants 2 to 5: version, a non-empty schedule, metadata that names a version and the seed. */
    private static void checkEnvelope(PlanRequest request, PlanResponse response) {
        require(PlanRequest.VERSION.equals(response.contractVersion()),
                "contractVersion must be " + PlanRequest.VERSION
                        + ", was " + response.contractVersion());
        require(!response.sessions().isEmpty(), "a plan must carry at least one session");

        PlanResponse.ExecutionMetadata metadata = response.metadata();
        require(metadata != null, "a plan must carry execution metadata");
        require(metadata.coreVersion() != null && !metadata.coreVersion().isBlank(),
                "execution metadata must name the core version that produced the plan");
        require(metadata.randomSeed() == request.randomSeed(),
                "the seed must be echoed exactly: sent " + request.randomSeed()
                        + ", answered " + metadata.randomSeed());
    }

    /**
     * Invariant 8, and the contiguity the platform does not require.
     *
     * <p>Distinct indices would satisfy the platform. Contiguous ones are asserted because the index
     * is the plan's only ordering key: a gap would let a consumer conclude a session had been dropped
     * in transit, and there is no reason for this side ever to produce one.
     */
    private static void checkSequence(PlanResponse response) {
        int count = response.sessions().size();
        SortedSet<Integer> indices = new TreeSet<>();
        response.sessions().forEach(session -> indices.add(session.sequenceIndex()));
        require(indices.size() == count, "sequenceIndex must not repeat within a plan");
        // The indices are distinct and there are exactly as many of them as there are sessions, so
        // spanning 0..count-1 is the whole of contiguity: no gap fits inside those bounds.
        require(indices.first() == 0 && indices.last() == count - 1,
                "sequenceIndex must run from 0 to " + (count - 1) + " with no gap, was " + indices);
    }

    /** Invariants 6, 7 and 12, plus the horizon and the availability windows, one session at a time. */
    private static void checkSessions(PlanRequest request, PlanResponse response) {
        Set<UUID> known = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        request.topics().forEach(topic -> known.add(topic.id()));
        HorizonBounds horizon = HorizonBounds.of(request.horizon());

        for (PlanResponse.ScheduledSession session : response.sessions()) {
            checkSession(session, known, horizon, request.availability());
        }
    }

    private static void checkSession(PlanResponse.ScheduledSession session, Set<UUID> known,
            HorizonBounds horizon, List<PlanRequest.AvailabilitySlot> availability) {

        String where = "session " + session.sequenceIndex();
        require(session.topicId() != null && session.kind() != null
                && session.scheduledStart() != null, where + " is incomplete");
        require(session.durationMinutes() > 0, where + " has no length");
        require(known.contains(session.topicId()),
                where + " cites topic " + session.topicId() + ", which was not sent");
        require(horizon.contains(session.scheduledStart()),
                where + " starts at " + session.scheduledStart() + ", outside the horizon");
        require(fitsAWindow(session, availability),
                where + " does not fit inside any availability window that was sent");
    }

    /**
     * Whether the session lies whole inside one window it was sent.
     *
     * <p>Containment of the whole block, not only of its start: a session that began inside a window
     * and ran past its close would keep the student studying through time they said they did not have,
     * and the platform checks neither end.
     */
    private static boolean fitsAWindow(PlanResponse.ScheduledSession session,
            List<PlanRequest.AvailabilitySlot> availability) {

        Instant start = session.scheduledStart();
        Instant end = start.plus(session.durationMinutes(), ChronoUnit.MINUTES);
        return availability.stream().anyMatch(slot ->
                !start.isBefore(slot.start()) && !end.isAfter(slot.end()));
    }

    /** No two sessions occupy the same minute, read in sequence order. */
    private static void checkChronology(PlanResponse response) {
        List<PlanResponse.ScheduledSession> ordered = response.sessions().stream()
                .sorted(Comparator.comparingInt(PlanResponse.ScheduledSession::sequenceIndex))
                .toList();
        for (int index = 1; index < ordered.size(); index++) {
            PlanResponse.ScheduledSession previous = ordered.get(index - 1);
            Instant previousEnd = previous.scheduledStart()
                    .plus(previous.durationMinutes(), ChronoUnit.MINUTES);
            require(!previousEnd.isAfter(ordered.get(index).scheduledStart()),
                    "session " + previous.sequenceIndex() + " runs into session "
                            + ordered.get(index).sequenceIndex());
        }
    }

    /**
     * No session of a dependent topic before its hard prerequisites are covered.
     *
     * <p>Asserted over the applied edges only — an edge with an endpoint outside {@code topics} was
     * never a constraint on this plan, for the reason {@link HardPrerequisiteGraph} gives. A
     * prerequisite that is absent from the plan altogether is a violation, not a pass: a dependent
     * topic scheduled with its prerequisite nowhere in the plan is the case a partial plan has to
     * avoid, which is why allocation truncates the study order rather than skipping over it.
     */
    private static void checkPrerequisites(PlanResponse response, HardPrerequisiteGraph graph) {
        Map<UUID, Integer> firstSession = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        response.sessions().forEach(session ->
                firstSession.merge(session.topicId(), session.sequenceIndex(), Math::min));

        for (UUID dependent : graph.constrainedTopics()) {
            Integer dependentAt = firstSession.get(dependent);
            if (dependentAt != null) {
                checkPrerequisitesOf(dependent, dependentAt, graph, firstSession);
            }
        }
    }

    private static void checkPrerequisitesOf(UUID dependent, int dependentAt,
            HardPrerequisiteGraph graph, Map<UUID, Integer> firstSession) {

        for (UUID prerequisite : graph.prerequisitesOf(dependent)) {
            Integer prerequisiteAt = firstSession.get(prerequisite);
            require(prerequisiteAt != null,
                    "topic " + dependent + " is scheduled but its hard prerequisite "
                            + prerequisite + " is not in the plan at all");
            require(prerequisiteAt < dependentAt,
                    "topic " + dependent + " is scheduled at " + dependentAt
                            + ", before its hard prerequisite " + prerequisite
                            + " at " + prerequisiteAt);
        }
    }

    private static void require(boolean held, String violation) {
        if (!held) {
            throw new PlanInvariantViolationException(
                    "The greedy baseline scheduler broke one of its own output invariants: "
                            + violation + ". This is a defect in the scheduler, not a bad request.");
        }
    }
}
