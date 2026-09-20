package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ia.project.dynamicstudyplanner.baseline.BaselineRequests.TOPIC_1;
import static com.ia.project.dynamicstudyplanner.baseline.BaselineRequests.TOPIC_2;
import static com.ia.project.dynamicstudyplanner.baseline.BaselineRequests.TOPIC_OUTSIDE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The output check, exercised against plans built by hand to be wrong.
 *
 * <h2>Why a plan the scheduler cannot produce is worth testing</h2>
 *
 * The checker is the last thing between a broken plan and a student's calendar, and the scheduler is
 * currently correct — so every rejecting branch of the checker is unreachable through
 * {@code GreedyBaselineScheduler} today. A check nothing exercises is a check nobody knows works: the
 * first change to the allocator that broke an invariant would be the first time the branch ran. These
 * responses are therefore assembled directly, one defect at a time.
 *
 * <p>Every failure is a {@code PlanInvariantViolationException}, which the controller answers with
 * {@code 500}: unlike a refusal, it means this module is wrong rather than the request.
 */
@DisplayName("Plan output invariants")
class PlanOutputInvariantsTest {

    private static final PlanRequest REQUEST = BaselineRequests.builder().build();

    private static final HardPrerequisiteGraph GRAPH =
            HardPrerequisiteGraph.of(REQUEST.topics(), REQUEST.prerequisites());

    private static final PlanResponse.ExecutionMetadata METADATA =
            new PlanResponse.ExecutionMetadata("2.0.1", BaselineRequests.SEED, 0, 0L);

    /** A plan the checker accepts: both topics inside the first window, prerequisite first. */
    private static final List<PlanResponse.ScheduledSession> VALID = List.of(
            session(TOPIC_1, "2026-09-01T19:00:00Z", 30, 0),
            session(TOPIC_2, "2026-09-01T19:30:00Z", 60, 1));

    private static PlanResponse.ScheduledSession session(UUID topicId, String start, int minutes,
            int index) {
        return new PlanResponse.ScheduledSession(topicId, SessionKind.STUDY, Instant.parse(start),
                minutes, index);
    }

    private static PlanResponse response(List<PlanResponse.ScheduledSession> sessions) {
        return new PlanResponse(PlanRequest.VERSION, sessions, Map.of(), METADATA);
    }

    private static void assertViolation(PlanResponse response, String fragment) {
        assertThatExceptionOfType(PlanInvariantViolationException.class)
                .isThrownBy(() -> PlanOutputInvariants.check(REQUEST, response, GRAPH))
                .withMessageContaining(fragment)
                .withMessageContaining("defect in the scheduler, not a bad request");
    }

