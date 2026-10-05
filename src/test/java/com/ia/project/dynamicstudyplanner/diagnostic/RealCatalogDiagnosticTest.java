package com.ia.project.dynamicstudyplanner.diagnostic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.config.GeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.generator.PopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;
import com.ia.project.dynamicstudyplanner.ga.tactical.repair.SpacedRepetitionRepairer;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.RequestConditions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnóstico de escala sobre o catálogo real (35 tópicos). <b>Mede e relata; não afirma nada.</b>
 *
 * <h2>Desligado no {@code verify}</h2>
 *
 * Só roda com {@code -Ddiagnostic=true}, por {@link EnabledIfSystemProperty}: é um instrumento, não
 * um portão, e nenhuma das suas leituras vira critério de CI. Exemplo:
 * <pre>
 * ./mvnw test -Dtest=RealCatalogDiagnosticTest -Ddiagnostic=true -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * Os CSVs vão para {@code target/diagnostic}, ou para {@code -Ddiagnostic.out=...}.
 *
 * <h2>Sementes declaradas antes de olhar os dados</h2>
 *
 * {@code ga} e {@code ga-timeline}: sementes 1 a 10. Guloso: uma execução, com a semente do arquivo,
 * porque ele não sorteia nada.
 *
 * <h2>Uma regra que vale para tudo aqui</h2>
 *
 * Nenhum fitness de um motor é comparado com o de outro. O avaliador a posteriori ({@link PlanScoring})
 * pontua todo plano com a mesma composição e é publicado como objetivo do genético; a comparação
 * dentro do motor ({@link WithinEngineComparison}) põe três soluções sob a função de UM motor.
 */
@SpringBootTest
@ActiveProfiles(PlanProtocol.PROFILE)
@EnabledIfSystemProperty(named = "diagnostic", matches = "true")
@DisplayName("Diagnóstico de escala do catálogo real")
class RealCatalogDiagnosticTest {

    static final List<Long> SEEDS = LongStream.rangeClosed(1, 10).boxed().toList();
    static final String GREEDY = "greedy-baseline";
    static final String GA = "ga";
    static final String TIMELINE = "ga-timeline";
    static final List<Integer> SCALE_POINTS = List.of(10, 15, 20, 25, 30, 35);

    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private PlanEngineSelector selector;
    @Autowired
    @Qualifier("sinapseFitnessComposition")
    private FitnessComposition composition;
    @Autowired
    private RetentionAlgorithm retention;
    @Autowired
    private RequestConditions conditions;
    @Autowired
    private GeneticSearchBudget budget;
    @Autowired
    private GeneticAlgorithmFactory algorithms;
    @Autowired
    private PopulationGenerator populations;
    @Autowired
    private SelectionStrategy selection;
    @Autowired
    private SpacedRepetitionRepairer retentionRepairer;

    private EngineProbe probe;
    private SearchReplay replay;

    @BeforeEach
    void monta() {
        probe = new EngineProbe(selector, new PlanScoring(composition, retention), conditions,
                budget);
        replay = new SearchReplay(algorithms, populations, selection, retentionRepairer, probe);
    }

    private PlanRequest instance(String profile) {
        return DiagnosticInstances.load(mapper, profile);
    }

    @Test
    @DisplayName("passo 3: guloso 1x, ga e ga-timeline com as sementes 1 a 10, por instância")
    void medicaoPorInstancia() {
        List<String> rows = new ArrayList<>();
        for (String profile : DiagnosticInstances.PROFILES) {
            PlanRequest request = instance(profile);
            rows.add(probe.run(profile, DiagnosticInstances.forEngine(request, GREEDY,
                    request.randomSeed())).csv());
            for (String engine : List.of(GA, TIMELINE)) {
                for (long seed : SEEDS) {
                    rows.add(probe.run(profile,
                            DiagnosticInstances.forEngine(request, engine, seed)).csv());
                }
            }
        }
        Csv.write("medicao.csv", EngineProbe.Outcome.header(), rows);
        Csv.write("parametros.csv", "property,value", List.of(
                "plan.engine.ga.generations," + budget.generations(),
                "plan.engine.ga.population-size," + budget.populationSize()));
        assertThat(rows).hasSize(DiagnosticInstances.PROFILES.size() * (1 + 2 * SEEDS.size()));
    }

    @Test
    @DisplayName("passo 3b: p_e, p_g e p_0 sob a função de cada motor genético")
    void comparacaoDentroDoMotor() {
        WithinEngineComparison comparison = new WithinEngineComparison(replay, probe);
        List<String> rows = new ArrayList<>();
        for (String profile : DiagnosticInstances.PROFILES) {
            PlanRequest request = instance(profile);
            EngineProbe.Outcome greedy = probe.run(profile,
                    DiagnosticInstances.forEngine(request, GREEDY, request.randomSeed()));
            for (long seed : SEEDS) {
                rows.addAll(scoreGa(comparison, profile, request, seed, greedy));
                rows.addAll(scoreTimeline(comparison, profile, request, seed, greedy));
            }
        }
        Csv.write("comparacao-3b.csv", WithinEngineComparison.header(), rows);
        assertThat(rows).isNotEmpty();
    }

    private List<String> scoreGa(WithinEngineComparison comparison, String profile,
            PlanRequest request, long seed, EngineProbe.Outcome greedy) {
        PlanRequest seeded = DiagnosticInstances.forEngine(request, GA, seed);
        List<String> rows = new ArrayList<>();
        comparison.ga(seeded, probe.run(profile, seeded), greedy).forEach(scored ->
                rows.addAll(WithinEngineComparison.rows(profile, GA, seed, scored)));
        return rows;
    }

