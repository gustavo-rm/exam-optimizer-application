package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The twelve invariants a plan has to satisfy, asserted from outside the scheduler.
 *
 * <h2>Why these are re-derived here rather than delegated to the production check</h2>
 *
 * {@code PlanOutputInvariants} asserts the same properties inside the scheduler. Calling it from the
 * tests would prove only that it agrees with itself: a checker with a wrong sign would pass every
 * test and ship. These assertions are written independently, from the survey, and read the finished
 * response the way the platform reads it off the wire.
 *
 * <h2>Where the list comes from</h2>
 *
 * The first eight are {@code RestSinapseCore.validated}, transcribed with line numbers in
 * {@code docs/CORE_CONTRACT_SURVEY.md} §3. The next four are the ones the same survey says
 * {@code validated} does <b>not</b> check — which is exactly why they are asserted here: nothing
 * downstream catches them.
 */
final class PlanInvariantAssertions {

    private PlanInvariantAssertions() {
    }

    /** Asserts all twelve, plus contiguity and the hard-prerequisite ordering. */
    static void assertEveryInvariant(PlanRequest request, PlanResponse response) {
        assertPlatformChecks(request, response);
        assertWhatThePlatformDoesNotCheck(request, response);
        assertSequenceIsContiguous(response);
        assertHardPrerequisitesComeFirst(request, response);
    }

    /** The eight checks of {@code RestSinapseCore.validated}, in its own order. */
    static void assertPlatformChecks(PlanRequest request, PlanResponse response) {
        assertThat(response)
                .as("1. RestSinapseCore.java:97 — the body is not empty")
                .isNotNull();
        assertThat(response.contractVersion())
                .as("2. RestSinapseCore.java:100 — the contract version equals the platform's")
                .isEqualTo(PlanRequest.VERSION);
        assertThat(response.sessions())
                .as("3. RestSinapseCore.java:105 — at least one session was produced")
                .isNotEmpty();
        assertThat(response.metadata())
                .as("4. RestSinapseCore.java:118 — execution metadata exists")
                .isNotNull();
        assertThat(response.metadata().coreVersion())
                .as("4. RestSinapseCore.java:118 — and names a core version, not null and not blank")
                .isNotNull()
                .isNotBlank();
        assertThat(response.metadata().randomSeed())
                .as("5. RestSinapseCore.java:121 — the seed echoed back is the seed that was sent")
                .isEqualTo(request.randomSeed());
        assertThat(response.sessions()).allSatisfy(session -> {
            assertThat(session.topicId())
                    .as("6. RestSinapseCore.java:129 — every session has a topic")
                    .isNotNull();
            assertThat(session.kind())
                    .as("6. RestSinapseCore.java:129 — every session has a kind")
                    .isNotNull();
            assertThat(session.scheduledStart())
                    .as("6. RestSinapseCore.java:130 — every session has a start instant")
                    .isNotNull();
            assertThat(session.durationMinutes())
                    .as("7. RestSinapseCore.java:133 — every session has a positive duration")
                    .isPositive();
        });
        assertThat(response.sessions()).extracting(PlanResponse.ScheduledSession::sequenceIndex)
                .as("8. RestSinapseCore.java:136 — no sequenceIndex repeats within the plan")
                .doesNotHaveDuplicates();
    }

    /** The four the platform trusts blindly, listed at the end of {@code CORE_CONTRACT_SURVEY} §3. */
    static void assertWhatThePlatformDoesNotCheck(PlanRequest request, PlanResponse response) {
        Instant horizonFrom = request.horizon().start().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant horizonUntil = request.horizon().end().plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Set<UUID> sent = request.topics().stream()
                .map(PlanRequest.Topic::id)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(response.sessions()).allSatisfy(session -> {
            assertThat(session.scheduledStart())
                    .as("9. not checked by the platform — scheduledStart is inside the horizon")
                    .isAfterOrEqualTo(horizonFrom)
                    .isBefore(horizonUntil);
            assertThat(insideSomeWindow(session, request.availability()))
                    .as("10. not checked by the platform — session %s fits inside a window that was "
                            + "sent", session.sequenceIndex())
                    .isTrue();
            assertThat(sent)
                    .as("12. not checked by the platform — topicId is one of the topics that were sent")
                    .contains(session.topicId());
        });
        assertNoOverlap(response);
    }