    @Test
    @DisplayName("a correct plan passes")
    void acceptsACorrectPlan() {
        assertThatCode(() -> PlanOutputInvariants.check(REQUEST, response(VALID), GRAPH))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a partial plan passes: a dependent topic simply absent is not a violation")
    void acceptsAPartialPlanThatLeavesDependentsOut() {
        assertThatCode(() -> PlanOutputInvariants.check(REQUEST,
                response(List.of(VALID.get(0))), GRAPH))
                .doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("The eight the platform enforces")
    class PlatformChecks {

        @Test
        @DisplayName("2: a contract version other than the platform's")
        void wrongContractVersion() {
            assertViolation(new PlanResponse("0.9", VALID, Map.of(), METADATA),
                    "contractVersion must be 1.0");
        }

        @Test
        @DisplayName("3: no sessions at all")
        void noSessions() {
            assertViolation(response(List.of()), "must carry at least one session");
        }

        @Test
        @DisplayName("4: no execution metadata")
        void noMetadata() {
            assertViolation(new PlanResponse(PlanRequest.VERSION, VALID, Map.of(), null),
                    "must carry execution metadata");
        }

        @Test
        @DisplayName("4: metadata that does not name a core version")
        void noCoreVersion() {
            assertViolation(withMetadata(new PlanResponse.ExecutionMetadata(
                    null, BaselineRequests.SEED, 0, 0L)), "must name the core version");
        }

        @Test
        @DisplayName("4: a blank core version is as useless as none")
        void blankCoreVersion() {
            assertViolation(withMetadata(new PlanResponse.ExecutionMetadata(
                    "   ", BaselineRequests.SEED, 0, 0L)), "must name the core version");
        }

        @Test
        @DisplayName("5: a seed other than the one that was sent")
        void wrongSeed() {
            assertViolation(withMetadata(new PlanResponse.ExecutionMetadata("2.0.1", 42L, 0, 0L)),
                    "seed must be echoed exactly");
        }

        @Test
        @DisplayName("6: a session without a topic")
        void sessionWithoutATopic() {
            assertViolation(response(List.of(session(null, "2026-09-01T19:00:00Z", 30, 0))),
                    "session 0 is incomplete");
        }

        @Test
        @DisplayName("6: a session without a kind")
        void sessionWithoutAKind() {
            assertViolation(response(List.of(new PlanResponse.ScheduledSession(
                            TOPIC_1, null, Instant.parse("2026-09-01T19:00:00Z"), 30, 0))),
                    "session 0 is incomplete");
        }

        @Test
        @DisplayName("6: a session without a start instant")
        void sessionWithoutAStart() {
            assertViolation(response(List.of(new PlanResponse.ScheduledSession(
                            TOPIC_1, SessionKind.STUDY, null, 30, 0))),
                    "session 0 is incomplete");
        }

        @Test
        @DisplayName("7: a session of no length")
        void sessionOfNoLength() {
            assertViolation(response(List.of(session(TOPIC_1, "2026-09-01T19:00:00Z", 0, 0))),
                    "session 0 has no length");
        }

        @Test
        @DisplayName("8: a repeated sequence index")
        void repeatedSequenceIndex() {
            assertViolation(response(List.of(
                            session(TOPIC_1, "2026-09-01T19:00:00Z", 30, 0),
                            session(TOPIC_2, "2026-09-01T19:30:00Z", 30, 0))),
                    "sequenceIndex must not repeat");
        }

        private PlanResponse withMetadata(PlanResponse.ExecutionMetadata metadata) {
            return new PlanResponse(PlanRequest.VERSION, VALID, Map.of(), metadata);
        }
    }

    @Nested
    @DisplayName("The four the platform trusts, and the two on top")
    class WhatNobodyElseCatches {

        @Test
        @DisplayName("9: a start before the horizon opens")
        void startsBeforeTheHorizon() {
            assertViolation(response(List.of(session(TOPIC_1, "2026-08-31T19:00:00Z", 30, 0))),
                    "outside the horizon");
        }

        @Test
        @DisplayName("9: a start after the horizon closes")
        void startsAfterTheHorizon() {
            assertViolation(response(List.of(session(TOPIC_1, "2026-10-01T19:00:00Z", 30, 0))),
                    "outside the horizon");
        }

        @Test
        @DisplayName("10: a start inside the horizon but in no availability window")
        void outsideEveryWindow() {
            assertViolation(response(List.of(session(TOPIC_1, "2026-09-02T19:00:00Z", 30, 0))),
                    "does not fit inside any availability window");
        }

        @Test
        @DisplayName("10: a session that starts inside a window and runs past its close")
        void runsPastTheWindow() {
            assertViolation(response(List.of(session(TOPIC_1, "2026-09-01T20:30:00Z", 60, 0))),
                    "does not fit inside any availability window");
        }

        @Test
        @DisplayName("11: two sessions in the same minute")
        void overlappingSessions() {
            assertViolation(response(List.of(
                            session(TOPIC_1, "2026-09-01T19:00:00Z", 60, 0),
                            session(TOPIC_2, "2026-09-01T19:30:00Z", 30, 1))),
                    "session 0 runs into session 1");
        }

        @Test
        @DisplayName("12: a topic that was never sent")
        void unknownTopic() {
            assertViolation(response(List.of(session(TOPIC_OUTSIDE, "2026-09-01T19:00:00Z", 30, 0))),
                    "which was not sent");
        }

        @Test
        @DisplayName("sequenceIndex distinct but not contiguous")
        void nonContiguousSequence() {
            assertViolation(response(List.of(
                            session(TOPIC_1, "2026-09-01T19:00:00Z", 30, 0),
                            session(TOPIC_2, "2026-09-01T19:30:00Z", 30, 2))),
                    "must run from 0 to 1 with no gap");
        }

        @Test
        @DisplayName("sequenceIndex contiguous but not starting at zero")
        void sequenceDoesNotStartAtZero() {
            assertViolation(response(List.of(
                            session(TOPIC_1, "2026-09-01T19:00:00Z", 30, 1),
                            session(TOPIC_2, "2026-09-01T19:30:00Z", 30, 2))),
                    "must run from 0 to 1 with no gap");
        }

        @Test
        @DisplayName("a dependent topic scheduled before its hard prerequisite")
        void dependentBeforeItsPrerequisite() {
            assertViolation(response(List.of(
                            session(TOPIC_2, "2026-09-01T19:00:00Z", 30, 0),
                            session(TOPIC_1, "2026-09-01T19:30:00Z", 30, 1))),
                    "before its hard prerequisite");
        }

        @Test
        @DisplayName("a dependent topic scheduled with its hard prerequisite missing entirely")
        void prerequisiteMissingFromThePlan() {
            assertViolation(response(List.of(session(TOPIC_2, "2026-09-01T19:00:00Z", 30, 0))),
                    "is not in the plan at all");
        }
    }
}
