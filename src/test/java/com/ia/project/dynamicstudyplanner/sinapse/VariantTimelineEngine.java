package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;
import com.ia.project.dynamicstudyplanner.ga.tactical.TacticalSlots;
import com.ia.project.dynamicstudyplanner.ga.tactical.repair.SpacedRepetitionRepairer;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.crossover.DayBoundaryCrossover;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation.BlockSwapMutation;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation.MethodologyMutation;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlacementVariant;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.plan.PlanRequestGuard;
import com.ia.project.dynamicstudyplanner.plan.PrecedencePolicy;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteOrderRepairer;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteReport;
import com.ia.project.dynamicstudyplanner.plan.SoftPrerequisiteEdges;
import com.ia.project.dynamicstudyplanner.plan.VariantEngine;
import com.ia.project.dynamicstudyplanner.plan.VariantPlan;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategy;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineChromosomes;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineRepairer;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineSearch;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.VariantTimelineRepairer;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cópia de teste de {@link TimelinePlanEngine}, com a colocação trocável (EOA-12b).
 *
 * <h2>A busca roda inteira com a variante</h2>
 *
 * No {@code ga-timeline} a colocação está dentro do reparo, e o reparo roda em cada semente e em cada
 * descendente; P muda a busca, não só o plano final. {@link TimelineSearch} recebe o
 * {@link TimelineRepairer} concreto, que é {@code final} e não tem interface, e a D2b não autoriza
 * extrair uma. Então a busca de produção recebe um dublê do Mockito ({@code stubOnly}, para não guardar
 * as milhares de invocações) cujo {@code repair} delega a {@link VariantTimelineRepairer}. O laço, os
 * operadores, a seleção e a aptidão são os de produção.
 *
 * <p>Em {@link PlacementVariant#BASE} a resposta é a mesma do motor de produção, e a medição confere o
 * hash em toda célula.
 */
public final class VariantTimelineEngine implements VariantEngine {

    /** Os colaboradores de produção, agrupados para o construtor caber no limite de parâmetros. */
    public record Beans(FitnessComposition composition, RetentionAlgorithm retention,
            RequestConditions conditions, SelectionStrategy selection,
            SpacedRepetitionRepairer retentionRepairer, GeneticSearchBudget budget,
            String coreVersion) {
    }

    private final Beans beans;
    private final PlacementVariant variant;

    public VariantTimelineEngine(Beans beans, PlacementVariant variant) {
        this.beans = beans;
        this.variant = variant;
    }

    @Override
    public String id() {
        return TimelinePlanEngine.ID;
    }

    @Override
    public VariantPlan plan(PlanRequest request) {
        PlanRequestGuard.check(request);
        EffortTierBands.checkEvery(request.topics());

        Random previous = RandomProvider.getInstance();
        RandomProvider.setInstance(new Random(request.randomSeed()));
        try {
            return run(request);
        } finally {
            RandomProvider.setInstance(previous);
        }
    }

    private VariantPlan run(PlanRequest request) {
        RequestConditions conditions = beans.conditions();
        EdgeProvenanceFilter filter = conditions.provenance(request);
        PrecedencePolicy policy = conditions.precedence(request, PrecedencePolicy.WEIGHTED);
        HardPrerequisiteGraph graph =
                HardPrerequisiteGraph.of(request.topics(), request.prerequisites(), filter);
        SoftPrerequisiteEdges soft =
                SoftPrerequisiteEdges.of(request.topics(), request.prerequisites(), filter);

        ImportanceStrategy importance = conditions.importance(request);
        List<PlanningItem> items = TopicPlanningItems.of(request.topics());
        EvolutionContext context = SinapseEvolutionContexts.of(request, items,
                AvailabilityWindows.of(request.availability()), beans.composition().evaluator(),
                beans.retention(), importance,
                SinapseEvolutionContexts.softPrerequisites(soft, request.topics()));

        Map<PlanningItem, UUID> topicIdsByItem = TopicPlanningItems.topicIdsByItem(request.topics());
        Searched searched = search(request, context, graph, topicIdsByItem);
        if (searched.fittest().getSchedule().isEmpty()) {
            throw new PlanRejectedException("plan-would-be-empty",
                    "No session fits: the availability windows inside the horizon cannot hold even "
                            + "the first topic of the study order. An empty plan is refused rather "
                            + "than returned, because the platform rejects one anyway.",
                    List.of(request.topics().get(0).id().toString()));
        }
        return respond(request, context, graph, soft, new Reported(filter, policy, importance),
                topicIdsByItem, searched);
    }

    private record Searched(TacticalStudyPlan fittest, SearchVitality vitality,
            VariantTimelineRepairer repairer, Map<PlanningItem, List<TacticalStudyBlock>> blocks) {
    }

    private record Reported(EdgeProvenanceFilter filter, PrecedencePolicy policy,
            ImportanceStrategy importance) {
    }

    /** Cópia de {@code TimelinePlanEngine.search}, com o reparador da variante. */
    private Searched search(PlanRequest request, EvolutionContext context,
            HardPrerequisiteGraph graph, Map<PlanningItem, UUID> topicIdsByItem) {

        Map<PlanningItem, Integer> sessions =
                TimelineChromosomes.sessionsPerItem(context, SessionBudget.of(request, context));

        Map<UUID, PlanRequest.Topic> topicsById = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        request.topics().forEach(topic -> topicsById.put(topic.id(), topic));
        Map<PlanningItem, List<TacticalStudyBlock>> blocksBy = new LinkedHashMap<>();
        for (PlanningItem item : context.importanceScores().keySet()) {
            blocksBy.put(item, TimelineChromosomes.blocksOf(item,
                    topicsById.get(topicIdsByItem.get(item)), sessions.getOrDefault(item, 1)));
        }
        VariantTimelineRepairer repairer =
                new VariantTimelineRepairer(request, graph, topicIdsByItem, blocksBy, variant);

        List<PlanningItem> canonical = List.copyOf(context.importanceScores().keySet());
        int size = beans.budget().populationSize();
        List<TacticalStudyPlan> seeds = new ArrayList<>(size);
        seeds.add(repairer.repair(TimelineChromosomes.inOrder(canonical, blocksBy), context));
        for (int index = 1; index < size; index++) {
            seeds.add(repairer.repair(
                    TimelineChromosomes.inOrder(TimelineChromosomes.shuffled(canonical), blocksBy),
                    context));
        }

        TimelineSearch.Outcome outcome = new TimelineSearch(beans.selection(),
                new DayBoundaryCrossover(), new BlockSwapMutation(), new MethodologyMutation(),
                beans.retentionRepairer(), delegatingTo(repairer))
                .run(seeds, context, beans.budget().generations());
        return new Searched(outcome.fittest(), outcome.vitality(), repairer, blocksBy);
    }

    /** O dublê que leva a variante para dentro de {@link TimelineSearch}. */
    private static TimelineRepairer delegatingTo(VariantTimelineRepairer repairer) {
        return Mockito.mock(TimelineRepairer.class, Mockito.withSettings().stubOnly()
                .defaultAnswer(invocation -> repairer.repair(invocation.getArgument(0),
                        invocation.getArgument(1))));
    }

    /** Cópia de {@code TimelinePlanEngine.respond}. */
    private VariantPlan respond(PlanRequest request, EvolutionContext context,
            HardPrerequisiteGraph graph, SoftPrerequisiteEdges soft, Reported reported,
            Map<PlanningItem, UUID> topicIdsByItem, Searched searched) {

        TacticalStudyPlan plan = searched.fittest();
        List<PlanRequest.Topic> order = fullOrder(request, topicIdsByItem, plan);

        PrerequisiteOrderRepairer.Result repair = null;
        if (reported.policy() == PrecedencePolicy.LEXICOGRAPHIC) {
            repair = PrerequisiteOrderRepairer.repair(order, graph, soft);
            order = repair.order();
            plan = searched.repairer().repair(
                    TimelineChromosomes.inOrder(itemsOf(order, topicIdsByItem), searched.blocks()),
                    context);
        }

        int scheduled = scheduledCount(plan);
        long minutes = plan.getSchedule().values().stream()
                .mapToLong(TacticalStudyBlock::durationMinutes)
                .sum();
        SessionPlacement.Result placed = new SessionPlacement.Result(plan, scheduled,
                request.topics().size(), minutes);

        PrerequisiteReport prerequisites = repair == null
                ? PrerequisiteReport.measured(reported.filter(), graph, soft, order, scheduled)
                : PrerequisiteReport.repaired(reported.filter(), graph, soft, repair, order,
                        scheduled);

        FitnessBreakdown breakdown = beans.composition().evaluator().explain(plan, context);
        PlanResponse response = new PlanResponse(PlanRequest.VERSION,
                TacticalSessions.of(plan, topicIdsByItem),
                SinapseFitness.of(beans.composition(), breakdown, placed, plan, context,
                        reported.importance(), prerequisites, searched.vitality()),
                new PlanResponse.ExecutionMetadata(beans.coreVersion(), request.randomSeed(),
                        beans.budget().generations(), 0L));

        PlanOutputInvariants.check(request, response, graph);
        return new VariantPlan(response, prerequisites.unscheduled());
    }

    private static List<PlanRequest.Topic> fullOrder(PlanRequest request,
            Map<PlanningItem, UUID> topicIdsByItem, TacticalStudyPlan plan) {

        List<PlanRequest.Topic> scheduled = studyOrder(request, topicIdsByItem, plan);
        List<PlanRequest.Topic> order = new ArrayList<>(scheduled);
        request.topics().stream().filter(topic -> !scheduled.contains(topic)).forEach(order::add);
        return order;
    }

    private static List<PlanningItem> itemsOf(List<PlanRequest.Topic> order,
            Map<PlanningItem, UUID> topicIdsByItem) {

        Map<UUID, PlanningItem> byId = new LinkedHashMap<>();
        topicIdsByItem.forEach((item, id) -> byId.put(id, item));
        return order.stream().map(topic -> byId.get(topic.id())).filter(item -> item != null).toList();
    }

    private static int scheduledCount(TacticalStudyPlan plan) {
        return (int) plan.getSchedule().values().stream()
                .map(TacticalStudyBlock::item)
                .distinct()
                .count();
    }

    private static List<PlanRequest.Topic> studyOrder(PlanRequest request,
            Map<PlanningItem, UUID> topicIdsByItem, TacticalStudyPlan plan) {

        Map<UUID, PlanRequest.Topic> topicsById = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        request.topics().forEach(topic -> topicsById.put(topic.id(), topic));

        Set<UUID> seen = new LinkedHashSet<>();
        TacticalSlots.ordered(plan).forEach(slot ->
                seen.add(topicIdsByItem.get(plan.getSchedule().get(slot).item())));

        List<PlanRequest.Topic> order = new ArrayList<>(seen.size());
        seen.forEach(id -> order.add(topicsById.get(id)));
        return order;
    }
}
