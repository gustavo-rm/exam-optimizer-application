package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator;
import com.ia.project.dynamicstudyplanner.plan.HardDependents;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlacementVariant;
import com.ia.project.dynamicstudyplanner.plan.SkipAheadAllocator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * Cópia de teste de {@link SessionPlacement#place}, com a colocação trocável (EOA-12b).
 *
 * <p>Em {@link PlacementVariant#BASE} é a colocação de produção, linha a linha. Em
 * {@link PlacementVariant#SKIP_AHEAD} vale a opção P de {@code docs/MEDICAO_ALOCADOR.md} §2: a
 * primeira sessão de um tópico é obrigatória, e se ela não cabe o tópico e os seus dependentes
 * {@code HARD} transitivos são pulados sem tentativa; as revisões continuam discricionárias, como na
 * produção, e a primeira que não cabe encerra as do tópico; nenhuma falha consome janela.
 */
public final class VariantSessionPlacement {

    private VariantSessionPlacement() {
    }

    /**
     * Coloca o plano.
     *
     * @param request      o pedido, para as janelas e o horizonte
     * @param order        os tópicos na ordem de colocação
     * @param chromosome   a alocação, lida como sessões por tópico
     * @param itemsByTopic o item de planejamento de cada tópico
     * @param graph        as arestas {@code HARD} aplicáveis, para a regra P4
     * @param variant      qual colocação
     * @return o calendário e o que coube, no formato de produção
     */
    public static SessionPlacement.Result place(PlanRequest request, List<PlanRequest.Topic> order,
            StudyPlan chromosome, Map<PlanRequest.Topic, PlanningItem> itemsByTopic,
            HardPrerequisiteGraph graph, PlacementVariant variant) {

        if (variant == PlacementVariant.SKIP_AHEAD) {
            return skipAhead(request, order, chromosome, itemsByTopic, graph);
        }
        AvailabilityAllocator allocator = AvailabilityAllocator.over(request);
        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();
        int scheduled = 0;
        long minutes = 0;

        for (PlanRequest.Topic topic : order) {
            PlanningItem item = itemsByTopic.get(topic);
            List<Block> blocks = placeTopic(topic, item, chromosome.getDaysForItem(item),
                    allocator::place);
            if (blocks.isEmpty()) {
                break;
            }
            for (Block block : blocks) {
                schedule.put(block.slot(), block.block());
                minutes += block.block().durationMinutes();
            }
            scheduled++;
        }

        return new SessionPlacement.Result(new TacticalStudyPlan(schedule), scheduled, order.size(),
                minutes);
    }

    /** Opção P. Ver o comentário da classe. */
    private static SessionPlacement.Result skipAhead(PlanRequest request,
            List<PlanRequest.Topic> order, StudyPlan chromosome,
            Map<PlanRequest.Topic, PlanningItem> itemsByTopic, HardPrerequisiteGraph graph) {

        SkipAheadAllocator allocator = SkipAheadAllocator.over(request);
        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();
        Set<UUID> skipped = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        int scheduled = 0;
        long minutes = 0;

        for (PlanRequest.Topic topic : order) {
            if (skipped.contains(topic.id())) {
                continue;
            }
            PlanningItem item = itemsByTopic.get(topic);
            List<Block> blocks = placeTopic(topic, item, chromosome.getDaysForItem(item),
                    allocator::place);
            if (blocks.isEmpty()) {
                skipped.add(topic.id());
                skipped.addAll(HardDependents.of(graph, topic.id()));
                continue;
            }
            for (Block block : blocks) {
                schedule.put(block.slot(), block.block());
                minutes += block.block().durationMinutes();
            }
            scheduled++;
        }

        return new SessionPlacement.Result(new TacticalStudyPlan(schedule), scheduled, order.size(),
                minutes);
    }

    /**
     * Cópia de {@code SessionPlacement.placeTopic}, com o alocador recebido como função.
     *
     * <p>A função é {@code AvailabilityAllocator::place} na base e {@code SkipAheadAllocator::place}
     * em P. Fora isso o corpo é o de produção.
     */
    private static List<Block> placeTopic(PlanRequest.Topic topic, PlanningItem item, int sessions,
            IntFunction<Optional<Instant>> allocator) {

        List<Block> blocks = new ArrayList<>();
        Optional<Instant> first = allocator.apply(topic.estimatedMinutes());
        if (first.isEmpty()) {
            return List.of();
        }
        blocks.add(block(item, first.get(), topic.estimatedMinutes(),
                StudyMethodology.ACTIVE_RECALL));

        int revisions = Math.max(0, sessions - 1);
        int revisionMinutes = SessionPlacement.revisionMinutes(topic.estimatedMinutes());
        for (int index = 0; index < revisions; index++) {
            Optional<Instant> again = allocator.apply(revisionMinutes);
            if (again.isEmpty()) {
                break;
            }
            blocks.add(block(item, again.get(), revisionMinutes,
                    StudyMethodology.SPACED_REPETITION_REVIEW));
        }
        return blocks;
    }

    private static Block block(PlanningItem item, Instant start, int minutes,
            StudyMethodology methodology) {

        TimeSlot slot = new TimeSlot(AvailabilityWindows.toLocal(start),
                AvailabilityWindows.toLocal(start.plusSeconds(minutes * 60L)));
        return new Block(slot, new TacticalStudyBlock(item, methodology, minutes));
    }

    private record Block(TimeSlot slot, TacticalStudyBlock block) {
    }
}
