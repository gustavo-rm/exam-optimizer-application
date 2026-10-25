package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlacementVariant;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteProvenance;
import com.ia.project.dynamicstudyplanner.plan.SkipAheadCases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_1;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_2;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_3;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_4;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Opção P (EOA-12b) no ga: a cópia de teste da colocação")
class VariantSessionPlacementTest {

    private static final PrerequisiteProvenance ALL = new PrerequisiteProvenance("all");

    static Stream<PlanRequest> casos() {
        return Stream.of(SkipAheadCases.neverFitsInTheMiddle(), SkipAheadCases.skippedHasHardDependent(),
                SkipAheadCases.failureKeepsTheWindow(), SkipAheadCases.oneHour(50, 10),
                SkipAheadCases.everythingFits());
    }

    @ParameterizedTest(name = "caso {index}")
    @MethodSource("casos")
    @DisplayName("em BASE a cópia devolve o mesmo resultado de SessionPlacement.place")
    void baseIgualAProducao(PlanRequest request) {
        StudyPlan chromosome = sessions(request, 2);

        assertThat(snapshot(place(request, chromosome, PlacementVariant.BASE))).isEqualTo(snapshot(
                SessionPlacement.place(request, request.topics(), chromosome, itemsByTopic(request))));
    }

    @Test
    @DisplayName("(a) um tópico que nunca cabe no meio: a base para, P agenda o seguinte")
    void nuncaCabeNoMeio() {
        PlanRequest request = SkipAheadCases.neverFitsInTheMiddle();

        assertThat(topics(request, place(request, sessions(request, 1), PlacementVariant.BASE)))
                .containsExactly(TOPIC_1);
        assertThat(topics(request, place(request, sessions(request, 1), PlacementVariant.SKIP_AHEAD)))
                .containsExactly(TOPIC_1, TOPIC_3);
    }

    @Test
    @DisplayName("(b) o pulado tem dependente HARD: o dependente sai e as invariantes passam")
    void dependenteHardSai() {
        PlanRequest request = SkipAheadCases.skippedHasHardDependent();

        SessionPlacement.Result placed = place(request, sessions(request, 1), PlacementVariant.SKIP_AHEAD);

        assertThat(topics(request, placed)).containsExactly(TOPIC_1, TOPIC_4);
        assertThat(placed.topicsScheduled()).isEqualTo(2);
        TacticalPlans.checked(request, placed.plan(), graph(request));
    }

    @Test
    @DisplayName("(c) uma falha não consome janela: o seguinte usa o que sobrou da primeira")
    void falhaNaoConsome() {
        PlanRequest request = SkipAheadCases.failureKeepsTheWindow();

        SessionPlacement.Result placed = place(request, sessions(request, 1), PlacementVariant.SKIP_AHEAD);

        assertThat(topics(request, placed)).containsExactly(TOPIC_1, TOPIC_3);
        assertThat(TacticalPlans.firstStartOf(placed.plan(), topicIdsByItem(request), TOPIC_3))
                .isEqualTo(SkipAheadCases.FIRST_WINDOW.plus(40, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("(c) revisão que não cabe: o tópico fica com o estudo e a falha não consome")
    void revisaoQueNaoCabeNaoConsome() {
        PlanRequest request = SkipAheadCases.oneHour(50, 10);
        StudyPlan chromosome = sessions(request, 2);

        assertThat(topics(request, place(request, chromosome, PlacementVariant.BASE)))
                .containsExactly(TOPIC_1);
        SessionPlacement.Result placed = place(request, chromosome, PlacementVariant.SKIP_AHEAD);
        assertThat(topics(request, placed)).containsExactly(TOPIC_1, TOPIC_2);
        assertThat(placed.plan().getSchedule()).hasSize(2);
        assertThat(TacticalPlans.firstStartOf(placed.plan(), topicIdsByItem(request), TOPIC_2))
                .isEqualTo(SkipAheadCases.FIRST_WINDOW.plus(50, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("(d) tudo cabe: P devolve exatamente o resultado da base")
    void tudoCabeIgualABase() {
        PlanRequest request = SkipAheadCases.everythingFits();
        StudyPlan chromosome = sessions(request, 1);

        assertThat(snapshot(place(request, chromosome, PlacementVariant.SKIP_AHEAD)))
                .isEqualTo(snapshot(place(request, chromosome, PlacementVariant.BASE)));
    }

    private static SessionPlacement.Result place(PlanRequest request, StudyPlan chromosome,
            PlacementVariant variant) {

        return VariantSessionPlacement.place(request, request.topics(), chromosome,
                itemsByTopic(request), graph(request), variant);
    }

    private static TacticalPlans.Snapshot snapshot(SessionPlacement.Result result) {
        return TacticalPlans.Snapshot.of(result);
    }

    private static StudyPlan sessions(PlanRequest request, int each) {
        Map<PlanningItem, Integer> days = new LinkedHashMap<>();
        TopicPlanningItems.of(request.topics()).forEach(item -> days.put(item, each));
        return new StudyPlan(days);
    }

    private static List<UUID> topics(PlanRequest request, SessionPlacement.Result placed) {
        return TacticalPlans.topicsInCalendarOrder(placed.plan(), topicIdsByItem(request));
    }

    private static Map<PlanningItem, UUID> topicIdsByItem(PlanRequest request) {
        return TopicPlanningItems.topicIdsByItem(request.topics());
    }

    private static Map<PlanRequest.Topic, PlanningItem> itemsByTopic(PlanRequest request) {
        Map<PlanRequest.Topic, PlanningItem> byTopic = new LinkedHashMap<>();
        request.topics().forEach(topic -> byTopic.put(topic, TopicPlanningItems.toItem(topic)));
        return byTopic;
    }

    private static HardPrerequisiteGraph graph(PlanRequest request) {
        return HardPrerequisiteGraph.of(request.topics(), request.prerequisites(), ALL.resolve(request));
    }
}
