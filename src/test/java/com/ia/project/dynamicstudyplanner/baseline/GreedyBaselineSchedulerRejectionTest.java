package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.SUBJECT_FIRST;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_1;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_2;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_3;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_4;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.hard;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.slot;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Every request the scheduler refuses, and what the refusal names.
 *
 * <p>All of them are {@code 422}: syntactically valid documents whose instructions cannot be carried
 * out. The status mapping is pinned at the HTTP boundary by {@code BaselinePlanEndpointTest}; what is
 * pinned here is that the condition is detected at all, and that the answer names the offender rather
 * than leaving the caller to bisect their own payload.
 */
@DisplayName("Greedy baseline scheduler: refusals")
class GreedyBaselineSchedulerRejectionTest {

    private final GreedyBaselineScheduler scheduler = new GreedyBaselineScheduler("2.0.1");

    private PlanRejectedException refusalOf(PlanRequest request) {
        Throwable thrown = catchThrowable(() -> scheduler.schedule(request));

        assertThat(thrown)
                .as("the request must be refused, not planned")
                .isInstanceOf(PlanRejectedException.class);
        return (PlanRejectedException) thrown;
    }

    @Nested
    @DisplayName("Horizon")
    class Horizon {

        @Test
        @DisplayName("an absent horizon denotes no stretch of calendar")
        void absentHorizon() {
            PlanRejectedException refusal =
                    refusalOf(PlanRequests.builder().withHorizon(null).build());

            assertThat(refusal.reason()).isEqualTo("unusable-horizon");
            assertThat(refusal.offending()).containsExactly("horizon");
        }

        @Test
        @DisplayName("a half-declared horizon is refused too")
        void halfDeclaredHorizon() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withHorizon(new PlanRequest.Horizon(LocalDate.parse("2026-09-01"), null))
                    .build());

