package com.ia.project.dynamicstudyplanner.diagnostic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.baseline.VariantGreedyScheduler;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.config.GeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.generator.PopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;
import com.ia.project.dynamicstudyplanner.ga.tactical.repair.SpacedRepetitionRepairer;
import com.ia.project.dynamicstudyplanner.plan.BuildIdentity;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlacementVariant;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteProvenance;
import com.ia.project.dynamicstudyplanner.plan.VariantEngine;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.RequestConditions;
import com.ia.project.dynamicstudyplanner.sinapse.VariantGeneticEngine;
import com.ia.project.dynamicstudyplanner.sinapse.VariantTimelineEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A medição do EOA-12b: base contra a opção P, e a classificação D dos ausentes. <b>Mede e relata;
 * não é portão, e nada é adotado</b> (decisão D2b, ADR-0012).
 *
 * <h2>Desligado no {@code verify}</h2>
 *
 * Como {@link RealCatalogRepairTest}, só roda com {@code -Ddiagnostic=true}:
 * <pre>
 * ./mvnw test -Dtest=AllocatorOptionsMeasurementTest -Ddiagnostic=true -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * Os casos pequenos de P ({@code VariantGreedySchedulerTest} e os outros dois) rodam sempre.
 *
 * <h2>O que cada célula faz</h2>
 *
 * <ol>
 *   <li>a <b>base</b> pelo seletor de produção ({@link EngineProbe}), como no E0;</li>
 *   <li>a cópia de teste do motor em {@code BASE}, cujo hash canônico tem de ser <b>igual</b> ao da
 *       base: se não for, a cópia não é fiel e a medição aborta;</li>
 *   <li>a mesma cópia em {@code SKIP_AHEAD} (opção P), com as mesmas sementes e o mesmo avaliador;</li>
 *   <li>{@code PlanOutputInvariants} de novo em cada resposta P. Qualquer violação faz o teste falhar
 *       no fim, com o caso que a reproduz.</li>
 * </ol>
 *
 * Os CSVs saem em {@code target/diagnostic/alocador-*.csv}; as cópias publicadas ficam em
 * {@code docs/alocador-opcoes/}. Tudo é determinístico exceto {@code wall_ms}.
 */
@SpringBootTest
@ActiveProfiles(PlanProtocol.PROFILE)
@EnabledIfSystemProperty(named = "diagnostic", matches = "true")
@DisplayName("EOA-12b: base contra a opção P, e por que cada tópico ficou de fora")
class AllocatorOptionsMeasurementTest {

    private static final String FEASIBLE = "/instances/reproducers/ga-timeline-empty-minimal-feasible.json";

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
    @Autowired
    private PrerequisiteProvenance provenance;
    @Autowired
    private BuildIdentity build;
    @Value("${baseline.core.version}")
    private String coreVersion;

    @Test
    @DisplayName("tres instancias + reprodutor viavel x tres motores x sementes 1 a 10, base e P")
    void baseContraP() {
        PlanScoring scoring = new PlanScoring(composition, retention);
        EngineProbe probe = new EngineProbe(selector, scoring, conditions, budget);
        VariantProbe variants = new VariantProbe(scoring, conditions, mapper, build.id(), budget.generations());
        Report report = new Report();

        for (Map.Entry<String, PlanRequest> instance : instances().entrySet()) {
            AbsenceClassifier classifier = classifier(instance.getValue());
            report.instance(instance.getKey(), classifier);
            for (String engine : RealCatalogRepairTest.ENGINES) {
                Map<PlacementVariant, VariantEngine> copies = copies(engine);
                for (long seed : RealCatalogDiagnosticTest.SEEDS) {
                    PlanRequest request = DiagnosticInstances.forEngine(instance.getValue(), engine, seed);
                    measure(report, classifier, probe, variants, copies, instance.getKey(), request);
                }
                report.closeCell(instance.getKey(), engine);
            }
        }
        report.write();

        assertThat(report.unfaithful).as("cópias em BASE que divergiram da produção").isEmpty();
        assertThat(report.violations).as("respostas P que violam PlanOutputInvariants").isEmpty();
    }

