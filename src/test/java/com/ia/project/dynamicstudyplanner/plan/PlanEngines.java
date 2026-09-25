package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine;
import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineScheduler;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.ga.config.DefaultGeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.generator.DefaultPopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.TournamentSelection;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.SoftPrerequisiteOrderConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import com.ia.project.dynamicstudyplanner.sinapse.DailyLoadBudgetObjective;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.SinapseFitnessConfig;
import com.ia.project.dynamicstudyplanner.sinapse.importance.GoalPriorityImportance;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategies;
import com.ia.project.dynamicstudyplanner.sinapse.importance.PrerequisiteCentralityImportance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Os dois motores, montados à mão, para os testes que valem para ambos.
 *
 * <h2>Sem contexto Spring, de propósito</h2>
 *
 * Todo colaborador é construído aqui. É mais verboso do que {@code @SpringBootTest} e é o que faz o
 * teste falhar pelo motivo certo: um teste que sobe o contexto também testa o contexto, e quando
 * reprova é preciso descobrir se o problema é o algoritmo ou a fiação. A fiação tem testes próprios
 * ({@code BaselinePlanEndpointTest}, {@code SinapsePlanEndpointTest}).
 *
 * <h2>Passa pelo seletor, e não pelo motor</h2>
 *
 * {@link Case#plan} chama {@link PlanEngineSelector}, escolhendo o motor por
 * {@code algorithmParams.engine}. Assim o caminho exercitado é o que a plataforma usa — incluindo a
 * estampagem de {@code fitness.engine}, que é obrigação do seletor e não do motor.
 *
 * <h2>Orçamento de busca reduzido</h2>
 *
 * {@value #TEST_GENERATIONS} gerações sobre população {@value #TEST_POPULATION}, contra 60 e 40 em
 * produção. As invariantes de saída não dependem de quanto a busca convergiu — são propriedades da
 * conversão e da alocação —, e o orçamento cheio multiplicaria o tempo da suíte sem mudar o que
 * qualquer asserção daqui observa.
 */
public final class PlanEngines {

    /** Gerações nos testes. Ver a nota de classe: a invariante não depende da convergência. */
    public static final int TEST_GENERATIONS = 20;

    /** População nos testes. */
    public static final int TEST_POPULATION = 20;

    /** A versão reportada como {@code metadata.coreVersion}; qualquer string não vazia serve aqui. */
    private static final String CORE_VERSION = "2.0.1";

    /**
     * The widest provenance condition, which is what every test that is not about the ablation
     * wants: it sees exactly the edges the request carries, so a fixture's edges all apply and a
     * test never has to know this key exists to reason about its own graph.
     */
    public static final PrerequisiteProvenance ALL_PROVENANCE =
            new PrerequisiteProvenance(EdgeProvenanceFilter.ALL.id());

    private PlanEngines() {
    }

    /** Um motor, com o rótulo que aparece no nome do caso parametrizado. */
    public record Case(String id, PlanEngineSelector selector) {

        /** Planeja pelo seletor, pedindo este motor explicitamente. */
        public PlanResponse plan(PlanRequest request) {
            return selector.plan(withEngine(request, id));
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** @return um caso por motor registrado */
    public static List<Case> all() {
        PlanEngineSelector selector = selector();
        return List.of(new Case(GreedyBaselineEngine.ID, selector),
                new Case(GeneticPlanEngine.ID, selector));
    }

    /** Um seletor com os dois motores e o baseline como padrão, como em produção. */
    public static PlanEngineSelector selector() {
        return new PlanEngineSelector(
                List.of(greedy(), genetic()), GreedyBaselineEngine.ID);
    }

    /** O motor guloso (EOA-2). */
    public static GreedyBaselineEngine greedy() {
        return new GreedyBaselineEngine(new GreedyBaselineScheduler(CORE_VERSION, ALL_PROVENANCE));
    }

    /** O motor genético (EOA-5), com a composição de fitness do caminho SINAPSE. */
    public static GeneticPlanEngine genetic() {
        return genetic(TEST_GENERATIONS, TEST_POPULATION, true);
    }

    /** O motor genético com o teto de carga diária ligado ou desligado. */
    public static GeneticPlanEngine genetic(boolean dailyLoadBudget) {
        return genetic(TEST_GENERATIONS, TEST_POPULATION, dailyLoadBudget);
    }

    /** O motor genético com um orçamento de busca escolhido. */
    public static GeneticPlanEngine genetic(int generations, int populationSize,
            boolean dailyLoadBudget) {
        SinapseFitnessConfig compositions = new SinapseFitnessConfig();
        return new GeneticPlanEngine(
                new DefaultGeneticAlgorithmFactory(
                        new TournamentSelection(),
                        new HybridCrossover(new WeightedAverageCrossover(),
                                new RepairingCrossover()),
                        new CreepMutation()),
                new DefaultPopulationGenerator(),
                compositions.sinapseFitnessComposition(
                        new ScoreGainObjective(), new RetentionObjective(),
                        new DailyLoadBudgetObjective(), new MinimumDaysConstraint(),
                        new MandatoryReviewConstraint(new HybridRetentionEngine()),
                        new SoftPrerequisiteOrderConstraint(),
                        dailyLoadBudget),
                new HybridRetentionEngine(),
                importanceStrategies(),
                ALL_PROVENANCE,
                CORE_VERSION,
                new GeneticSearchBudget(generations, populationSize));
    }

    /**
     * As duas estratégias de importância, com {@code goal-priority} como padrão — como em produção.
     *
     * <p>As duas registradas mesmo nos testes que não trocam de estratégia: o seletor recusa um id
     * desconhecido, e um registro com uma só estratégia não exercitaria essa recusa.
     */
    public static ImportanceStrategies importanceStrategies() {
        return new ImportanceStrategies(
                List.of(new GoalPriorityImportance(), new PrerequisiteCentralityImportance(ALL_PROVENANCE)),
                GoalPriorityImportance.ID);
    }

    /** The same request, asking for one engine by name. */
    public static PlanRequest withEngine(PlanRequest request, String engineId) {
        Map<String, Object> params = new HashMap<>(request.algorithmParams());
        params.put(PlanEngineSelector.ENGINE_PARAM, engineId);
        return new PlanRequest(request.contractVersion(), request.horizon(), request.availability(),
                request.goals(), request.topics(), request.prerequisites(), request.history(),
                params, request.randomSeed());
    }
}
