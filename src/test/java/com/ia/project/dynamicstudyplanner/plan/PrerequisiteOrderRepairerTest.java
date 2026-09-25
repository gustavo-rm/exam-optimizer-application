package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeProvenance;
import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The repair pass: what it removes, what it must not, and that it always stops.
 *
 * <h2>Read with {@code SoftPrerequisiteOrderConstraint}</h2>
 *
 * The two are halves of one answer. The repair removes the inversions that can be removed; the
 * fitness term prices the ones that cannot. A test that only checked "no inversions remain" would
 * be asserting something false for every instance where a preference contradicts a rule — and
 * hiding the case the pair exists to handle.
 */
@DisplayName("Soft-order repair")
class PrerequisiteOrderRepairerTest {

    private static final UUID A = id(1);
    private static final UUID B = id(2);
    private static final UUID C = id(3);

    @Test
    @DisplayName("an inversion nothing forbids is removed")
    void removesAnInversionNothingForbids() {
        List<PlanRequest.Topic> order = topics(A, B);
        SoftPrerequisiteEdges soft = soft(edge(B, A, EdgeStrength.SOFT));

        PrerequisiteOrderRepairer.Result result =
                PrerequisiteOrderRepairer.repair(order, hardGraph(order), soft);

        assertThat(ids(result.order())).containsExactly(B, A);
        assertThat(result.inversionsBefore()).isEqualTo(1);
        assertThat(result.inversionsAfter()).isZero();
        assertThat(result.repaired()).isEqualTo(1);
    }

    @Test
    @DisplayName("an inversion a HARD edge forbids is left alone, and priced instead")
    void leavesAnInversionAHardEdgeForbids() {
        // A must come first (HARD) and B would ideally come first (SOFT). The rule wins, the
        // preference stays violated, and the count says so rather than the pass looping forever
        // trying to satisfy both.
        List<PlanRequest.Topic> order = topics(A, B);
        SoftPrerequisiteEdges soft = soft(edge(B, A, EdgeStrength.SOFT));
        HardPrerequisiteGraph hard = hardGraph(order, edge(A, B, EdgeStrength.HARD));

        PrerequisiteOrderRepairer.Result result =
                PrerequisiteOrderRepairer.repair(order, hard, soft);

        assertThat(ids(result.order()))
                .as("the hard constraint is not negotiable")
                .containsExactly(A, B);
        assertThat(result.inversionsAfter()).isEqualTo(1);
        assertThat(result.repaired()).isZero();
    }

    @Test
    @DisplayName("preferences that contradict each other terminate, with a residue")
    void contradictoryPreferencesTerminate() {
        // A before B and B before A cannot both hold. The pass must stop, not oscillate: it accepts
        // a move only when the total strictly drops, and the total is a non-negative integer.
        List<PlanRequest.Topic> order = topics(A, B);
        SoftPrerequisiteEdges soft = soft(edge(A, B, EdgeStrength.SOFT),
                edge(B, A, EdgeStrength.SOFT));

        PrerequisiteOrderRepairer.Result result =
                PrerequisiteOrderRepairer.repair(order, hardGraph(order), soft);

        assertThat(result.inversionsAfter())
                .as("exactly one of the two can hold, whichever order is chosen")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the pass is deterministic: the same input gives the same order")
    void isDeterministic() {
        List<PlanRequest.Topic> order = topics(A, B, C);
        SoftPrerequisiteEdges soft = soft(edge(C, A, EdgeStrength.SOFT),
                edge(B, A, EdgeStrength.SOFT));
        HardPrerequisiteGraph hard = hardGraph(order);

        assertThat(ids(PrerequisiteOrderRepairer.repair(order, hard, soft).order()))
                .isEqualTo(ids(PrerequisiteOrderRepairer.repair(order, hard, soft).order()));
    }

    @Test
    @DisplayName("with no preferences the order is returned untouched")
    void withoutPreferencesNothingChanges() {
        List<PlanRequest.Topic> order = topics(A, B, C);

        PrerequisiteOrderRepairer.Result result =
                PrerequisiteOrderRepairer.repair(order, hardGraph(order), soft());

        assertThat(ids(result.order())).containsExactly(A, B, C);
        assertThat(result.inversionsBefore()).isZero();
    }

    @Test
    @DisplayName("counting an order does not change it")
    void countingDoesNotReorder() {
        List<PlanRequest.Topic> order = topics(A, B);

        assertThat(PrerequisiteOrderRepairer.inversionsIn(order,
                soft(edge(B, A, EdgeStrength.SOFT))))
                .as("the baseline measures without repairing; the count must not move anything")
                .isEqualTo(1);
    }

    private static List<PlanRequest.Topic> topics(UUID... ids) {
        List<PlanRequest.Topic> topics = new ArrayList<>();
        for (int index = 0; index < ids.length; index++) {
            topics.add(new PlanRequest.Topic(ids[index], PlanRequests.SUBJECT_FIRST, index + 1,
                    "STANDARD", 30));
        }
        return topics;
    }

    private static List<UUID> ids(List<PlanRequest.Topic> topics) {
        return topics.stream().map(PlanRequest.Topic::id).toList();
    }

    private static PlanRequest.PrerequisiteEdge edge(UUID prerequisite, UUID dependent,
            EdgeStrength strength) {

        return new PlanRequest.PrerequisiteEdge(prerequisite, dependent, strength,
                EdgeProvenance.CURATED);
    }

    private static SoftPrerequisiteEdges soft(PlanRequest.PrerequisiteEdge... edges) {
        return SoftPrerequisiteEdges.of(topics(A, B, C), List.of(edges), EdgeProvenanceFilter.ALL);
    }

    private static HardPrerequisiteGraph hardGraph(List<PlanRequest.Topic> topics,
            PlanRequest.PrerequisiteEdge... edges) {

        return HardPrerequisiteGraph.of(topics, List.of(edges), EdgeProvenanceFilter.ALL);
    }

    private static UUID id(int index) {
        return new UUID(0xe000000000004000L, 0x8000000000000000L | index);
    }
}
