package com.ia.project.dynamicstudyplanner.diagnostic;

import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.Population;
import com.ia.project.dynamicstudyplanner.ga.config.GeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.generator.PopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;
import com.ia.project.dynamicstudyplanner.ga.tactical.repair.SpacedRepetitionRepairer;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.crossover.DayBoundaryCrossover;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation.BlockSwapMutation;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation.MethodologyMutation;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.RequestConditions;
import com.ia.project.dynamicstudyplanner.sinapse.SearchVitality;
import com.ia.project.dynamicstudyplanner.sinapse.SessionBudget;
import com.ia.project.dynamicstudyplanner.sinapse.TopicPlanningItems;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineChromosomes;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineRepairer;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineSearch;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Refaz, em código de teste, a busca de cada motor genético, para ler o que o motor não devolve.
 *
 * <h2>Por que refazer, e como se sabe que é a mesma busca</h2>
 *
 * {@code GeneticPlanEngine} não devolve o cromossomo macro que venceu, e {@code TimelinePlanEngine}
 * descarta o melhor indivíduo quando ele é vazio — é aí que nasce {@code plan-would-be-empty}. Os
 * dois são lidos aqui chamando <b>as mesmas classes públicas de produção, na mesma ordem</b>, sob a
 * mesma semente: contexto, orçamento de sessões, população inicial, laço de gerações. Nada de
 * {@code src/main} é copiado nem alterado.
 *
 * <p>A identidade não é suposta: o teste compara {@code finalBest} daqui com
 * {@code search-final-best} que o motor publicou, bit a bit, sempre que o motor aceitou o pedido.
 * Qualquer sorteio a mais ou a menos mudaria esse número.
 */
final class SearchReplay {

    private final GeneticAlgorithmFactory algorithms;
    private final PopulationGenerator populations;
    private final SelectionStrategy selection;
    private final SpacedRepetitionRepairer retentionRepairer;
    private final GeneticSearchBudget budget;
    private final RequestConditions conditions;
    private final PlanScoring scoring;

    SearchReplay(GeneticAlgorithmFactory algorithms, PopulationGenerator populations,
            SelectionStrategy selection, SpacedRepetitionRepairer retentionRepairer,
            EngineProbe probe) {
        this.algorithms = algorithms;
        this.populations = populations;
        this.selection = selection;
        this.retentionRepairer = retentionRepairer;
        this.budget = probe.budget();
        this.conditions = probe.conditions();
        this.scoring = probe.scoring();
    }

    /** O contexto do pedido, montado pelo montador de produção. */
    EvolutionContext context(PlanRequest request) {
        return scoring.contextFor(request, conditions.provenance(request),
                conditions.importance(request));
    }

    /**
     * O resultado de uma busca refeita.
     *
     * @param fittest  o melhor indivíduo da última geração
     * @param vitality os sinais publicados pelo motor, recalculados aqui para conferência
     * @param initial  os indivíduos da geração zero, na ordem em que foram gerados
     */
    record Replayed(StudyPlan fittest, SearchVitality vitality, List<StudyPlan> initial) {
    }

    /** A busca do motor {@code ga}: evolui a alocação macro. */
    Replayed ga(PlanRequest request) {
        return seeded(request.randomSeed(), () -> {
            EvolutionContext context = context(request);
            GeneticAlgorithm algorithm = algorithms.create();
            Population initial = populations.generate(SessionBudget.of(request, context),
                    budget.populationSize(), context);
            Population population = initial;
            for (int generation = 0; generation < budget.generations(); generation++) {
                population = algorithm.evolvePopulation(population, context);
            }
            return new Replayed(population.getFittest().getPlan(),
                    SearchVitality.of(initial, population), plans(initial));
        });
    }

    /** A busca do motor {@code ga-timeline}, com o orçamento de gerações configurado. */
    Replayed timeline(PlanRequest request) {
        return timeline(request, budget.generations());
    }