    private void measure(Report report, AbsenceClassifier classifier, EngineProbe probe,
            VariantProbe variants, Map<PlacementVariant, VariantEngine> copies, String instance,
            PlanRequest request) {

        EngineProbe.Outcome base = probe.run(instance, request);
        VariantProbe.Measured copy = variants.run(instance, copies.get(PlacementVariant.BASE), request);
        if (!sameAnswer(variants, base, copy.outcome())) {
            report.unfaithful.add(instance + "/" + base.engine() + "/" + request.randomSeed());
        }
        VariantProbe.Measured skipAhead = variants.run(instance, copies.get(PlacementVariant.SKIP_AHEAD),
                request);
        HardPrerequisiteGraph graph = graph(request);
        String recheck = VariantProbe.recheck(request, skipAhead.outcome().response(), graph);
        if (recheck.contains("fail") || "plan-invariant-violation".equals(skipAhead.outcome().state())) {
            report.violations.add(instance + "/" + base.engine() + "/" + request.randomSeed() + ": "
                    + recheck + skipAhead.outcome().violation());
        }
        Set<String> topicIds = new TreeSet<>();
        request.topics().forEach(topic -> topicIds.add(topic.id().toString()));
        report.add("base", base, VariantProbe.recheck(request, base.response(), graph), copy.prefixReading(),
                variants, classifier, topicIds);
        report.add("P", skipAhead.outcome(), recheck, skipAhead.prefixReading(), variants, classifier,
                topicIds);
    }

    /** A cópia em BASE responde o mesmo que a produção: mesmo hash, ou a mesma recusa. */
    private static boolean sameAnswer(VariantProbe variants, EngineProbe.Outcome production,
            EngineProbe.Outcome copy) {

        if (production.response() == null || copy.response() == null) {
            return production.response() == copy.response()
                    && production.state().equals(copy.state())
                    && Objects.equals(production.message(), copy.message());
        }
        return variants.responseHash(production.response()).equals(variants.responseHash(copy.response()));
    }

    private Map<PlacementVariant, VariantEngine> copies(String engine) {
        Map<PlacementVariant, VariantEngine> copies = new LinkedHashMap<>();
        for (PlacementVariant variant : PlacementVariant.values()) {
            copies.put(variant, switch (engine) {
                case "greedy-baseline" -> new VariantGreedyScheduler(coreVersion, provenance, variant);
                case "ga" -> new VariantGeneticEngine(new VariantGeneticEngine.Beans(algorithms, populations,
                        composition, retention, conditions, coreVersion, budget), variant);
                default -> new VariantTimelineEngine(new VariantTimelineEngine.Beans(composition, retention,
                        conditions, selection, retentionRepairer, budget, coreVersion), variant);
            });
        }
        return copies;
    }

