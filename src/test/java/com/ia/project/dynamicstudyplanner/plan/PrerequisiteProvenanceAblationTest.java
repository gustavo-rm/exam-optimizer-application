package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeProvenance;
import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The three provenance conditions are an ablation, so they have to differ — and only in that.
 *
 * <h2>What "three conditions" has to mean to be worth anything</h2>
 *
 * ADR 0006 put {@code provenance} on the wire so that the question "does the scheduler plan better
 * with curated edges, with curated plus curricular ordering, or with everything?" could be answered
 * without adding instrumentation later. Three conditions answer it only if three things hold, and
 * this file asserts all three:
 *
 * <ol>
 *   <li>they produce <b>different</b> plans on an instance where the extra edges bind — otherwise
 *       the key is wired up but inert, and a null result would be indistinguishable from a
 *       broken filter;</li>
 *   <li>each is <b>reproducible</b> — otherwise the difference between two arms cannot be
 *       attributed to the arm rather than to the run;</li>
 *   <li>each <b>says which it was</b> in the answer — otherwise a stored plan cannot be assigned to
 *       an arm at all, and the collected numbers are unattributable after the fact.</li>
 * </ol>
 *
 * <h2>The instance is built so that each condition adds a binding constraint</h2>
 *
 * Four topics in curricular order {@code A, B, C, D}, and one edge per provenance, each pointing
 * <b>against</b> that order so that it has to change something:
 *
 * <pre>
 *   CURATED         B -&gt; A     curated            forces B before A
 *   TEXTBOOK_ORDER  D -&gt; C     curated-textbook   ... and D before C
 *   DERIVED         C -&gt; B     all                ... and C before B, which chains to D,C,B,A
 * </pre>
 *
 * An instance whose extra edges happened to agree with the order already chosen would pass a
 * "distinct plans" assertion only by accident, and fail it for a correct implementation.
 */
@DisplayName("Provenance ablation: three conditions, three plans")
class PrerequisiteProvenanceAblationTest {

    private static final UUID A = topicId(1);
    private static final UUID B = topicId(2);
    private static final UUID C = topicId(3);
    private static final UUID D = topicId(4);

    private static List<PlanEngines.Case> engines() {
        return PlanEngines.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("the three conditions produce three different plans")
    void theThreeConditionsProduceDifferentPlans(PlanEngines.Case engine) {
        String curated = signature(engine.plan(under(EdgeProvenanceFilter.CURATED)));
        String textbook = signature(engine.plan(under(EdgeProvenanceFilter.CURATED_TEXTBOOK)));
        String all = signature(engine.plan(under(EdgeProvenanceFilter.ALL)));

        assertThat(List.of(curated, textbook, all))
                .as("""
                        Two provenance conditions produced the same plan on an instance built so
                        that each one adds a constraint the previous does not have.

                        Either the filter is not reaching the graph, or it is reaching only some of
                        its consumers. Every consumer has to see the same condition — both engines
                        and the prerequisite-centrality importance strategy — or the arms of the
                        ablation differ in more than one thing.""")
                .doesNotHaveDuplicates();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("each condition is reproducible on its own")
    void eachConditionIsReproducible(PlanEngines.Case engine) {
        for (EdgeProvenanceFilter filter : EdgeProvenanceFilter.values()) {
            assertThat(signature(engine.plan(under(filter))))
                    .as("condition %s did not reproduce", filter.id())
                    .isEqualTo(signature(engine.plan(under(filter))));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("the answer says which condition ran")
    void theAnswerEchoesTheCondition(PlanEngines.Case engine) {
        for (EdgeProvenanceFilter filter : EdgeProvenanceFilter.values()) {
            assertThat(engine.plan(under(filter)).fitness())
                    .as("a stored plan that does not name its condition cannot be attributed to one")
                    .containsEntry(PrerequisiteProvenance.FITNESS_KEY, filter.id());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("a narrower condition applies fewer edges, and reports that it did")
    void aNarrowerConditionAppliesFewerEdges(PlanEngines.Case engine) {
        Object curated = engine.plan(under(EdgeProvenanceFilter.CURATED))
                .fitness().get("prerequisite-edges-hard");
        Object all = engine.plan(under(EdgeProvenanceFilter.ALL))
                .fitness().get("prerequisite-edges-hard");

        assertThat(curated).isEqualTo(1);
        assertThat(all).isEqualTo(3);
    }

    @Test
    @DisplayName("an unknown condition is refused, never silently replaced by the default")
    void anUnknownConditionIsRefused() {
        PlanEngines.Case engine = PlanEngines.all().get(0);

        assertThatThrownBy(() -> engine.plan(withParam("provenance", "curated+derived")))
                .as("""
                        Falling back to the default would run one condition while the caller
                        recorded another. Every figure collected under that id would be attributed
                        to the wrong arm, and nothing in the answer would say so.""")
                .isInstanceOf(PlanRejectedException.class)
                .hasMessageContaining("curated+derived");
    }

    @Test
    @DisplayName("a condition that is not a string is refused too")
    void aNonStringConditionIsRefused() {
        PlanEngines.Case engine = PlanEngines.all().get(0);

        assertThatThrownBy(() -> engine.plan(withParam("provenance", 3)))
                .isInstanceOf(PlanRejectedException.class);
    }

    private static PlanRequest under(EdgeProvenanceFilter filter) {
        return withParam("provenance", filter.id());
    }

    private static PlanRequest withParam(String key, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put(key, value);

        List<PlanRequest.Topic> topics = new ArrayList<>();
        UUID[] ids = {A, B, C, D};
        for (int index = 0; index < ids.length; index++) {
            topics.add(new PlanRequest.Topic(ids[index], PlanRequests.SUBJECT_FIRST, index + 1,
                    "STANDARD", 30));
        }

        return new PlanRequest(PlanRequest.VERSION,
                new PlanRequest.Horizon(PlanRequests.HORIZON_START,
                        PlanRequests.HORIZON_START.plusDays(13)),
                availability(),
                List.of(new PlanRequest.Goal(PlanRequests.SUBJECT_FIRST, null, 3)),
                topics,
                List.of(edge(B, A, EdgeProvenance.CURATED),
                        edge(D, C, EdgeProvenance.TEXTBOOK_ORDER),
                        edge(C, B, EdgeProvenance.DERIVED)),
                List.of(), params, PlanRequests.SEED);
    }

    private static PlanRequest.PrerequisiteEdge edge(UUID prerequisite, UUID dependent,
            EdgeProvenance provenance) {

        return new PlanRequest.PrerequisiteEdge(prerequisite, dependent, EdgeStrength.HARD,
                provenance);
    }

    private static List<PlanRequest.AvailabilitySlot> availability() {
        List<PlanRequest.AvailabilitySlot> slots = new ArrayList<>();
        for (int day = 0; day < 14; day++) {
            Instant from = PlanRequests.HORIZON_START.plusDays(day)
                    .atTime(LocalTime.of(8, 0)).toInstant(ZoneOffset.UTC);
            slots.add(new PlanRequest.AvailabilitySlot(from, from.plusSeconds(4 * 3600L)));
        }
        return slots;
    }

    private static UUID topicId(int index) {
        return new UUID(0xd000000000004000L, 0x8000000000000000L | index);
    }

    /** The order the topics are studied in, which is what a provenance condition can change. */
    private static String signature(PlanResponse response) {
        return response.sessions().stream()
                .map(session -> session.topicId() + "@" + session.scheduledStart())
                .reduce((a, b) -> a + "|" + b)
                .orElse("<empty>");
    }
}
