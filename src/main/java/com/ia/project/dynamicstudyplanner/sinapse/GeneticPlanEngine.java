package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.Population;
import com.ia.project.dynamicstudyplanner.ga.config.GeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.generator.PopulationGenerator;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategy;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteOrderRepairer;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteReport;
import com.ia.project.dynamicstudyplanner.plan.PrecedencePolicy;
import com.ia.project.dynamicstudyplanner.plan.SoftPrerequisiteEdges;
import com.ia.project.dynamicstudyplanner.plan.PlanEngine;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.plan.PlanRequestGuard;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * {@code POST /plans} answered by the genetic algorithm: the second condition of the experiment.
 *
 * <h2>The pass, in order</h2>
 *
 * <ol>
 *   <li>{@link PlanRequestGuard} refuses what no engine can plan, with {@code 422} — the same guard
 *       the baseline runs, because the protocol's preconditions are not under test;</li>
 *   <li>{@link EffortTierBands} refuses a tier outside the closed set, because <b>this</b> engine
 *       reads the band and the baseline does not;</li>
 *   <li>the seed installs a reproducible source on this thread;</li>
 *   <li>{@link SinapseEvolutionContexts} assembles what the algorithm is told, from platform data
 *       only;</li>
 *   <li>the algorithm evolves an allocation of sessions across topics;</li>
 *   <li>{@link SinapseStudyOrder} and {@link SessionPlacement} lay that allocation onto the
 *       calendar, prerequisites first, forward only;</li>
 *   <li>{@link TacticalSessions} numbers the result and {@link SinapseFitness} reports what ran;</li>
 *   <li>{@link PlanOutputInvariants} asserts the twelve properties before the answer leaves.</li>
 * </ol>
 *
 * <h2>Determinism</h2>
 *
 * The seed from the request is installed on the calling thread and <b>restored in a
 * {@code finally}</b>, the pattern this repository settled on in EOA-3: the endpoint runs on a pool,
 * and a seed left installed would make the next request on that thread reproduce this one's plan
 * intermittently. Every map and comparator on the path is insertion-ordered or sorted by identifier
 * text, never by hash, so the same request and seed produce the same plan on any thread —
 * {@code SinapseDeterminismTest} runs it on two.
 *
 * <h2>{@code elapsedMillis} is zero, like the baseline's</h2>
 *
 * Measuring it would make two runs of the same seeded request differ, and reproducibility is a
 * requirement of the experiment. {@code generations} is the real count. The platform reads both off
 * the wire and drops them.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class GeneticPlanEngine implements PlanEngine {

    /** The id of the second condition of the experiment. */
    public static final String ID = "ga";

    /** See the class comment: a measured duration would break byte-for-byte reproducibility. */
    private static final long ELAPSED_MILLIS = 0L;

    private final GeneticAlgorithmFactory algorithms;
    private final PopulationGenerator populations;
    private final FitnessComposition composition;
    private final RetentionAlgorithm retention;
    private final RequestConditions conditions;
    private final String coreVersion;
    private final GeneticSearchBudget budget;

    /**
     * @param composition this path's fitness, by bean name, so no other aggregate can be injected
     *                    here by accident
     * @param conditions  which cell of the factorial this request names: provenance, importance and
     *                    precedence policy. See {@code RequestConditions}
     * @param coreVersion the build, reported as {@code metadata.coreVersion}; no code default
     * @param budget      how much search to spend, from {@code plan.engine.ga.*}
     */
    public GeneticPlanEngine(
            GeneticAlgorithmFactory algorithms,
            PopulationGenerator populations,
            @Qualifier("sinapseFitnessComposition") FitnessComposition composition,
            RetentionAlgorithm retention,
            RequestConditions conditions,
            @Value("${baseline.core.version}") String coreVersion,
            GeneticSearchBudget budget) {

        this.algorithms = algorithms;
        this.populations = populations;
        this.composition = composition;
        this.retention = retention;
        this.conditions = conditions;
        this.coreVersion = coreVersion;
        this.budget = budget;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PlanResponse plan(PlanRequest request) {
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

    private PlanResponse run(PlanRequest request) {
        EdgeProvenanceFilter filter = conditions.provenance(request);
        // LEXICOGRAPHIC e o padrao DESTE motor, e nao um padrao global: e o comportamento que os
        // relatorios 09 e 10 mediram como grupo de controle. Ver PrecedencePolicies.
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
        // Topological first, then the preferences: the repair may only reorder within what the
        // hard constraints already allow, so it can never turn a valid order into an invalid one.
        //
        // Sob WEIGHTED o reparo NAO roda: a ordem fica como a alocacao a induziu, e a inversao
        // residual e precificada pelo termo da fitness. Essa celula isola o que o reparo contribui
        // na representacao macro, e e por construcao quase inerte — o termo devolve 0,0 num plano
        // macro, entao a BUSCA nao ve preco nenhum e so a ordem final difere.
        PrerequisiteOrderRepairer.Result repair = policy == PrecedencePolicy.LEXICOGRAPHIC
                ? PrerequisiteOrderRepairer.repair(ordered, graph, soft)
                : null;
        List<PlanRequest.Topic> order = repair == null ? ordered : repair.order();

        SessionPlacement.Result placed =
                SessionPlacement.place(request, order, chromosome, itemsByTopic(request));
        if (placed.plan().getSchedule().isEmpty()) {
            throw new PlanRejectedException("plan-would-be-empty",
                    "No session fits: the availability windows inside the horizon cannot hold even "
                            + "the first topic of the study order. An empty plan is refused rather "
                            + "than returned, because the platform rejects one anyway.",
                    List.of(order.get(0).id().toString()));
        }

        FitnessBreakdown breakdown = evaluator().explain(placed.plan(), context);
        PrerequisiteReport prerequisites = repair == null
                ? PrerequisiteReport.measured(filter, graph, soft, order, placed.topicsScheduled())
                : PrerequisiteReport.repaired(filter, graph, soft, repair, order,
                        placed.topicsScheduled());
        PlanResponse response = new PlanResponse(
                PlanRequest.VERSION,
                TacticalSessions.of(placed.plan(), topicsByItem),
                SinapseFitness.of(composition, breakdown, placed, placed.plan(), context,
                        importance, prerequisites, evolved.vitality()),
                new PlanResponse.ExecutionMetadata(coreVersion, request.randomSeed(),
                        budget.generations(), ELAPSED_MILLIS));

        PlanOutputInvariants.check(request, response, graph);
        return response;
    }

    /** The context, with this run's order preferences already reduced to its provenance condition. */
    private EvolutionContext contextFor(PlanRequest request, ImportanceStrategy importance,
            SoftPrerequisiteEdges soft) {

        List<PlanningItem> items = TopicPlanningItems.of(request.topics());
        List<AvailabilityWindow> windows = AvailabilityWindows.of(request.availability());
        return SinapseEvolutionContexts.of(request, items, windows, evaluator(), retention,
                importance, SinapseEvolutionContexts.softPrerequisites(soft, request.topics()));
    }

    /** This path's evaluator, from its own composition. */
    private FitnessEvaluator evaluator() {
        return composition.evaluator();
    }

    /**
     * A alocação mais apta, e os sinais de que a busca buscou.
     *
     * @param chromosome a alocação vencedora
     * @param vitality   ver {@link SearchVitality}
     */
    private record Evolved(StudyPlan chromosome, SearchVitality vitality) {
    }

    /** Runs the evolution and returns the fittest allocation. */
    private Evolved evolve(EvolutionContext context, int sessions) {
        GeneticAlgorithm algorithm = algorithms.create();
        Population initial = populations.generate(sessions, budget.populationSize(), context);
        Population population = initial;
        for (int generation = 0; generation < budget.generations(); generation++) {
            population = algorithm.evolvePopulation(population, context);
        }
        // A aptidao esta em cache, entao ler aqui nao reavalia nada nem consome sorteio: o plano
        // desta busca e identico ao de antes desta invariante existir.
        return new Evolved(population.getFittest().getPlan(),
                SearchVitality.of(initial, population));
    }

    /** Topic to planning item, for reading the chromosome during placement. */
    private static Map<PlanRequest.Topic, PlanningItem> itemsByTopic(PlanRequest request) {
        Map<PlanRequest.Topic, PlanningItem> byTopic = new LinkedHashMap<>();
        request.topics().forEach(topic -> byTopic.put(topic, TopicPlanningItems.toItem(topic)));
        return byTopic;
    }
}