    /**
     * A busca do motor {@code ga-timeline}, parada em {@code generations}.
     *
     * <p>Com a mesma semente, parar na geração {@code g} dá exatamente a população {@code g} da
     * execução completa: a sequência de sorteios é a mesma até ali. É assim que a trajetória do
     * melhor indivíduo é lida sem instrumentar o laço de produção.
     */
    Replayed timeline(PlanRequest request, int generations) {
        return seeded(request.randomSeed(), () -> {
            EvolutionContext context = context(request);
            HardPrerequisiteGraph graph = HardPrerequisiteGraph.of(request.topics(),
                    request.prerequisites(), conditions.provenance(request));
            Map<PlanningItem, UUID> topicIdsByItem =
                    TopicPlanningItems.topicIdsByItem(request.topics());
            Map<PlanningItem, List<TacticalStudyBlock>> blocksBy =
                    blocks(request, context, topicIdsByItem);
            TimelineRepairer repairer =
                    new TimelineRepairer(request, graph, topicIdsByItem, blocksBy);
            List<TacticalStudyPlan> seeds = timelineSeeds(context, blocksBy, repairer);
            TimelineSearch.Outcome outcome = new TimelineSearch(selection,
                    new DayBoundaryCrossover(), new BlockSwapMutation(), new MethodologyMutation(),
                    retentionRepairer, repairer).run(seeds, context, generations);
            return new Replayed(outcome.fittest(), outcome.vitality(), List.copyOf(seeds));
        });
    }

    /** Os blocos canônicos de cada item, como {@code TimelinePlanEngine.search} os monta. */
    private static Map<PlanningItem, List<TacticalStudyBlock>> blocks(PlanRequest request,
            EvolutionContext context, Map<PlanningItem, UUID> topicIdsByItem) {

        Map<PlanningItem, Integer> sessions = TimelineChromosomes.sessionsPerItem(context,
                SessionBudget.of(request, context));
        Map<UUID, PlanRequest.Topic> topicsById = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        request.topics().forEach(topic -> topicsById.put(topic.id(), topic));
        Map<PlanningItem, List<TacticalStudyBlock>> blocksBy = new LinkedHashMap<>();
        for (PlanningItem item : context.importanceScores().keySet()) {
            blocksBy.put(item, TimelineChromosomes.blocksOf(item,
                    topicsById.get(topicIdsByItem.get(item)), sessions.getOrDefault(item, 1)));
        }
        return blocksBy;
    }

    /** A geração zero: a ordem canônica primeiro, depois permutações, todas reparadas. */
    private List<TacticalStudyPlan> timelineSeeds(EvolutionContext context,
            Map<PlanningItem, List<TacticalStudyBlock>> blocksBy, TimelineRepairer repairer) {

        List<PlanningItem> canonical = List.copyOf(context.importanceScores().keySet());
        List<TacticalStudyPlan> seeds = new ArrayList<>(budget.populationSize());
        seeds.add(repairer.repair(TimelineChromosomes.inOrder(canonical, blocksBy), context));
        for (int index = 1; index < budget.populationSize(); index++) {
            seeds.add(repairer.repair(
                    TimelineChromosomes.inOrder(TimelineChromosomes.shuffled(canonical), blocksBy),
                    context));
        }
        return seeds;
    }

    private static List<StudyPlan> plans(Population population) {
        List<StudyPlan> plans = new ArrayList<>(population.getSize());
        for (int index = 0; index < population.getSize(); index++) {
            plans.add(population.getIndividual(index).getPlan());
        }
        return plans;
    }

    /** Instala a semente como os motores fazem, e restaura a fonte anterior no fim. */
    private static Replayed seeded(long seed, Supplier<Replayed> search) {
        Random previous = RandomProvider.getInstance();
        RandomProvider.setInstance(new Random(seed));
        try {
            return search.get();
        } finally {
            RandomProvider.setInstance(previous);
        }
    }
}