    private Map<String, PlanRequest> instances() {
        Map<String, PlanRequest> all = new LinkedHashMap<>();
        DiagnosticInstances.PROFILES.forEach(profile -> all.put(profile, DiagnosticInstances.load(mapper, profile)));
        try (InputStream in = getClass().getResourceAsStream(FEASIBLE)) {
            all.put("feasible", mapper.readValue(in, PlanRequest.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return all;
    }

    private AbsenceClassifier classifier(PlanRequest request) {
        return AbsenceClassifier.of(request, graph(request));
    }

    private HardPrerequisiteGraph graph(PlanRequest request) {
        return HardPrerequisiteGraph.of(request.topics(), request.prerequisites(), conditions.provenance(request));
    }

    /** As linhas e os achados, acumulados ao longo da matriz. */
    private static final class Report {

        private final List<String> matrix = new ArrayList<>();
        private final List<String> statics = new ArrayList<>();
        private final List<String> windows = new ArrayList<>();
        private final List<String> cells = new ArrayList<>();
        private final List<String> pairs = new ArrayList<>();
        private final List<String> absences = new ArrayList<>();
        private final List<String> unfaithful = new ArrayList<>();
        private final List<String> violations = new ArrayList<>();
        private final Map<String, List<MeasurementSummary.Run>> open = new LinkedHashMap<>();
        private final Map<String, Integer> passes = new LinkedHashMap<>();

        void instance(String name, AbsenceClassifier classifier) {
            statics.addAll(classifier.staticRows(name));
            windows.add(String.join(",", name, Integer.toString(classifier.largestWindowMinutes()),
                    Integer.toString(classifier.neverFitsCount()), Integer.toString(classifier.dependentsCount())));
        }

        void add(String variant, EngineProbe.Outcome outcome, String recheck, List<String> prefixReading,
                VariantProbe hashes, AbsenceClassifier classifier, Set<String> topicIds) {

            PlanResponse response = outcome.response();
            AbsenceClassifier.Counts counts = classifier.classify(response);
            String sessionsHash = hashes.sessionsHash(response);
            open.computeIfAbsent(variant, key -> new ArrayList<>())
                    .add(new MeasurementSummary.Run(variant, outcome, sessionsHash, counts));
            passes.merge(variant, "pass".equals(recheck) ? 1 : 0, Integer::sum);
            String base = outcome.csv();
            String withoutMessage = base.substring(0, base.length() - Csv.quote(outcome.message()).length() - 1);
            matrix.add(String.join(",", variant, withoutMessage, recheck, declaredByAbsence(response, topicIds),
                    prefixMatches(response, prefixReading, topicIds), counts.csv(), hashes.responseHash(response),
                    sessionsHash, Csv.quote(outcome.message())));
        }

        void closeCell(String instance, String engine) {
            List<MeasurementSummary.Run> base = open.remove("base");
            List<MeasurementSummary.Run> skipAhead = open.remove("P");
            cells.add(MeasurementSummary.cell(base, passes.remove("base")));
            cells.add(MeasurementSummary.cell(skipAhead, passes.remove("P")));
            pairs.add(MeasurementSummary.pair(base, skipAhead));
            for (List<MeasurementSummary.Run> runs : List.of(base, skipAhead)) {
                List<MeasurementSummary.Run> accepted = runs.stream().filter(MeasurementSummary.Run::accepted)
                        .toList();
                List<MeasurementSummary.Run> refused = runs.stream().filter(run -> !run.accepted()).toList();
                if (!accepted.isEmpty()) {
                    absences.add(MeasurementSummary.absence(accepted, "accepted"));
                }
                if (!refused.isEmpty()) {
                    absences.add(MeasurementSummary.absence(refused, "refused"));
                }
            }
        }

        void write() {
            Csv.write("alocador-matriz.csv", "variant," + EngineProbe.Outcome.header().replace(",message", "")
                    + ",invariants_recheck,unscheduled_ids_match_absence,prefix_reading_matches,"
                    + AbsenceClassifier.Counts.header() + ",response_sha256,sessions_sha256,message", matrix);
            Csv.write("alocador-estatico.csv", AbsenceClassifier.staticHeader(), statics);
            Csv.write("alocador-janelas.csv", "instance,largest_window_minutes,never_fits_A,dependents_B",
                    windows);
            Csv.write("alocador-celulas.csv", MeasurementSummary.cellHeader(), cells);
            Csv.write("alocador-pares.csv", MeasurementSummary.pairHeader(), pairs);
            Csv.write("alocador-ausentes.csv", MeasurementSummary.absenceHeader(), absences);
        }

        /** Se {@code topics-unscheduled-ids} nomeia exatamente os tópicos sem sessão. */
        private static String declaredByAbsence(PlanResponse response, Set<String> topicIds) {
            if (response == null) {
                return "n/a";
            }
            return absent(response, topicIds).equals(declared(response)) ? "yes" : "no";
        }

        /** Se a leitura de prefixo de produção ({@code skip(scheduled)}) teria nomeado os mesmos. */
        private static String prefixMatches(PlanResponse response, List<String> prefixReading,
                Set<String> topicIds) {
            if (response == null) {
                return "n/a";
            }
            return absent(response, topicIds).equals(new TreeSet<>(prefixReading)) ? "yes" : "no";
        }

        /** Os tópicos do pedido sem nenhuma sessão na resposta. */
        private static Set<String> absent(PlanResponse response, Set<String> topicIds) {
            Set<String> absent = new TreeSet<>(topicIds);
            response.sessions().forEach(session -> absent.remove(session.topicId().toString()));
            return absent;
        }

        private static Set<String> declared(PlanResponse response) {
            Set<String> ids = new TreeSet<>();
            if (response.fitness().get("topics-unscheduled-ids") instanceof Collection<?> list) {
                list.forEach(id -> ids.add(String.valueOf(id)));
            }
            return ids;
        }
    }
}
