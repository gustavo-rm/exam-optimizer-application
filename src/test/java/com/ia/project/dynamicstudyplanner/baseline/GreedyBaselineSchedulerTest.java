package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.plan.PlanInvariantAssertions;
import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeProvenance;
import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.coreapi.contract.RecallRating;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.SUBJECT_FIRST;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.SUBJECT_SECOND;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_1;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_2;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_3;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_4;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_OUTSIDE;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.goal;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.hard;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.slot;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.soft;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.studied;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * What the greedy baseline scheduler produces, property by property.
 *
 * <p>No Spring context: the scheduler takes its only collaborator — the build version — as a
 * constructor argument, so it can be exercised directly. The wiring, the HTTP status codes and the
 * wire shape are pinned by {@code BaselinePlanEndpointTest}, which does boot the application.
 */
@DisplayName("Greedy baseline scheduler")
class GreedyBaselineSchedulerTest {

    private static final String CORE_VERSION = "2.0.1";

    private final GreedyBaselineScheduler scheduler = new GreedyBaselineScheduler(CORE_VERSION);

    private static List<UUID> topicsInOrder(PlanResponse response) {
        return PlanInvariantAssertions.inSequenceOrder(response).stream()
                .map(PlanResponse.ScheduledSession::topicId)
                .distinct()
                .toList();
    }

    @Nested
    @DisplayName("Output invariants")
    class OutputInvariants {

        @Test
        @DisplayName("the plan satisfies all eight invariants RestSinapseCore.validated enforces")
        void satisfiesTheEightThePlatformChecks() {
            PlanRequest request = PlanRequests.builder().build();

            PlanInvariantAssertions.assertPlatformChecks(request, scheduler.schedule(request));
        }

        @Test
        @DisplayName("and the four the platform does not check, which nobody else would catch")
        void satisfiesTheFourThePlatformDoesNotCheck() {
            PlanRequest request = PlanRequests.builder().build();

            PlanInvariantAssertions.assertWhatThePlatformDoesNotCheck(request,
                    scheduler.schedule(request));
        }

        @Test
        @DisplayName("sequenceIndex is contiguous from zero, not merely unique")
        void sequenceIndexIsContiguous() {
            PlanRequest request = PlanRequests.builder()
                    .withHistory(List.of(studied(TOPIC_1, "2026-08-01T10:00:00Z",
                            RecallRating.GOOD, RecallRating.AGAIN)))
                    .build();

            PlanResponse response = scheduler.schedule(request);

            assertThat(response.sessions()).hasSizeGreaterThan(4);
            PlanInvariantAssertions.assertSequenceIsContiguous(response);
        }

        @Test
        @DisplayName("no session of a dependent topic comes before its hard prerequisites")
        void dependentsNeverPrecedeTheirHardPrerequisites() {
            // TOPIC_4 is position 2 of the second subject and would otherwise be scheduled last;
            // the chain forces it first, so an ordering that ignored HARD edges would be visible.
            PlanRequest request = PlanRequests.builder()
                    .withPrerequisites(List.of(hard(TOPIC_4, TOPIC_3), hard(TOPIC_3, TOPIC_2),
                            hard(TOPIC_2, TOPIC_1)))
                    .withAvailability(List.of(slot("2026-09-01T06:00:00Z", "2026-09-01T18:00:00Z")))
                    .build();

            PlanResponse response = scheduler.schedule(request);

            PlanInvariantAssertions.assertHardPrerequisitesComeFirst(request, response);
            assertThat(topicsInOrder(response))
                    .as("the chain dictates the order outright, against position and goal priority")
                    .containsExactly(TOPIC_4, TOPIC_3, TOPIC_2, TOPIC_1);
        }
    }

    @Nested
    @DisplayName("Study order")
    class StudyOrdering {

