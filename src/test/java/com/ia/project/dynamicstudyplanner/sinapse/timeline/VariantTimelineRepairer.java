package com.ia.project.dynamicstudyplanner.sinapse.timeline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.tactical.TacticalSlots;
import com.ia.project.dynamicstudyplanner.ga.tactical.repair.ChromosomeRepairer;
import com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlacementVariant;
import com.ia.project.dynamicstudyplanner.sinapse.AvailabilityWindows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cópia de teste de {@link TimelineRepairer}, com a colocação trocável (EOA-12b).
 *
 * <p>Construtor, ordem pedida, normalização e Kahn são os de produção, copiados linha a linha; só
 * {@code place} muda com a variante. Em {@link PlacementVariant#BASE} o reparo devolve o mesmo plano
 * que {@link TimelineRepairer}. Em {@link PlacementVariant#SKIP_AHEAD} vale a opção P de
 * {@code docs/MEDICAO_ALOCADOR.md} §2: o primeiro bloco de um item é obrigatório, e se ele não cabe o
 * item e os seus dependentes {@code HARD} transitivos são pulados sem tentativa; um bloco seguinte que
 * não cabe encerra os blocos do item, que fica com os já colocados; nenhuma falha consome janela.
 *
 * <p>{@code TimelineSearch} recebe o {@code TimelineRepairer} concreto, que é {@code final}; a cópia
 * entra na busca por um dublê do Mockito que delega a ela ({@code VariantTimelineEngine}). Nada em
 * {@code src/main} muda.
 */
public final class VariantTimelineRepairer implements ChromosomeRepairer {

    private final Map<PlanningItem, List<TacticalStudyBlock>> canonical;
    private final AvailabilityAllocator prepared;
    private final Map<PlanningItem, Integer> positions;
    private final int[][] dependents;
    private final int[] baseIndegree;
    private final PlacementVariant variant;

    public VariantTimelineRepairer(PlanRequest request, HardPrerequisiteGraph graph,
            Map<PlanningItem, UUID> topicIdsByItem,
            Map<PlanningItem, List<TacticalStudyBlock>> canonical, PlacementVariant variant) {

        this.canonical = Collections.unmodifiableMap(new LinkedHashMap<>(canonical));
        this.prepared = AvailabilityAllocator.over(request);
        this.variant = variant;

        Map<PlanningItem, Integer> byPosition = new LinkedHashMap<>();
        canonical.keySet().forEach(item -> byPosition.put(item, byPosition.size()));
        this.positions = Collections.unmodifiableMap(byPosition);

        Map<UUID, PlanningItem> itemsById = new LinkedHashMap<>();
        topicIdsByItem.forEach((item, id) -> itemsById.put(id, item));

        int size = byPosition.size();
        List<List<Integer>> dependentsOf = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            dependentsOf.add(new ArrayList<>());
        }
        this.baseIndegree = new int[size];

        byPosition.forEach((item, at) -> {
            for (UUID prerequisiteId : graph.prerequisitesOf(topicIdsByItem.get(item))) {
                PlanningItem prerequisite = itemsById.get(prerequisiteId);
                Integer from = prerequisite == null ? null : byPosition.get(prerequisite);
                if (from != null) {
                    baseIndegree[at]++;
                    dependentsOf.get(from).add(at);
                }
            }
        });

        this.dependents = new int[size][];
        for (int index = 0; index < size; index++) {
            this.dependents[index] =
                    dependentsOf.get(index).stream().mapToInt(Integer::intValue).toArray();
        }
    }

    @Override
    public TacticalStudyPlan repair(TacticalStudyPlan plan, EvolutionContext context) {
        Map<PlanningItem, List<TacticalStudyBlock>> observed =
                groupByItem(blocksInCalendarOrder(plan));
        List<PlanningItem> requested = requestedOrder(observed.keySet());
        List<PlanningItem> order = topologicalOrder(requested);
        Map<PlanningItem, List<TacticalStudyBlock>> blocks = normalised(observed);
        return variant == PlacementVariant.BASE ? place(order, blocks) : skipAhead(order, blocks);
    }

    private List<PlanningItem> requestedOrder(Set<PlanningItem> observed) {
        List<PlanningItem> order = new ArrayList<>(observed);
        canonical.keySet().stream().filter(item -> !observed.contains(item)).forEach(order::add);
        return order;
    }

    private Map<PlanningItem, List<TacticalStudyBlock>> normalised(
            Map<PlanningItem, List<TacticalStudyBlock>> observed) {

        Map<PlanningItem, List<TacticalStudyBlock>> fixed = new LinkedHashMap<>();
        canonical.forEach((item, template) -> {
            List<TacticalStudyBlock> seen = observed.getOrDefault(item, List.of());
            List<TacticalStudyBlock> blocks = new ArrayList<>(template.size());
            for (int index = 0; index < template.size(); index++) {
                TacticalStudyBlock canon = template.get(index);
                blocks.add(index < seen.size()
                        ? new TacticalStudyBlock(item, seen.get(index).methodology(),
                                canon.durationMinutes())
                        : canon);
            }
            fixed.put(item, blocks);
        });
        return fixed;
    }

    private static List<TacticalStudyBlock> blocksInCalendarOrder(TacticalStudyPlan plan) {
        List<TacticalStudyBlock> blocks = new ArrayList<>(plan.getSchedule().size());
        TacticalSlots.ordered(plan).forEach(slot -> blocks.add(plan.getSchedule().get(slot)));
        return blocks;
    }

    private static Map<PlanningItem, List<TacticalStudyBlock>> groupByItem(
            List<TacticalStudyBlock> blocks) {

        Map<PlanningItem, List<TacticalStudyBlock>> byItem = new LinkedHashMap<>();
        blocks.forEach(block ->
                byItem.computeIfAbsent(block.item(), key -> new ArrayList<>()).add(block));
        return byItem;
    }

    /** Cópia literal do Kahn de produção, com a mesma estrutura de passadas. */
    private List<PlanningItem> topologicalOrder(List<PlanningItem> preference) {
        int[] pending = baseIndegree.clone();
        boolean[] placed = new boolean[pending.length];
        List<PlanningItem> order = new ArrayList<>(preference.size());

        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (PlanningItem candidate : preference) {
                int at = positions.get(candidate);
                if (placed[at] || pending[at] > 0) {
                    continue;
                }
                order.add(candidate);
                placed[at] = true;
                progressed = true;
                for (int dependent : dependents[at]) {
                    pending[dependent]--;
                }
            }
        }

        preference.stream().filter(item -> !placed[positions.get(item)]).forEach(order::add);
        return order;
    }

    /** Cópia de {@code TimelineRepairer.place}: para no primeiro bloco que não couber. */
    private TacticalStudyPlan place(List<PlanningItem> order,
            Map<PlanningItem, List<TacticalStudyBlock>> byItem) {

        AvailabilityAllocator allocator = prepared.rewound();
        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();

        for (PlanningItem item : order) {
            for (TacticalStudyBlock block : byItem.get(item)) {
                Optional<Instant> start = allocator.place((int) block.durationMinutes());
                if (start.isEmpty()) {
                    return new TacticalStudyPlan(schedule);
                }
                put(schedule, start.get(), block);
            }
        }
        return new TacticalStudyPlan(schedule);
    }

    /** Opção P. Ver o comentário da classe. */
    private TacticalStudyPlan skipAhead(List<PlanningItem> order,
            Map<PlanningItem, List<TacticalStudyBlock>> byItem) {

        throw new UnsupportedOperationException("P ainda não implementada");
    }

    private static void put(Map<TimeSlot, TacticalStudyBlock> schedule, Instant start,
            TacticalStudyBlock block) {

        Instant end = start.plusSeconds(block.durationMinutes() * 60L);
        schedule.put(new TimeSlot(AvailabilityWindows.toLocal(start),
                AvailabilityWindows.toLocal(end)), block);
    }
}
