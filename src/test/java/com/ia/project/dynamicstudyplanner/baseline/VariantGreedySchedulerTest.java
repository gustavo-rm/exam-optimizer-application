package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlacementVariant;
import com.ia.project.dynamicstudyplanner.plan.PlanInvariantAssertions;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteProvenance;
import com.ia.project.dynamicstudyplanner.plan.SkipAheadCases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_1;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_2;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_3;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_4;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Opção P (EOA-12b) no guloso: a cópia de teste do motor inteiro")
class VariantGreedySchedulerTest {

    private static final PrerequisiteProvenance ALL = new PrerequisiteProvenance("all");
    private static final String CORE_VERSION = "2.0.1";

    private final GreedyBaselineScheduler production = new GreedyBaselineScheduler(CORE_VERSION, ALL);
    private final VariantGreedyScheduler base =
            new VariantGreedyScheduler(CORE_VERSION, ALL, PlacementVariant.BASE);
    private final VariantGreedyScheduler skipAhead =
            new VariantGreedyScheduler(CORE_VERSION, ALL, PlacementVariant.SKIP_AHEAD);

    static Stream<PlanRequest> casos() {
        return Stream.of(SkipAheadCases.neverFitsInTheMiddle(), SkipAheadCases.skippedHasHardDependent(),
                SkipAheadCases.failureKeepsTheWindow(), SkipAheadCases.revisionDoesNotFit(),
                SkipAheadCases.everythingFits());
    }

    @ParameterizedTest(name = "caso {index}")
    @MethodSource("casos")
    @DisplayName("em BASE a cópia devolve a mesma resposta do motor de produção")
    void baseIgualAProducao(PlanRequest request) {
        assertThat(base.plan(request).response()).isEqualTo(production.schedule(request));
    }

    @Test
    @DisplayName("(a) um tópico que nunca cabe no meio: a base para, P agenda o seguinte")
    void nuncaCabeNoMeio() {
        PlanRequest request = SkipAheadCases.neverFitsInTheMiddle();

        assertThat(topics(production.schedule(request))).containsExactly(TOPIC_1);
        PlanResponse response = skipAhead.plan(request).response();
        assertThat(topics(response)).containsExactly(TOPIC_1, TOPIC_3);
        assertThat(response.fitness().get("topics-unscheduled-ids"))
                .isEqualTo(List.of(TOPIC_2.toString()));
    }

    @Test
    @DisplayName("(b) o pulado tem dependente HARD: o dependente sai e as invariantes passam")
    void dependenteHardSai() {
        PlanRequest request = SkipAheadCases.skippedHasHardDependent();

        PlanResponse response = skipAhead.plan(request).response();

        assertThat(topics(response)).containsExactly(TOPIC_1, TOPIC_4);
        assertThat(response.fitness().get("topics-unscheduled-ids"))
                .isEqualTo(List.of(TOPIC_2.toString(), TOPIC_3.toString()));
        PlanOutputInvariants.check(request, response, HardPrerequisiteGraph.of(request.topics(),
                request.prerequisites(), ALL.resolve(request)));
        PlanInvariantAssertions.assertEveryInvariant(request, response);
    }

    @Test
    @DisplayName("(c) uma falha não consome janela: o seguinte usa o que sobrou da primeira")
    void falhaNaoConsome() {
        PlanResponse response = skipAhead.plan(SkipAheadCases.failureKeepsTheWindow()).response();

        assertThat(topics(response)).containsExactly(TOPIC_1, TOPIC_3);
        assertThat(startOf(response, TOPIC_3))
                .isEqualTo(SkipAheadCases.FIRST_WINDOW.plus(40, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("(c) estudo que cabe com revisão que não cabe: o estudo é devolvido sem consumir")
    void estudoDevolvidoSemConsumir() {
        PlanRequest request = SkipAheadCases.revisionDoesNotFit();

        assertThat(topics(production.schedule(request))).containsExactly(TOPIC_1);
        PlanResponse response = skipAhead.plan(request).response();
        assertThat(topics(response)).containsExactly(TOPIC_1, TOPIC_3);
        assertThat(startOf(response, TOPIC_3))
                .isEqualTo(SkipAheadCases.FIRST_WINDOW.plus(10, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("(d) tudo cabe: P devolve exatamente a resposta da base")
    void tudoCabeIgualABase() {
        PlanRequest request = SkipAheadCases.everythingFits();

        assertThat(skipAhead.plan(request).response()).isEqualTo(production.schedule(request));
    }

    private static List<UUID> topics(PlanResponse response) {
        return PlanInvariantAssertions.inSequenceOrder(response).stream()
                .map(PlanResponse.ScheduledSession::topicId)
                .distinct()
                .toList();
    }

    private static Instant startOf(PlanResponse response, UUID topic) {
        return PlanInvariantAssertions.inSequenceOrder(response).stream()
                .filter(session -> session.topicId().equals(topic))
                .map(PlanResponse.ScheduledSession::scheduledStart)
                .findFirst()
                .orElseThrow();
    }
}
