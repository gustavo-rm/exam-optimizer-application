package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeProvenance;
import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.RecallRating;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Requests to schedule, built from the reference document so the tests exercise realistic shapes.
 *
 * <h2>Why the default request mirrors {@code plan-request-v1.0.json}</h2>
 *
 * The identifiers, horizon, windows, goals, topics, edges and seed of {@link #builder()} are the ones
 * in {@code src/test/resources/contract/plan-request-v1.0.json} — the document
 * {@code sinapse-platform} keeps byte for byte identical on its side. A fixture invented here could
 * drift into shapes the platform never sends; this one cannot without the golden test noticing.
 *
 * <p>Each {@code with…} method changes one thing, so a test that varies the goals is visibly a test
 * about goals and not a second fixture with its own accidental differences.
 */
final class BaselineRequests {

    static final UUID SUBJECT_FIRST = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID SUBJECT_SECOND = UUID.fromString("22222222-2222-4222-8222-222222222222");

    static final UUID TOPIC_1 = UUID.fromString("a0000001-0000-4000-8000-000000000001");
    static final UUID TOPIC_2 = UUID.fromString("a0000002-0000-4000-8000-000000000002");
    static final UUID TOPIC_3 = UUID.fromString("a0000003-0000-4000-8000-000000000003");
    static final UUID TOPIC_4 = UUID.fromString("a0000004-0000-4000-8000-000000000004");

    /** A topic the default request does not carry, for edges that point outside the plan. */
    static final UUID TOPIC_OUTSIDE = UUID.fromString("b0000009-0000-4000-8000-000000000009");

    /** The seed of the reference document. Echoed back, never consumed. */
    static final long SEED = 7362819450172837461L;

    static final LocalDate HORIZON_START = LocalDate.parse("2026-09-01");
    static final LocalDate HORIZON_END = LocalDate.parse("2026-09-28");

    private BaselineRequests() {
    }

    static Builder builder() {
        return new Builder();
    }

    static PlanRequest.AvailabilitySlot slot(String from, String to) {
        return new PlanRequest.AvailabilitySlot(Instant.parse(from), Instant.parse(to));
    }

    static PlanRequest.Topic topic(UUID id, UUID subjectId, int position, int minutes) {
        return new PlanRequest.Topic(id, subjectId, position, "STANDARD", minutes);
    }

    static PlanRequest.Goal goal(UUID subjectId, String targetDate, int priority) {
        return new PlanRequest.Goal(subjectId,
                targetDate == null ? null : LocalDate.parse(targetDate), priority);
    }

    static PlanRequest.PrerequisiteEdge hard(UUID prerequisite, UUID dependent) {
        return new PlanRequest.PrerequisiteEdge(prerequisite, dependent, EdgeStrength.HARD,
                EdgeProvenance.CURATED);
    }

    static PlanRequest.PrerequisiteEdge soft(UUID prerequisite, UUID dependent) {
        return new PlanRequest.PrerequisiteEdge(prerequisite, dependent, EdgeStrength.SOFT,
                EdgeProvenance.TEXTBOOK_ORDER);
    }

    static PlanRequest.TopicHistory studied(UUID topicId, String lastStudiedAt,
            RecallRating... ratings) {
        return new PlanRequest.TopicHistory(topicId, Math.max(1, ratings.length), 60L,
                lastStudiedAt == null ? null : Instant.parse(lastStudiedAt), List.of(ratings));
    }

    /** One request, varied one field at a time. */
    static final class Builder {

        private PlanRequest.Horizon horizon = new PlanRequest.Horizon(HORIZON_START, HORIZON_END);

        private List<PlanRequest.AvailabilitySlot> availability = List.of(
                slot("2026-09-01T19:00:00Z", "2026-09-01T21:00:00Z"),
                slot("2026-09-03T19:00:00Z", "2026-09-03T21:00:00Z"),
                slot("2026-09-05T09:00:00Z", "2026-09-05T12:00:00Z"));

        private List<PlanRequest.Goal> goals = List.of(
                goal(SUBJECT_FIRST, "2026-09-20", 5),
                goal(SUBJECT_SECOND, "2026-10-15", 3));

        private List<PlanRequest.Topic> topics = List.of(
                topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                topic(TOPIC_2, SUBJECT_FIRST, 2, 60),
                topic(TOPIC_3, SUBJECT_SECOND, 1, 108),
                topic(TOPIC_4, SUBJECT_SECOND, 2, 180));

        private List<PlanRequest.PrerequisiteEdge> prerequisites = List.of(
                hard(TOPIC_1, TOPIC_2),
                soft(TOPIC_2, TOPIC_3),
                soft(TOPIC_3, TOPIC_4));

        private List<PlanRequest.TopicHistory> history = List.of();

        private long randomSeed = SEED;

        Builder withHorizon(PlanRequest.Horizon replacement) {
            this.horizon = replacement;
            return this;
        }

        Builder withAvailability(List<PlanRequest.AvailabilitySlot> replacement) {
            this.availability = replacement;
            return this;
        }

        Builder withGoals(List<PlanRequest.Goal> replacement) {
            this.goals = replacement;
            return this;
        }

        Builder withTopics(List<PlanRequest.Topic> replacement) {
            this.topics = replacement;
            return this;
        }

        Builder withPrerequisites(List<PlanRequest.PrerequisiteEdge> replacement) {
            this.prerequisites = replacement;
            return this;
        }

        Builder withHistory(List<PlanRequest.TopicHistory> replacement) {
            this.history = replacement;
            return this;
        }

        Builder withSeed(long replacement) {
            this.randomSeed = replacement;
            return this;
        }

        PlanRequest build() {
            return new PlanRequest(PlanRequest.VERSION, horizon, availability, goals, topics,
                    prerequisites, history,
                    Map.of("population-size", 120, "generations", 400), randomSeed);
        }
    }
}