        @Test
        @DisplayName("the reference request produces the schedule a reader can work out by hand")
        void referenceRequestProducesTheExpectedSchedule() {
            PlanRequest request = PlanRequests.builder().build();

            PlanResponse response = scheduler.schedule(request);

            assertThat(PlanInvariantAssertions.inSequenceOrder(response))
                    .extracting(PlanResponse.ScheduledSession::topicId,
                            PlanResponse.ScheduledSession::kind,
                            PlanResponse.ScheduledSession::scheduledStart,
                            PlanResponse.ScheduledSession::durationMinutes)
                    .containsExactly(
                            tuple(TOPIC_1, SessionKind.STUDY,
                                    Instant.parse("2026-09-01T19:00:00Z"), 30),
                            tuple(TOPIC_2, SessionKind.STUDY,
                                    Instant.parse("2026-09-01T19:30:00Z"), 60),
                            tuple(TOPIC_3, SessionKind.STUDY,
                                    Instant.parse("2026-09-03T19:00:00Z"), 108),
                            tuple(TOPIC_4, SessionKind.STUDY,
                                    Instant.parse("2026-09-05T09:00:00Z"), 180));
        }

        @Test
        @DisplayName("the higher goal priority wins outright, whatever the deadlines say")
        void higherPriorityWinsOutright() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                            topic(TOPIC_3, SUBJECT_SECOND, 1, 30)))
                    .withGoals(List.of(goal(SUBJECT_FIRST, "2026-09-02", 1),
                            goal(SUBJECT_SECOND, "2026-09-27", 5)))
                    .withPrerequisites(List.of())
                    .build();

            assertThat(topicsInOrder(scheduler.schedule(request)))
                    .as("priority 5 is served before priority 1 even though its deadline is later")
                    .containsExactly(TOPIC_3, TOPIC_1);
        }

        @Test
        @DisplayName("the nearer target date breaks a tie inside one priority level")
        void nearerDeadlineBreaksAPriorityTie() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                            topic(TOPIC_3, SUBJECT_SECOND, 1, 30)))
                    .withGoals(List.of(goal(SUBJECT_FIRST, "2026-09-25", 3),
                            goal(SUBJECT_SECOND, "2026-09-10", 3)))
                    .withPrerequisites(List.of())
                    .build();

            assertThat(topicsInOrder(scheduler.schedule(request)))
                    .containsExactly(TOPIC_3, TOPIC_1);
        }

        @Test
        @DisplayName("a subject with no target date sorts after one that declared it")
        void aMissingDeadlineSortsLast() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                            topic(TOPIC_3, SUBJECT_SECOND, 1, 30)))
                    .withGoals(List.of(goal(SUBJECT_FIRST, null, 3),
                            goal(SUBJECT_SECOND, "2026-09-25", 3)))
                    .withPrerequisites(List.of())
                    .build();

            assertThat(topicsInOrder(scheduler.schedule(request)))
                    .containsExactly(TOPIC_3, TOPIC_1);
        }

        @Test
        @DisplayName("a topic whose subject has no goal at all sorts below every goal")
        void aTopicWithoutAGoalSortsLast() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                            topic(TOPIC_3, SUBJECT_SECOND, 1, 30)))
                    .withGoals(List.of(goal(SUBJECT_SECOND, "2026-09-27", 1)))
                    .withPrerequisites(List.of())
                    .build();

            assertThat(topicsInOrder(scheduler.schedule(request)))
                    .as("priority 1 still outranks the absence of a goal, which counts as zero")
                    .containsExactly(TOPIC_3, TOPIC_1);
        }

        @Test
        @DisplayName("curricular position breaks a tie the goals leave")
        void positionBreaksAGoalTie() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_2, SUBJECT_FIRST, 2, 30),
                            topic(TOPIC_1, SUBJECT_FIRST, 1, 30)))
                    .withPrerequisites(List.of())
                    .build();

            assertThat(topicsInOrder(scheduler.schedule(request)))
                    .as("position decides, and the order the topics arrived in does not")
                    .containsExactly(TOPIC_1, TOPIC_2);
        }

        @Test
        @DisplayName("the topic identifier, as text, breaks everything the other three leave")
        void identifierIsTheFinalTieBreak() {
            // Same priority, same date, same position: only the identifier is left. The topics are
            // handed over in the opposite order, so a result of [TOPIC_1, TOPIC_3] can only come from
            // comparing identifiers, never from iteration order.
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_3, SUBJECT_SECOND, 1, 30),
                            topic(TOPIC_1, SUBJECT_FIRST, 1, 30)))
                    .withGoals(List.of(goal(SUBJECT_FIRST, "2026-09-20", 4),
                            goal(SUBJECT_SECOND, "2026-09-20", 4)))
                    .withPrerequisites(List.of())
                    .build();

            assertThat(topicsInOrder(scheduler.schedule(request)))
                    .containsExactly(TOPIC_1, TOPIC_3);
        }

        @Test
        @DisplayName("SOFT edges do not constrain the order — they are a preference, not a constraint")
        void softEdgesDoNotConstrainTheOrder() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 2, 30),
                            topic(TOPIC_2, SUBJECT_FIRST, 1, 30)))
                    .withPrerequisites(List.of(soft(TOPIC_1, TOPIC_2)))
                    .build();

            assertThat(topicsInOrder(scheduler.schedule(request)))
                    .as("position wins and the SOFT edge is knowingly violated")
                    .containsExactly(TOPIC_2, TOPIC_1);
        }

        @Test
        @DisplayName("a HARD edge whose prerequisite was not sent cannot block the dependent topic")
        void edgesPointingOutsideTheTopicsAreIgnored() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30)))
                    .withPrerequisites(List.of(hard(TOPIC_OUTSIDE, TOPIC_1)))
                    .build();

            assertThat(topicsInOrder(scheduler.schedule(request)))
                    .as("the topic is planned; the unplanned prerequisite cannot be satisfied here")
                    .containsExactly(TOPIC_1);
        }

        @Test
        @DisplayName("a HARD edge whose dependent was not sent constrains nothing either")
        void edgesWhoseDependentIsOutsideTheTopicsAreIgnored() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30)))
                    .withPrerequisites(List.of(hard(TOPIC_1, TOPIC_OUTSIDE)))
                    .build();

            assertThat(scheduler.schedule(request).fitness())
                    .as("no constraint is applied, because the dependent topic is not being planned")
                    .containsEntry("hard-edges-applied", 0);
        }

        @Test
        @DisplayName("an edge missing an endpoint or a strength carries no constraint")
        void malformedEdgesAreIgnored() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30)))
                    .withPrerequisites(List.of(
                            new PlanRequest.PrerequisiteEdge(null, TOPIC_1, EdgeStrength.HARD,
                                    EdgeProvenance.DERIVED),
                            new PlanRequest.PrerequisiteEdge(TOPIC_1, null, EdgeStrength.HARD,
                                    EdgeProvenance.DERIVED),
                            new PlanRequest.PrerequisiteEdge(TOPIC_1, TOPIC_1, null,
                                    EdgeProvenance.DERIVED)))
                    .build();

            PlanResponse response = scheduler.schedule(request);

            assertThat(topicsInOrder(response))
                    .as("the third edge is a self-loop, and would be a cycle if strength were read "
                            + "as HARD by default — it is not read at all")
                    .containsExactly(TOPIC_1);
            assertThat(response.fitness()).containsEntry("hard-edges-applied", 0);
        }

        @Test
        @DisplayName("a topic with two hard prerequisites waits for both, not just the first")
        void waitsForEveryHardPrerequisite() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                            topic(TOPIC_2, SUBJECT_FIRST, 2, 30),
                            topic(TOPIC_3, SUBJECT_FIRST, 3, 30)))
                    .withPrerequisites(List.of(hard(TOPIC_1, TOPIC_3), hard(TOPIC_2, TOPIC_3)))
                    .withAvailability(List.of(slot("2026-09-01T19:00:00Z", "2026-09-01T21:00:00Z")))
                    .build();

            PlanResponse response = scheduler.schedule(request);

            assertThat(topicsInOrder(response)).containsExactly(TOPIC_1, TOPIC_2, TOPIC_3);
            assertThat(response.fitness()).containsEntry("hard-edges-applied", 2);
        }

        @Test
        @DisplayName("a HARD edge sent twice constrains once")
        void duplicateEdgeConstrainsOnce() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 2, 30),
                            topic(TOPIC_2, SUBJECT_FIRST, 1, 30)))
                    .withPrerequisites(List.of(hard(TOPIC_1, TOPIC_2), hard(TOPIC_1, TOPIC_2)))
                    .build();

            PlanResponse response = scheduler.schedule(request);

            assertThat(topicsInOrder(response)).containsExactly(TOPIC_1, TOPIC_2);
            assertThat(response.fitness()).containsEntry("hard-edges-applied", 1);
        }

        @Test
        @DisplayName("two goals for one subject merge to the highest priority and earliest date")
        void repeatedGoalsMergeCommutatively() {
            List<PlanRequest.Goal> ascending = List.of(goal(SUBJECT_FIRST, "2026-09-25", 1),
                    goal(SUBJECT_FIRST, "2026-09-05", 5), goal(SUBJECT_SECOND, "2026-09-10", 4));
            List<PlanRequest.Goal> descending = List.of(ascending.get(2), ascending.get(1),
                    ascending.get(0));
            List<PlanRequest.Topic> topics = List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                    topic(TOPIC_3, SUBJECT_SECOND, 1, 30));

            PlanResponse first = scheduler.schedule(PlanRequests.builder()
                    .withTopics(topics).withGoals(ascending).withPrerequisites(List.of()).build());
            PlanResponse second = scheduler.schedule(PlanRequests.builder()
                    .withTopics(topics).withGoals(descending).withPrerequisites(List.of()).build());

            assertThat(topicsInOrder(first))
                    .as("priority 5 is taken for the first subject, so it leads")
                    .containsExactly(TOPIC_1, TOPIC_3);
            assertThat(topicsInOrder(second))
                    .as("the merge does not depend on the order the goals arrived in")
                    .isEqualTo(topicsInOrder(first));
        }
    }

    @Nested
    @DisplayName("Revision sessions")
    class Revisions {

        private static final String STALE = "2026-08-01T10:00:00Z";

        private PlanRequest oneTopicWith(List<PlanRequest.TopicHistory> history) {
            return PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30)))
                    .withPrerequisites(List.of())
                    .withAvailability(List.of(slot("2026-09-01T19:00:00Z", "2026-09-01T21:00:00Z")))
                    .withHistory(history)
                    .build();
        }

        @Test
        @DisplayName("a stale topic whose latest recall was poor gets one revision, half as long")
        void staleAndPoorlyRecalledEarnsARevision() {
            PlanResponse response = scheduler.schedule(oneTopicWith(
                    List.of(studied(TOPIC_1, STALE, RecallRating.GOOD, RecallRating.AGAIN))));

            assertThat(PlanInvariantAssertions.inSequenceOrder(response))
                    .extracting(PlanResponse.ScheduledSession::kind,
                            PlanResponse.ScheduledSession::scheduledStart,
                            PlanResponse.ScheduledSession::durationMinutes)
                    .containsExactly(
                            tuple(SessionKind.STUDY,
                                    Instant.parse("2026-09-01T19:00:00Z"), 30),
                            tuple(SessionKind.REVISION,
                                    Instant.parse("2026-09-01T19:30:00Z"), 15));
        }

        @Test
        @DisplayName("HARD is a poor recall too, and the threshold day itself counts as stale")
        void hardRecallOnTheThresholdDayEarnsARevision() {
            PlanResponse response = scheduler.schedule(oneTopicWith(List.of(
                    studied(TOPIC_1, "2026-08-18T00:00:00Z", RecallRating.HARD))));

            assertThat(response.sessions()).extracting(PlanResponse.ScheduledSession::kind)
                    .as("exactly fourteen days before the horizon opens is old enough")
                    .containsExactly(SessionKind.STUDY, SessionKind.REVISION);
        }

        @Test
        @DisplayName("one minute inside the threshold is recent, and earns nothing")
        void oneMinuteInsideTheThresholdIsRecent() {
            PlanResponse response = scheduler.schedule(oneTopicWith(List.of(
                    studied(TOPIC_1, "2026-08-18T00:01:00Z", RecallRating.AGAIN))));

            assertThat(response.sessions()).extracting(PlanResponse.ScheduledSession::kind)
                    .containsExactly(SessionKind.STUDY);
        }

        @Test
        @DisplayName("a good latest recall earns nothing, however old it is")
        void goodRecallEarnsNothing() {
            PlanResponse response = scheduler.schedule(oneTopicWith(List.of(
                    studied(TOPIC_1, STALE, RecallRating.AGAIN, RecallRating.GOOD))));

            assertThat(response.sessions()).extracting(PlanResponse.ScheduledSession::kind)
                    .as("the latest rating decides, not the worst one in the trajectory")
                    .containsExactly(SessionKind.STUDY);
        }

        @Test
        @DisplayName("a history entry with no ratings says nothing about recall, and earns nothing")
        void noRatingsEarnsNothing() {
            PlanResponse response = scheduler.schedule(oneTopicWith(
                    List.of(studied(TOPIC_1, STALE))));

            assertThat(response.sessions()).extracting(PlanResponse.ScheduledSession::kind)
                    .containsExactly(SessionKind.STUDY);
        }

        @Test
        @DisplayName("a history entry with no lastStudiedAt earns nothing")
        void noLastStudiedAtEarnsNothing() {
            PlanResponse response = scheduler.schedule(oneTopicWith(List.of(
                    studied(TOPIC_1, null, RecallRating.AGAIN))));

            assertThat(response.sessions()).extracting(PlanResponse.ScheduledSession::kind)
                    .containsExactly(SessionKind.STUDY);
        }

        /**
         * The 90-day trap, asserted as the design assumption it is.
         *
         * <p>A topic absent from {@code history} was either never studied or last studied outside the
         * platform's 90-day window, and the request cannot tell the two apart. This scheduler treats
         * the absence as "not studied" and schedules no revision — see {@link RevisionPolicy} for why
         * the cost of being wrong is bounded. Changing that choice has to break this test.
         */
        @Test
        @DisplayName("a topic absent from history gets no revision: absence is read as not studied")
        void absentHistoryIsReadAsNotStudied() {
            PlanResponse response = scheduler.schedule(oneTopicWith(List.of(
                    studied(TOPIC_2, STALE, RecallRating.AGAIN))));

            assertThat(response.sessions()).extracting(PlanResponse.ScheduledSession::kind)
                    .as("no entry for TOPIC_1, so no revision — and its full STUDY block stands")
                    .containsExactly(SessionKind.STUDY);
            assertThat(response.sessions()).first()
                    .extracting(PlanResponse.ScheduledSession::durationMinutes)
                    .as("the whole estimate, which is more time than a revision would have given")
                    .isEqualTo(30);
        }

        @Test
        @DisplayName("a repeated history entry keeps the first, so the document decides")
        void repeatedHistoryKeepsTheFirst() {
            PlanResponse response = scheduler.schedule(oneTopicWith(List.of(
                    studied(TOPIC_1, STALE, RecallRating.GOOD),
                    studied(TOPIC_1, STALE, RecallRating.AGAIN))));

            assertThat(response.sessions()).extracting(PlanResponse.ScheduledSession::kind)
                    .as("the first entry recalled GOOD, so no revision")
                    .containsExactly(SessionKind.STUDY);
        }
    }

    @Nested
    @DisplayName("Allocation")
    class Allocation {

        @Test
        @DisplayName("windows are clipped to the horizon, which is read in UTC")
        void windowsAreClippedToTheHorizon() {
            PlanRequest request = PlanRequests.builder()
                    .withHorizon(new PlanRequest.Horizon(LocalDate.parse("2026-09-01"),
                            LocalDate.parse("2026-09-01")))
                    .withAvailability(List.of(
                            slot("2026-08-31T23:00:00Z", "2026-09-01T01:00:00Z"),
                            slot("2026-09-01T23:00:00Z", "2026-09-02T01:00:00Z")))
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 60),
                            topic(TOPIC_2, SUBJECT_FIRST, 2, 60)))
                    .withPrerequisites(List.of())
                    .build();

            PlanResponse response = scheduler.schedule(request);

            assertThat(PlanInvariantAssertions.inSequenceOrder(response))
                    .extracting(PlanResponse.ScheduledSession::scheduledStart)
                    .as("the hour before the horizon and the hour after it are both unusable")
                    .containsExactly(Instant.parse("2026-09-01T00:00:00Z"),
                            Instant.parse("2026-09-01T23:00:00Z"));
            assertThat(response.fitness())
                    .as("two clipped hours, not the four that were sent")
                    .containsEntry("available-minutes", 120L);
        }

        @Test
        @DisplayName("overlapping windows produce no overlapping sessions")
        void overlappingWindowsProduceNoOverlappingSessions() {
            PlanRequest request = PlanRequests.builder()
                    .withAvailability(List.of(
                            slot("2026-09-01T20:00:00Z", "2026-09-01T22:00:00Z"),
                            slot("2026-09-01T19:00:00Z", "2026-09-01T21:00:00Z")))
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                            topic(TOPIC_2, SUBJECT_FIRST, 2, 60),
                            topic(TOPIC_3, SUBJECT_SECOND, 1, 60)))
                    .withPrerequisites(List.of())
                    .build();

            PlanResponse response = scheduler.schedule(request);

            PlanInvariantAssertions.assertNoOverlap(response);
            assertThat(PlanInvariantAssertions.inSequenceOrder(response))
                    .extracting(PlanResponse.ScheduledSession::scheduledStart)
                    .as("the cursor carries on through the second window rather than restarting it")
                    .containsExactly(Instant.parse("2026-09-01T19:00:00Z"),
                            Instant.parse("2026-09-01T19:30:00Z"),
                            Instant.parse("2026-09-01T20:30:00Z"));
        }

        @Test
        @DisplayName("a block is never split across two windows, even adjacent ones")
        void aBlockIsNeverSplitAcrossWindows() {
            PlanRequest request = PlanRequests.builder()
                    .withAvailability(List.of(
                            slot("2026-09-01T19:00:00Z", "2026-09-01T19:40:00Z"),
                            slot("2026-09-01T19:40:00Z", "2026-09-01T20:40:00Z")))
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 60)))
                    .withPrerequisites(List.of())
                    .build();

            assertThat(scheduler.schedule(request).sessions())
                    .extracting(PlanResponse.ScheduledSession::scheduledStart)
                    .as("the 40-minute window is abandoned rather than joined to the next one")
                    .containsExactly(Instant.parse("2026-09-01T19:40:00Z"));
        }

        @Test
        @DisplayName("not enough availability produces a declared partial plan, a prefix of the order")
        void notEnoughAvailabilityProducesADeclaredPartialPlan() {
            PlanRequest request = PlanRequests.builder()
                    .withAvailability(List.of(slot("2026-09-01T19:00:00Z", "2026-09-01T20:30:00Z")))
                    .build();

            PlanResponse response = scheduler.schedule(request);

            PlanInvariantAssertions.assertEveryInvariant(request, response);
            assertThat(topicsInOrder(response))
                    .as("a prefix of the study order, so the set stays closed under prerequisites")
                    .containsExactly(TOPIC_1, TOPIC_2);
            assertThat(response.fitness())
                    .as("the plan says it is partial rather than leaving it to be inferred")
                    .containsEntry("partial", true)
                    .containsEntry("topics-total", 4)
                    .containsEntry("topics-scheduled", 2L)
                    .containsEntry("topics-unscheduled", 2L);
        }

        @Test
        @DisplayName("a topic whose revision does not fit is dropped whole, study block included")
        void aTopicIsPlacedWholeOrNotAtAll() {
            PlanRequest request = PlanRequests.builder()
                    .withAvailability(List.of(slot("2026-09-01T19:00:00Z", "2026-09-01T20:00:00Z")))
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                            topic(TOPIC_2, SUBJECT_FIRST, 2, 30)))
                    .withPrerequisites(List.of())
                    .withHistory(List.of(studied(TOPIC_2, "2026-08-01T10:00:00Z",
                            RecallRating.AGAIN)))
                    .build();

            PlanResponse response = scheduler.schedule(request);

            assertThat(topicsInOrder(response))
                    .as("TOPIC_2's study block fitted but its revision did not, so it is unscheduled")
                    .containsExactly(TOPIC_1);
            assertThat(response.fitness()).containsEntry("partial", true);
        }
    }

    @Nested
    @DisplayName("Reported metadata and fitness")
    class Reported {

        @Test
        @DisplayName("fitness names only the terms this scheduler computes, plus the strategy")
        void fitnessNamesOnlyWhatItComputes() {
            PlanResponse response = scheduler.schedule(PlanRequests.builder().build());

            assertThat(response.fitness())
                    .containsOnlyKeys("strategy", "topics-total", "topics-scheduled",
                            "topics-unscheduled", "partial", "study-sessions", "revision-sessions",
                            "scheduled-minutes", "available-minutes", "availability-utilisation",
                            "hard-edges-applied")
                    .containsEntry("strategy", "greedy-baseline")
                    .containsEntry("study-sessions", 4L)
                    .containsEntry("revision-sessions", 0L)
                    .containsEntry("scheduled-minutes", 378L)
                    .containsEntry("available-minutes", 420L)
                    .containsEntry("availability-utilisation", 0.9d);
        }

        @Test
        @DisplayName("none of the genetic run's fitness terms are reported, because none are computed")
        void doesNotReportTermsItNeverComputed() {
            PlanResponse response = scheduler.schedule(PlanRequests.builder().build());

            assertThat(response.fitness())
                    .as("the reference document's terms belong to an evolutionary run, not to this one")
                    .doesNotContainKeys("weighted-score", "coverage", "overload-penalty",
                            "hard-violations");
        }

        @Test
        @DisplayName("the seed is echoed exactly and never consumed")
        void theSeedIsEchoedExactly() {
            PlanRequest request = PlanRequests.builder().withSeed(Long.MIN_VALUE).build();

            assertThat(scheduler.schedule(request).metadata().randomSeed())
                    .isEqualTo(Long.MIN_VALUE);
        }

        @Test
        @DisplayName("generations and elapsedMillis are zero, because neither happened")
        void generationsAndElapsedAreZero() {
            PlanResponse.ExecutionMetadata metadata =
                    scheduler.schedule(PlanRequests.builder().build()).metadata();

            assertThat(metadata.coreVersion()).isEqualTo(CORE_VERSION);
            assertThat(metadata.generations())
                    .as("no evolutionary run happened, and the field says so")
                    .isZero();
            assertThat(metadata.elapsedMillis())
                    .as("a measured duration would break byte-for-byte reproducibility")
                    .isZero();
        }

        @Test
        @DisplayName("an effortTier outside the closed set is accepted and changes nothing")
        void anUnknownEffortTierChangesNothing() {
            PlanRequest known = PlanRequests.builder().build();
            PlanRequest unknown = PlanRequests.builder()
                    .withTopics(known.topics().stream()
                            .map(topic -> new PlanRequest.Topic(topic.id(), topic.subjectId(),
                                    topic.position(), "GALACTIC", topic.estimatedMinutes()))
                            .toList())
                    .build();

            assertThat(scheduler.schedule(unknown).sessions())
                    .as("allocation is by estimatedMinutes; the band is carried past unread, so an "
                            + "unknown value is neither used nor refused")
                    .isEqualTo(scheduler.schedule(known).sessions());
        }
    }
}
