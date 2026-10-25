package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.Population;
import com.ia.project.dynamicstudyplanner.ga.config.GeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.generator.PopulationGenerator;
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
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Cópia de teste de {@link GeneticPlanEngine}, com a colocação trocável (EOA-12b).
 *
 * <p>A busca é a de produção, com os mesmos beans e a mesma semente; ela não chama a colocação
 * ({@code SessionPlacement.place} só roda depois de {@code evolve}), então a opção P muda só a
 * colocação final, e esta cópia é o motor inteiro com P. Em {@link PlacementVariant#BASE} a resposta é
 * a mesma do motor de produção, e a medição confere o hash em toda célula.
 */
public final class VariantGeneticEngine implements VariantEngine {

    /** Os colaboradores de produção, agrupados para o construtor caber no limite de parâmetros. */
    public record Beans(GeneticAlgorithmFactory algorithms, PopulationGenerator populations,
            FitnessComposition composition, RetentionAlgorithm retention,
            RequestConditions conditions, String coreVersion, GeneticSearchBudget budget) {
    }

    private final Beans beans;
    private final PlacementVariant variant;

    public VariantGeneticEngine(Beans beans, PlacementVariant variant) {
        this.beans = beans;
        this.variant = variant;
    }

    @Override
    public String id() {
        return GeneticPlanEngine.ID;
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
        PrecedencePolicy policy = conditions.precedence(request, PrecedencePolicy.LEXICOGRAPHIC);
        HardPrerequisiteGraph graph = HardPrerequisiteGraph.of(
                request.topics(), request.prerequisites(), filter);
        SoftPrerequisiteEdges soft = SoftPrerequisiteEdges.of(
                request.topics(), request.prerequisites(), filter);

        ImportanceStrategy importance = conditions.importance(request);
        EvolutionContext context = contextFor(request, importance, soft);
        Evolved evolved = evolve(context, SessionBudget.of(request, context));
        StudyPlan chromosome = evolved.chromosome();

        Map<PlanningItem, UUID> topicsByItem = TopicPlanningItems.topicIdsByItem(request.topics());
        List<PlanRequest.Topic> ordered =
                SinapseStudyOrder.of(request.topics(), graph, chromosome, topicsByItem);
        PrerequisiteOrderRepairer.Result repair = policy == PrecedencePolicy.LEXICOGRAPHIC
                ? PrerequisiteOrderRepairer.repair(ordered, graph, soft)
                : null;
        List<PlanRequest.Topic> order = repair == null ? ordered : repair.order();

        SessionPlacement.Result placed = VariantSessionPlacement.place(request, order, chromosome,
                itemsByTopic(request), graph, variant);
        if (placed.plan().getSchedule().isEmpty()) {
            throw new PlanRejectedException("plan-would-be-empty",
                    "No session fits: the availability windows inside the horizon cannot hold even "
                            + "the first topic of the study order. An empty plan is refused rather "
                            + "than returned, because the platform rejects one anyway.",
                    List.of(order.get(0).id().toString()));
        }

        FitnessBreakdown breakdown = beans.composition().evaluator().explain(placed.plan(), context);
        PrerequisiteReport prerequisites = repair == null
                ? PrerequisiteReport.measured(filter, graph, soft, order, placed.topicsScheduled())
                : PrerequisiteReport.repaired(filter, graph, soft, repair, order,
                        placed.topicsScheduled());
        PlanResponse response = new PlanResponse(
                PlanRequest.VERSION,
                TacticalSessions.of(placed.plan(), topicsByItem),
                SinapseFitness.of(beans.composition(), breakdown, placed, placed.plan(), context,
                        importance, byAbsence(prerequisites, order, placed, topicsByItem),
                        evolved.vitality()),
                new PlanResponse.ExecutionMetadata(beans.coreVersion(), request.randomSeed(),
                        beans.budget().generations(), 0L));

        PlanOutputInvariants.check(request, response, graph);
        return new VariantPlan(response, prerequisites.unscheduled());
    }

    /**
     * A regra P9: sob P, os não agendados são os ausentes de fato, na ordem do motor.
     *
     * <p>Na base o plano é prefixo da ordem e as duas leituras coincidem, então a resposta da base
     * fica a de produção.
     */
    private PrerequisiteReport byAbsence(PrerequisiteReport report, List<PlanRequest.Topic> order,
            SessionPlacement.Result placed, Map<PlanningItem, UUID> topicsByItem) {

        if (variant == PlacementVariant.BASE) {
            return report;
        }
        Set<UUID> scheduled = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        placed.plan().getSchedule().values().forEach(block ->
                scheduled.add(topicsByItem.get(block.item())));
        List<String> absent = order.stream().map(PlanRequest.Topic::id)
                .filter(id -> !scheduled.contains(id))
                .map(UUID::toString)
                .toList();
        return new PrerequisiteReport(report.policy(), report.provenance(), report.hardEdges(),
                report.softEdges(), report.inversionsBefore(), report.inversionsAfter(), absent);
    }

    private EvolutionContext contextFor(PlanRequest request, ImportanceStrategy importance,
            SoftPrerequisiteEdges soft) {

        List<PlanningItem> items = TopicPlanningItems.of(request.topics());
        return SinapseEvolutionContexts.of(request, items,
                AvailabilityWindows.of(request.availability()), beans.composition().evaluator(),
                beans.retention(), importance,
                SinapseEvolutionContexts.softPrerequisites(soft, request.topics()));
    }

    private record Evolved(StudyPlan chromosome, SearchVitality vitality) {
    }

    /** Cópia de {@code GeneticPlanEngine.evolve}. */
    private Evolved evolve(EvolutionContext context, int sessions) {
        GeneticAlgorithm algorithm = beans.algorithms().create();
        Population initial = beans.populations().generate(sessions, beans.budget().populationSize(),
                context);
        Population population = initial;
        for (int generation = 0; generation < beans.budget().generations(); generation++) {
            population = algorithm.evolvePopulation(population, context);
        }
        return new Evolved(population.getFittest().getPlan(),
                SearchVitality.of(initial, population));
    }

    private static Map<PlanRequest.Topic, PlanningItem> itemsByTopic(PlanRequest request) {
        Map<PlanRequest.Topic, PlanningItem> byTopic = new LinkedHashMap<>();
        request.topics().forEach(topic -> byTopic.put(topic, TopicPlanningItems.toItem(topic)));
        return byTopic;
    }
}
