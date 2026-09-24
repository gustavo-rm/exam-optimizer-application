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
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategies;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategy;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
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
    private final ImportanceStrategies importanceStrategies;
    private final String coreVersion;
    private final int generations;
    private final int populationSize;

    /**
     * @param composition    this path's fitness, by bean name, so the concurso aggregate cannot be
     *                       injected here by accident
     * @param coreVersion    the build, reported as {@code metadata.coreVersion}; no code default
     * @param generations    how many generations to run; from {@code plan.engine.ga.generations}
     * @param populationSize the population size; from {@code plan.engine.ga.population-size}
     */
    public GeneticPlanEngine(
            GeneticAlgorithmFactory algorithms,
            PopulationGenerator populations,
            @Qualifier("sinapseFitnessComposition") FitnessComposition composition,
            RetentionAlgorithm retention,
            ImportanceStrategies importanceStrategies,
            @Value("${baseline.core.version}") String coreVersion,
            @Value("${plan.engine.ga.generations}") int generations,
            @Value("${plan.engine.ga.population-size}") int populationSize) {

        this.algorithms = algorithms;
        this.populations = populations;
        this.composition = composition;
        this.retention = retention;
        this.importanceStrategies = importanceStrategies;
        this.coreVersion = coreVersion;
        this.generations = generations;
        this.populationSize = populationSize;
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
        List<PlanningItem> items = TopicPlanningItems.of(request.topics());
        List<AvailabilityWindow> windows = AvailabilityWindows.of(request.availability());
        FitnessEvaluator evaluator = composition.evaluator();
        ImportanceStrategy importance = importanceStrategies.resolve(request);
        EvolutionContext context = SinapseEvolutionContexts.of(
                request, items, windows, evaluator, retention, importance);

        StudyPlan chromosome = evolve(context, sessionBudget(request, context));

        Map<PlanningItem, UUID> topicsByItem = TopicPlanningItems.topicIdsByItem(request.topics());
        HardPrerequisiteGraph graph =
                HardPrerequisiteGraph.of(request.topics(), request.prerequisites());
        List<PlanRequest.Topic> order =
                SinapseStudyOrder.of(request.topics(), graph, chromosome, topicsByItem);

        SessionPlacement.Result placed =
                SessionPlacement.place(request, order, chromosome, itemsByTopic(request));
        if (placed.plan().getSchedule().isEmpty()) {
            throw new PlanRejectedException("plan-would-be-empty",
                    "No session fits: the availability windows inside the horizon cannot hold even "
                            + "the first topic of the study order. An empty plan is refused rather "
                            + "than returned, because the platform rejects one anyway.",
                    List.of(order.get(0).id().toString()));
        }

        FitnessBreakdown breakdown = evaluator.explain(placed.plan(), context);
        PlanResponse response = new PlanResponse(
                PlanRequest.VERSION,
                TacticalSessions.of(placed.plan(), topicsByItem),
                SinapseFitness.of(composition, breakdown, placed, placed.plan(), context,
                        importance),
                new PlanResponse.ExecutionMetadata(coreVersion, request.randomSeed(),
                        generations, ELAPSED_MILLIS));

        PlanOutputInvariants.check(request, response, graph);
        return response;
    }

    /** Runs the evolution and returns the fittest allocation. */
    private StudyPlan evolve(EvolutionContext context, int budget) {
        GeneticAlgorithm algorithm = algorithms.create();
        Population population = populations.generate(budget, populationSize, context);
        for (int generation = 0; generation < generations; generation++) {
            population = algorithm.evolvePopulation(population, context);
        }
        return population.getFittest().getPlan();
    }

    /**
     * How many sessions the chromosome has to distribute.
     *
     * <h2>Why the budget is derived and not configured</h2>
     *
     * The chromosome allocates a fixed total, so something has to say what the total is. It is the
     * number of average-length sessions the availability can hold: total usable minutes divided by
     * the mean {@code estimatedMinutes}. Any larger and the search would optimise a plan the
     * calendar cannot hold, and placement would truncate most of it — the algorithm would be
     * scoring a fiction. Any smaller and it would leave the student's declared time unused.
     *
     * <p>Floored at the <b>sum of the per-topic floors</b>, which {@link SinapseMinimumDays} derives
     * from {@code estimatedMinutes} over the measured study day. A budget below that sum makes the
     * initial population unsatisfiable, and {@code StudyPlanFactory} would refuse it with a
     * {@code 422} naming the wrong cause — the request would look infeasible when it is the budget
     * that is too small.
     */
    private static int sessionBudget(PlanRequest request, EvolutionContext context) {
        long minutes = com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator.over(request)
                .availableMinutes();
        double meanLength = request.topics().stream()
                .mapToInt(PlanRequest.Topic::estimatedMinutes)
                .average()
                .orElse(1.0);
        int affordable = (int) Math.floor(minutes / Math.max(1.0, meanLength));
        return Math.max(SinapseMinimumDays.totalFloor(context.minimumDaysPerItem()), affordable);
    }

    /** Topic to planning item, for reading the chromosome during placement. */
    private static Map<PlanRequest.Topic, PlanningItem> itemsByTopic(PlanRequest request) {
        Map<PlanRequest.Topic, PlanningItem> byTopic = new LinkedHashMap<>();
        request.topics().forEach(topic -> byTopic.put(topic, TopicPlanningItems.toItem(topic)));
        return byTopic;
    }
}