    private List<String> scoreTimeline(WithinEngineComparison comparison, String profile,
            PlanRequest request, long seed, EngineProbe.Outcome greedy) {
        PlanRequest seeded = DiagnosticInstances.forEngine(request, TIMELINE, seed);
        List<String> rows = new ArrayList<>();
        comparison.timeline(seeded, probe.run(profile, seeded), greedy).forEach(scored ->
                rows.addAll(WithinEngineComparison.rows(profile, TIMELINE, seed, scored)));
        return rows;
    }

    @Test
    @DisplayName("passo 3b, complemento: a geração zero do ga-timeline em cada instância")
    void populacaoInicialDaLinhaDoTempo() {
        List<String> rows = new ArrayList<>();
        for (String profile : DiagnosticInstances.PROFILES) {
            PlanRequest request = instance(profile);
            for (long seed : SEEDS) {
                PlanRequest seeded = DiagnosticInstances.forEngine(request, TIMELINE, seed);
                SearchReplay.Replayed zero = replay.timeline(seeded, 0);
                rows.add(String.join(",", profile, Long.toString(seed),
                        EmptyPlanReduction.initialPopulation(zero.initial(),
                                replay.context(seeded))));
            }
        }
        Csv.write("populacao-inicial-timeline.csv",
                "instance,seed," + EmptyPlanReduction.initialHeader(), rows);
        assertThat(rows).hasSize(DiagnosticInstances.PROFILES.size() * SEEDS.size());
    }

    @Test
    @DisplayName("passo 4: curva de escala no perfil médio, rho constante")
    void curvaDeEscala() {
        PlanRequest medio = instance("medio");
        List<String> rows = new ArrayList<>();
        for (int k : SCALE_POINTS) {
            PlanRequest reduced = DiagnosticInstances.firstTopicsAtSameRho(medio, k);
            String prefix = String.format(Locale.ROOT, "%d,%.6f,", k,
                    DiagnosticInstances.capacity(reduced) / (double) DiagnosticInstances.demand(reduced));
            String label = "medio-k" + k;
            rows.add(prefix + probe.run(label, DiagnosticInstances.forEngine(reduced, GREEDY,
                    reduced.randomSeed())).csv());
            for (long seed : SEEDS) {
                rows.add(prefix + probe.run(label,
                        DiagnosticInstances.forEngine(reduced, GA, seed)).csv());
            }
        }
        Csv.write("escala.csv", "k,rho," + EngineProbe.Outcome.header(), rows);
        assertThat(rows).hasSize(SCALE_POINTS.size() * (1 + SEEDS.size()));
    }

    @Test
    @DisplayName("passo 6: plan-would-be-empty do ga-timeline, reprodutor mínimo e trajetória")
    void planoVazioDaLinhaDoTempo() throws IOException {
        String chosen = null;
        int reproductions = 0;
        for (String profile : DiagnosticInstances.PROFILES) {
            int count = new EmptyPlanReduction(probe, SEEDS, 1, false)
                    .reproductions(instance(profile));
            if (count > reproductions) {
                chosen = profile;
                reproductions = count;
            }
        }
        assertThat(chosen).as("nenhuma instância reproduz plan-would-be-empty").isNotNull();
        int literal = reduceAndTrace("ga-timeline-empty-minimal", chosen, reproductions, false);
        int feasible = reduceAndTrace("ga-timeline-empty-minimal-feasible", chosen, reproductions,
                true);
        assertThat(List.of(literal, feasible)).allMatch(count -> count >= 10);
    }

    /**
     * Reduz, confirma com as dez sementes, grava o pedido mínimo e a trajetória do melhor indivíduo.
     *
     * @return em quantas sementes o pedido mínimo reproduz
     */
    private int reduceAndTrace(String name, String chosen, int reproductions, boolean feasible)
            throws IOException {
        EmptyPlanReduction reduction = new EmptyPlanReduction(probe, SEEDS, reproductions, feasible);
        PlanRequest minimal = reduction.reduce(instance(chosen));
        int confirmed = reduction.reproductions(minimal);
        List<String> log = new ArrayList<>(reduction.log());
        log.add(0, "partida: " + chosen + ", reproduz em " + reproductions + "/10, guloso viavel "
                + "exigido: " + feasible);
        log.add("minimo: " + minimal.topics().size() + " topicos, " + minimal.availability().size()
                + " janelas, " + minimal.goals().size() + " metas; confirmado em " + confirmed
                + "/10; guloso aceita: " + reduction.greedyAccepts(minimal));
        Csv.write(name + "-reducao.txt", "# reducao do plan-would-be-empty", log);

        PlanRequest stored = DiagnosticInstances.forEngine(minimal, TIMELINE, SEEDS.get(0));
        Files.writeString(Csv.outputDirectory().resolve(name + ".json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(stored) + "\n");

        List<String> trace = new ArrayList<>();
        for (long seed : SEEDS) {
            trace.addAll(EmptyPlanReduction.trace(replay,
                    DiagnosticInstances.forEngine(minimal, TIMELINE, seed), budget.generations()));
        }
        Csv.write(name + "-trajetoria.csv", EmptyPlanReduction.traceHeader(), trace);
        return confirmed;
    }
}
