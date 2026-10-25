package com.ia.project.dynamicstudyplanner.sinapse.timeline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlacementVariant;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteProvenance;
import com.ia.project.dynamicstudyplanner.plan.SkipAheadCases;
import com.ia.project.dynamicstudyplanner.sinapse.TacticalPlans;
import com.ia.project.dynamicstudyplanner.sinapse.TopicPlanningItems;
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

@DisplayName("Opção P (EOA-12b) no ga-timeline: a cópia de teste do reparo")
class VariantTimelineRepairerTest {

    private static final PrerequisiteProvenance ALL = new PrerequisiteProvenance("all");

    static Stream<PlanRequest> casos() {
        return Stream.of(SkipAheadCases.neverFitsInTheMiddle(), SkipAheadCases.skippedHasHardDependent(),
                SkipAheadCases.failureKeepsTheWindow(), SkipAheadCases.oneHour(50, 10),
                SkipAheadCases.everythingFits());
    }

    @ParameterizedTest(name = "caso {index}")
    @MethodSource("casos")
    @DisplayName("em BASE a cópia devolve o mesmo plano de TimelineRepairer")
    void baseIgualAProducao(PlanRequest request) {
        Map<PlanningItem, List<TacticalStudyBlock>> blocks = blocks(request, 2);
        TimelineRepairer production = new TimelineRepairer(request, graph(request),
                topicIdsByItem(request), blocks);

        assertThat(TacticalPlans.entries(repair(request, blocks, PlacementVariant.BASE)))
                .isEqualTo(TacticalPlans.entries(production.repair(chromosome(blocks), null)));
    }

    @Test
    @DisplayName("(a) um tópico que nunca cabe no meio: a base para, P agenda o seguinte")
    void nuncaCabeNoMeio() {
        PlanRequest request = SkipAheadCases.neverFitsInTheMiddle();

        assertThat(topics(request, repair(request, blocks(request, 1), PlacementVariant.BASE)))
                .containsExactly(TOPIC_1);
        assertThat(topics(request, repair(request, blocks(request, 1), PlacementVariant.SKIP_AHEAD)))
                .containsExactly(TOPIC_1, TOPIC_3);
    }

    @Test
    @DisplayName("(b) o pulado tem dependente HARD: o dependente sai e as invariantes passam")
    void dependenteHardSai() {
        PlanRequest request = SkipAheadCases.skippedHasHardDependent();

        TacticalStudyPlan plan = repair(request, blocks(request, 1), PlacementVariant.SKIP_AHEAD);

        assertThat(topics(request, plan)).containsExactly(TOPIC_1, TOPIC_4);
        TacticalPlans.checked(request, plan, graph(request));
    }

    @Test
    @DisplayName("(c) uma falha não consome janela: o seguinte usa o que sobrou da primeira")
    void falhaNaoConsome() {
        PlanRequest request = SkipAheadCases.failureKeepsTheWindow();

        TacticalStudyPlan plan = repair(request, blocks(request, 1), PlacementVariant.SKIP_AHEAD);

        assertThat(topics(request, plan)).containsExactly(TOPIC_1, TOPIC_3);
        assertThat(TacticalPlans.firstStartOf(plan, topicIdsByItem(request), TOPIC_3))
                .isEqualTo(SkipAheadCases.FIRST_WINDOW.plus(40, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("(c) bloco seguinte que não cabe: o item fica com o primeiro e a falha não consome")
    void blocoSeguinteNaoConsome() {
        PlanRequest request = SkipAheadCases.oneHour(50, 10);
        Map<PlanningItem, List<TacticalStudyBlock>> blocks = blocks(request, 2);

        assertThat(topics(request, repair(request, blocks, PlacementVariant.BASE)))
                .containsExactly(TOPIC_1);
        TacticalStudyPlan plan = repair(request, blocks, PlacementVariant.SKIP_AHEAD);
        assertThat(topics(request, plan)).containsExactly(TOPIC_1, TOPIC_2);
        assertThat(plan.getSchedule()).hasSize(2);
        assertThat(TacticalPlans.firstStartOf(plan, topicIdsByItem(request), TOPIC_2))
                .isEqualTo(SkipAheadCases.FIRST_WINDOW.plus(50, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("(d) tudo cabe: P devolve exatamente o plano da base")
    void tudoCabeIgualABase() {
        PlanRequest request = SkipAheadCases.everythingFits();
        Map<PlanningItem, List<TacticalStudyBlock>> blocks = blocks(request, 1);

        assertThat(TacticalPlans.entries(repair(request, blocks, PlacementVariant.SKIP_AHEAD)))
                .isEqualTo(TacticalPlans.entries(repair(request, blocks, PlacementVariant.BASE)));
    }

    private static TacticalStudyPlan repair(PlanRequest request,
            Map<PlanningItem, List<TacticalStudyBlock>> blocks, PlacementVariant variant) {

        return new VariantTimelineRepairer(request, graph(request), topicIdsByItem(request), blocks,
                variant).repair(chromosome(blocks), null);
    }

    private static TacticalStudyPlan chromosome(Map<PlanningItem, List<TacticalStudyBlock>> blocks) {
        return TimelineChromosomes.inOrder(List.copyOf(blocks.keySet()), blocks);
    }

    /** Os blocos canônicos, na ordem do pedido: {@code first} sessões no primeiro tópico, 1 nos demais. */
    private static Map<PlanningItem, List<TacticalStudyBlock>> blocks(PlanRequest request, int first) {
        Map<PlanningItem, List<TacticalStudyBlock>> blocks = new LinkedHashMap<>();
        for (PlanRequest.Topic topic : request.topics()) {
            PlanningItem item = TopicPlanningItems.toItem(topic);
            blocks.put(item, TimelineChromosomes.blocksOf(item, topic, blocks.isEmpty() ? first : 1));
        }
        return blocks;
    }

    private static List<UUID> topics(PlanRequest request, TacticalStudyPlan plan) {
        return TacticalPlans.topicsInCalendarOrder(plan, topicIdsByItem(request));
    }

    private static Map<PlanningItem, UUID> topicIdsByItem(PlanRequest request) {
        return TopicPlanningItems.topicIdsByItem(request.topics());
    }

    private static HardPrerequisiteGraph graph(PlanRequest request) {
        return HardPrerequisiteGraph.of(request.topics(), request.prerequisites(), ALL.resolve(request));
    }
}
