package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property the whole prerequisite stage exists for, over generated graphs.
 *
 * <h2>The assertion is the strong reading, not the weak one</h2>
 *
 * {@link PlanOutputInvariants} already refuses a plan whose dependent topic <b>starts</b> before its
 * prerequisite starts. This asserts something stricter: no session of a dependent may begin before
 * <b>every</b> session of its hard prerequisites has ended. The weak reading would let a plan
 * interleave a prerequisite's revision after its dependent's first session and still pass, which is
 * not what "study this first" means to a student.
 *
 * <p>Both engines happen to satisfy the strong reading for the same structural reason — each places
 * a topic's sessions contiguously and walks the study order forward — and that reason is exactly the
 * kind of thing that survives until someone optimises placement. Asserting the strong form is what
 * turns it from an accident into a promise.
 *
 * <h2>Why both engines are under the same test</h2>
 *
 * The two are the conditions of an experiment and are free to differ on which order they pick. They
 * are not free to differ on whether the order is legal: that is a protocol property, owed to the
 * platform identically. Parameterising over {@link PlanEngines#all()} means a third engine inherits
 * the whole file.
 */
@DisplayName("Prerequisite ordering over generated graphs")
class PrerequisiteOrderingTest {

    private static Stream<Arguments> shapesAndEngines() {
        List<PrerequisiteGraphs.Shape> shapes = List.of(
                PrerequisiteGraphs.chain(3),
                PrerequisiteGraphs.chain(12),
                PrerequisiteGraphs.diamonds(1),
                PrerequisiteGraphs.diamonds(3),
                PrerequisiteGraphs.layered(4, 3, 20260925L),
                PrerequisiteGraphs.layered(5, 4, 7L),
                PrerequisiteGraphs.withInvertedSoftEdges(PrerequisiteGraphs.chain(8)),
                PrerequisiteGraphs.withInvertedSoftEdges(PrerequisiteGraphs.diamonds(2)));

        List<Arguments> cases = new ArrayList<>();
        for (PrerequisiteGraphs.Shape shape : shapes) {
            for (PlanEngines.Case engine : PlanEngines.all()) {
                cases.add(Arguments.of(shape, engine));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{1} on {0}")
    @MethodSource("shapesAndEngines")
    @DisplayName("no session of a dependent begins before its hard prerequisites have finished")
    void noDependentRunsBeforeItsHardPrerequisites(PrerequisiteGraphs.Shape shape,
            PlanEngines.Case engine) {

        PlanResponse response = engine.plan(shape.request());
        Map<UUID, Instant> firstStart = firstStartByTopic(response);
        Map<UUID, Instant> lastEnd = lastEndByTopic(response);

        for (PlanRequest.PrerequisiteEdge edge : shape.hard()) {
            Instant dependentAt = firstStart.get(edge.dependentTopicId());
            if (dependentAt == null) {
                // Truncated away: the plan is a prefix of a topological order, so a topic that did
                // not fit takes every topic that depends on it with it. Nothing to check.
                continue;
            }
            Instant prerequisiteEnd = lastEnd.get(edge.prerequisiteTopicId());

            assertThat(prerequisiteEnd)
                    .as("%s: topic %s is scheduled but its hard prerequisite %s is not in the plan",
                            shape.name(), edge.dependentTopicId(), edge.prerequisiteTopicId())
                    .isNotNull();
            assertThat(prerequisiteEnd)
                    .as("%s: topic %s starts at %s, before its hard prerequisite %s finishes at %s",
                            shape.name(), edge.dependentTopicId(), dependentAt,
                            edge.prerequisiteTopicId(), prerequisiteEnd)
                    .isBeforeOrEqualTo(dependentAt);
        }
    }

    @ParameterizedTest(name = "{1} on {0}")
    @MethodSource("shapesAndEngines")
    @DisplayName("the same request and seed produce the same plan, session for session")
    void theSameSeedProducesTheSamePlan(PrerequisiteGraphs.Shape shape, PlanEngines.Case engine) {
        // The repair pass is new machinery on the path between the search and the answer, and it
        // reorders a list. A pass that iterated a hash-ordered collection would reproduce within a
        // JVM and diverge across two, which is the failure this repository has already had once.
        assertThat(signature(engine.plan(shape.request())))
                .isEqualTo(signature(engine.plan(shape.request())));
    }

    @ParameterizedTest(name = "{1} on {0}")
    @MethodSource("shapesAndEngines")
    @DisplayName("every scheduled topic keeps its sessions together")
    void sessionsOfOneTopicAreContiguous(PrerequisiteGraphs.Shape shape, PlanEngines.Case engine) {
        // The reason the strong reading above holds. Asserted separately so that a future placement
        // change breaks this with a message naming the cause, instead of breaking the ordering test
        // with a message naming a symptom.
        PlanResponse response = engine.plan(shape.request());

        Map<UUID, List<Integer>> positions = new LinkedHashMap<>();
        List<PlanResponse.ScheduledSession> ordered = byStart(response);
        for (int index = 0; index < ordered.size(); index++) {
            positions.computeIfAbsent(ordered.get(index).topicId(), key -> new ArrayList<>())
                    .add(index);
        }

        positions.forEach((topic, at) -> assertThat(at.get(at.size() - 1) - at.get(0))
                .as("%s: sessions of topic %s are split by another topic's session", shape.name(),
                        topic)
                .isEqualTo(at.size() - 1));
    }

    @ParameterizedTest(name = "{1} on {0}")
    @MethodSource("shapesAndEngines")
    @DisplayName("a partial plan names the topics it could not hold, and they are the missing ones")
    void aPartialPlanNamesWhatItLeftOut(PrerequisiteGraphs.Shape shape, PlanEngines.Case engine) {
        // One day of calendar against a syllabus that needs many: the plan is necessarily partial.
        // "Three topics did not fit" leaves the platform unable to tell the student which three,
        // which is the difference between a declared partial plan and a silently incomplete one.
        PlanRequest cramped = PrerequisiteGraphs.withOneDayOfAvailability(shape.request());
        PlanResponse response = engine.plan(cramped);

        List<String> scheduled = response.sessions().stream()
                .map(session -> session.topicId().toString())
                .distinct()
                .toList();
        @SuppressWarnings("unchecked")
        List<String> named = (List<String>) response.fitness().get("topics-unscheduled-ids");

        assertThat(named)
                .as("%s: the key is missing, so a partial plan says only how many were dropped",
                        shape.name())
                .isNotNull();
        assertThat(named)
                .as("%s: a named topic is actually in the plan", shape.name())
                .doesNotContainAnyElementsOf(scheduled);
        assertThat(named.size() + scheduled.size())
                .as("%s: named plus scheduled must account for every topic in scope", shape.name())
                .isEqualTo(cramped.topics().size());
    }

    private static List<PlanResponse.ScheduledSession> byStart(PlanResponse response) {
        return response.sessions().stream()
                .sorted(java.util.Comparator.comparing(PlanResponse.ScheduledSession::scheduledStart)
                        .thenComparing(PlanResponse.ScheduledSession::sequenceIndex))
                .toList();
    }

    private static Map<UUID, Instant> firstStartByTopic(PlanResponse response) {
        Map<UUID, Instant> first = new LinkedHashMap<>();
        response.sessions().forEach(session -> first.merge(session.topicId(),
                session.scheduledStart(),
                (existing, candidate) -> candidate.isBefore(existing) ? candidate : existing));
        return first;
    }

    private static Map<UUID, Instant> lastEndByTopic(PlanResponse response) {
        Map<UUID, Instant> last = new LinkedHashMap<>();
        response.sessions().forEach(session -> last.merge(session.topicId(),
                session.scheduledStart().plusSeconds(session.durationMinutes() * 60L),
                (existing, candidate) -> candidate.isAfter(existing) ? candidate : existing));
        return last;
    }

    /** Everything about a plan that a reordering could change. */
    private static String signature(PlanResponse response) {
        return response.sessions().stream()
                .map(session -> session.sequenceIndex() + ":" + session.topicId() + "@"
                        + session.scheduledStart() + "/" + session.durationMinutes())
                .reduce((a, b) -> a + "|" + b)
                .orElse("<empty>");
    }
}