            assertThat(refusal.reason()).isEqualTo("unusable-horizon");
        }

        @Test
        @DisplayName("a horizon with no start date is refused on the same rule")
        void horizonWithoutAStart() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withHorizon(new PlanRequest.Horizon(null, LocalDate.parse("2026-09-28")))
                    .build());

            assertThat(refusal.reason()).isEqualTo("unusable-horizon");
        }

        @Test
        @DisplayName("a horizon that ends before it starts is named with both dates")
        void invertedHorizon() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withHorizon(new PlanRequest.Horizon(LocalDate.parse("2026-09-28"),
                            LocalDate.parse("2026-09-01")))
                    .build());

            assertThat(refusal.reason()).isEqualTo("unusable-horizon");
            assertThat(refusal.offending()).containsExactly("2026-09-28..2026-09-01");
        }
    }

    @Nested
    @DisplayName("Topics")
    class Topics {

        @Test
        @DisplayName("a request with no topics has nothing to plan")
        void noTopics() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withTopics(List.of()).withPrerequisites(List.of()).build());

            assertThat(refusal.reason()).isEqualTo("no-topics");
        }

        @Test
        @DisplayName("a topic without an identifier cannot be scheduled or referred to")
        void unidentifiedTopic() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                            topic(null, SUBJECT_FIRST, 2, 30)))
                    .withPrerequisites(List.of()).build());

            assertThat(refusal.reason()).isEqualTo("unidentified-topic");
            assertThat(refusal.offending()).containsExactly("topics[1]");
        }

        @Test
        @DisplayName("the same topic sent twice is refused rather than silently de-duplicated")
        void duplicateTopic() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                            topic(TOPIC_1, SUBJECT_FIRST, 2, 90)))
                    .withPrerequisites(List.of()).build());

            assertThat(refusal.reason()).isEqualTo("duplicate-topic");
            assertThat(refusal.offending()).containsExactly(TOPIC_1.toString());
        }

        @Test
        @DisplayName("a topic claiming no minutes would break the positive-duration invariant")
        void nonPositiveEstimate() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withTopics(List.of(topic(TOPIC_1, SUBJECT_FIRST, 1, 0),
                            topic(TOPIC_2, SUBJECT_FIRST, 2, -5),
                            topic(TOPIC_3, SUBJECT_FIRST, 3, 30)))
                    .withPrerequisites(List.of()).build());

            assertThat(refusal.reason()).isEqualTo("unusable-topic-duration");
            assertThat(refusal.offending())
                    .as("every offender is named, not just the first")
                    .containsExactly(TOPIC_1.toString(), TOPIC_2.toString());
        }
    }

    @Nested
    @DisplayName("Availability")
    class Availability {

        @Test
        @DisplayName("a request with no windows has nowhere to put a session")
        void noAvailability() {
            PlanRejectedException refusal =
                    refusalOf(PlanRequests.builder().withAvailability(List.of()).build());

            assertThat(refusal.reason()).isEqualTo("no-availability");
        }

        @Test
        @DisplayName("a window that closes before it opens is not a window")
        void invertedWindow() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withAvailability(List.of(
                            slot("2026-09-01T19:00:00Z", "2026-09-01T21:00:00Z"),
                            slot("2026-09-03T21:00:00Z", "2026-09-03T19:00:00Z")))
                    .build());

            assertThat(refusal.reason()).isEqualTo("unusable-availability-slot");
            assertThat(refusal.offending()).containsExactly("availability[1]");
        }

        @Test
        @DisplayName("a window with no opening instant is refused on the same rule")
        void windowWithoutAnOpening() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withAvailability(List.of(new PlanRequest.AvailabilitySlot(
                            null, Instant.parse("2026-09-01T21:00:00Z"))))
                    .build());

            assertThat(refusal.reason()).isEqualTo("unusable-availability-slot");
        }

        @Test
        @DisplayName("an empty window is refused on the same rule")
        void emptyWindow() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withAvailability(List.of(
                            slot("2026-09-01T19:00:00Z", "2026-09-01T19:00:00Z")))
                    .build());

            assertThat(refusal.reason()).isEqualTo("unusable-availability-slot");
        }

        @Test
        @DisplayName("a window missing one of its instants is refused on the same rule")
        void halfDeclaredWindow() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withAvailability(List.of(new PlanRequest.AvailabilitySlot(
                            Instant.parse("2026-09-01T19:00:00Z"), null)))
                    .build());

            assertThat(refusal.reason()).isEqualTo("unusable-availability-slot");
        }
    }

    @Nested
    @DisplayName("Hard prerequisite cycles")
    class Cycles {

        @Test
        @DisplayName("a two-topic cycle is refused with the topics of the cycle named")
        void twoTopicCycle() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withPrerequisites(List.of(hard(TOPIC_1, TOPIC_2), hard(TOPIC_2, TOPIC_1)))
                    .build());

            assertThat(refusal.reason()).isEqualTo("hard-prerequisite-cycle");
            assertThat(refusal.offending())
                    .containsExactlyInAnyOrder(TOPIC_1.toString(), TOPIC_2.toString());
            assertThat(refusal.getMessage())
                    .as("the chain reads as the loop it is, and says the cycle is not broken here")
                    .contains(TOPIC_1.toString(), TOPIC_2.toString(), "->")
                    .contains("does not choose an edge to ignore");
        }

        @Test
        @DisplayName("a topic that is its own prerequisite is a cycle of one")
        void selfCycle() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withPrerequisites(List.of(hard(TOPIC_1, TOPIC_1)))
                    .build());

            assertThat(refusal.reason()).isEqualTo("hard-prerequisite-cycle");
            assertThat(refusal.offending()).containsExactly(TOPIC_1.toString());
        }

        @Test
        @DisplayName("only the cycle is named, not everything the cycle blocks")
        void namesTheCycleAndNotItsDownstream() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withPrerequisites(List.of(hard(TOPIC_1, TOPIC_2), hard(TOPIC_2, TOPIC_3),
                            hard(TOPIC_3, TOPIC_1), hard(TOPIC_3, TOPIC_4)))
                    .build());

            assertThat(refusal.offending())
                    .as("TOPIC_4 is unplaceable but is not part of the cycle")
                    .containsExactlyInAnyOrder(TOPIC_1.toString(), TOPIC_2.toString(),
                            TOPIC_3.toString())
                    .doesNotContain(TOPIC_4.toString());
        }

        @Test
        @DisplayName("detection terminates rather than looping on the cycle")
        void detectionTerminates() {
            List<PlanRequest.PrerequisiteEdge> ring = List.of(hard(TOPIC_1, TOPIC_2),
                    hard(TOPIC_2, TOPIC_3), hard(TOPIC_3, TOPIC_4), hard(TOPIC_4, TOPIC_1));

            assertThatExceptionOfType(PlanRejectedException.class)
                    .as("a four-topic ring is answered, not spun on")
                    .isThrownBy(() -> scheduler.schedule(
                            PlanRequests.builder().withPrerequisites(ring).build()));
        }
    }

    @Nested
    @DisplayName("Nothing fits")
    class NothingFits {

        @Test
        @DisplayName("windows entirely outside the horizon leave nothing to allocate")
        void windowsOutsideTheHorizon() {
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withHorizon(new PlanRequest.Horizon(LocalDate.parse("2026-09-01"),
                            LocalDate.parse("2026-09-01")))
                    .withAvailability(List.of(
                            slot("2026-09-10T19:00:00Z", "2026-09-10T21:00:00Z")))
                    .build());

            assertThat(refusal.reason()).isEqualTo("plan-would-be-empty");
        }

        @Test
        @DisplayName("an empty plan is refused here rather than rejected by the platform")
        void firstTopicTooLongForEveryWindow() {
            UUID onlyTopic = TOPIC_1;
            PlanRejectedException refusal = refusalOf(PlanRequests.builder()
                    .withTopics(List.of(topic(onlyTopic, SUBJECT_FIRST, 1, 600)))
                    .withPrerequisites(List.of())
                    .build());

            assertThat(refusal.reason()).isEqualTo("plan-would-be-empty");
            assertThat(refusal.offending())
                    .as("the first topic of the study order is the one that did not fit")
                    .containsExactly(onlyTopic.toString());
            assertThat(refusal.getMessage())
                    .contains("the platform rejects one anyway");
        }
    }
}