    /** 11. not checked by the platform — two sessions never occupy the same minute. */
    static void assertNoOverlap(PlanResponse response) {
        List<PlanResponse.ScheduledSession> ordered = inSequenceOrder(response);
        for (int index = 1; index < ordered.size(); index++) {
            PlanResponse.ScheduledSession previous = ordered.get(index - 1);
            assertThat(endOf(previous))
                    .as("11. session %s ends before session %s starts",
                            previous.sequenceIndex(), ordered.get(index).sequenceIndex())
                    .isBeforeOrEqualTo(ordered.get(index).scheduledStart());
        }
    }

    /**
     * {@code sequenceIndex} runs 0, 1, 2, … with no gap.
     *
     * <p>The platform only requires the indices to be distinct, so {@code [0, 7, 9]} would satisfy it.
     * A gap would let a consumer conclude that sessions had been lost in transit.
     */
    static void assertSequenceIsContiguous(PlanResponse response) {
        assertThat(inSequenceOrder(response))
                .extracting(PlanResponse.ScheduledSession::sequenceIndex)
                .as("sequenceIndex is contiguous from zero, not merely unique")
                .containsExactlyElementsOf(
                        IntStream.range(0, response.sessions().size()).boxed().toList());
    }

    /** No session of a dependent topic before every session of its hard prerequisites. */
    static void assertHardPrerequisitesComeFirst(PlanRequest request, PlanResponse response) {
        List<PlanResponse.ScheduledSession> ordered = inSequenceOrder(response);
        Set<UUID> scheduled = ordered.stream()
                .map(PlanResponse.ScheduledSession::topicId)
                .collect(Collectors.toUnmodifiableSet());

        request.prerequisites().stream()
                .filter(edge -> edge.strength() == EdgeStrength.HARD)
                .filter(edge -> scheduled.contains(edge.dependentTopicId()))
                .forEach(edge -> {
                    assertThat(scheduled)
                            .as("a dependent topic is only scheduled when its hard prerequisite is too")
                            .contains(edge.prerequisiteTopicId());
                    assertThat(firstIndexOf(ordered, edge.prerequisiteTopicId()))
                            .as("topic %s is studied after its hard prerequisite %s",
                                    edge.dependentTopicId(), edge.prerequisiteTopicId())
                            .isLessThan(firstIndexOf(ordered, edge.dependentTopicId()));
                });
    }

    static List<PlanResponse.ScheduledSession> inSequenceOrder(PlanResponse response) {
        return response.sessions().stream()
                .sorted(Comparator.comparingInt(PlanResponse.ScheduledSession::sequenceIndex))
                .toList();
    }

    static Instant endOf(PlanResponse.ScheduledSession session) {
        return session.scheduledStart().plus(session.durationMinutes(), ChronoUnit.MINUTES);
    }

    private static int firstIndexOf(List<PlanResponse.ScheduledSession> ordered, UUID topicId) {
        return ordered.stream()
                .filter(session -> topicId.equals(session.topicId()))
                .mapToInt(PlanResponse.ScheduledSession::sequenceIndex)
                .min()
                .orElseThrow();
    }

    private static boolean insideSomeWindow(PlanResponse.ScheduledSession session,
            List<PlanRequest.AvailabilitySlot> availability) {

        return availability.stream().anyMatch(slot ->
                !session.scheduledStart().isBefore(slot.start())
                        && !endOf(session).isAfter(slot.end()));
    }
}
