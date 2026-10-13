package com.ia.project.dynamicstudyplanner.diagnostic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.plan.BuildIdentity;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PlanInvariantViolationException;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.RequestConditions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Antes e depois do reparo do EOA-12: os três motores, as três instâncias e os dois reprodutores,
 * sementes 1 a 10, com o hash de cada resposta. <b>Mede e relata; não é portão.</b>
 *
 * <h2>Desligado no {@code verify}</h2>
 *
 * Como {@link RealCatalogDiagnosticTest}, só roda com {@code -Ddiagnostic=true}. O portão da decisão D1
 * (ADR-0010) é {@code LinhaDoTempoPlanoNaoVazioTest}, que roda sempre. Exemplo:
 * <pre>
 * ./mvnw test -Dtest=RealCatalogRepairTest -Ddiagnostic=true -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <h2>O hash, e o que fica fora dele</h2>
 *
 * SHA-256 da resposta serializada pelo {@code ObjectMapper} do contexto, com as chaves de todo mapa
 * em ordem e <b>sem {@value BuildIdentity#FITNESS_KEY}</b>: essa chave é o commit do build, com
 * sufixo {@code -dirty} para árvore suja, e mudaria entre o antes e o depois sem que o plano mudasse.
 * Todo o resto entra, inclusive {@code metadata}. Um segundo hash cobre só {@code sessions}, para
 * contar planos distintos entre sementes.
 *
 * <p>A ordem das chaves é imposta porque {@code PlanResponse} copia {@code fitness} com
 * {@code Map.copyOf}, cuja ordem de iteração muda de uma JVM para outra: sem isso, a mesma resposta
 * daria hashes diferentes em duas execuções do mesmo commit. O conteúdo é o mesmo; os bytes da
 * serialização padrão, não.
 *
 * <h2>Uma regra que vale para tudo aqui</h2>
 *
 * Todo plano aceito é pontuado pelo avaliador a posteriori do EOA-10 ({@link EngineProbe}, que usa
 * {@link PlanScoring} com a composição de produção). O agregado é o objetivo do genético relatado por
 * completude; nenhuma linha daqui afirma que um motor é superior a outro.
 */
@SpringBootTest
@ActiveProfiles(PlanProtocol.PROFILE)
@EnabledIfSystemProperty(named = "diagnostic", matches = "true")
@DisplayName("Reparo do EOA-12: antes e depois, com hash de cada resposta")
class RealCatalogRepairTest {

    static final List<String> ENGINES = List.of("greedy-baseline", "ga", "ga-timeline");

    /** Os reprodutores do EOA-10: o viável isola o defeito; no degenerado nada cabe e recusar é certo. */
    private static final Map<String, String> REPRODUCERS = Map.of(
            "feasible", "/instances/reproducers/ga-timeline-empty-minimal-feasible.json",
            "degenerate", "/instances/reproducers/ga-timeline-empty-minimal.json");

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

    @Test
    @DisplayName("tres motores x (tres instancias + dois reprodutores) x sementes 1 a 10")
    void antesEDepois() {
        EngineProbe probe = new EngineProbe(selector, new PlanScoring(composition, retention),
                conditions, budget);
        List<String> rows = new ArrayList<>();
        for (Map.Entry<String, PlanRequest> instance : instances().entrySet()) {
            for (String engine : ENGINES) {
                for (long seed : RealCatalogDiagnosticTest.SEEDS) {
                    PlanRequest request = DiagnosticInstances.forEngine(instance.getValue(), engine, seed);
                    rows.add(row(request, probe.run(instance.getKey(), request)));
                }
            }
        }
        Csv.write("reparo.csv", header(), rows);
        assertThat(rows).hasSize(5 * ENGINES.size() * RealCatalogDiagnosticTest.SEEDS.size());
    }

    private Map<String, PlanRequest> instances() {
        Map<String, PlanRequest> all = new LinkedHashMap<>();
        DiagnosticInstances.PROFILES.forEach(profile ->
                all.put(profile, DiagnosticInstances.load(mapper, profile)));
        for (String name : List.of("feasible", "degenerate")) {
            try (InputStream in = getClass().getResourceAsStream(REPRODUCERS.get(name))) {
                all.put(name, mapper.readValue(in, PlanRequest.class));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return all;
    }

    static String header() {
        return EngineProbe.Outcome.header().replace(",message", "")
                + ",invariants_recheck,search_initial_best,search_final_best,response_sha256,"
                + "sessions_sha256,message";
    }

    private String row(PlanRequest request, EngineProbe.Outcome outcome) {
        String base = outcome.csv();
        String withoutMessage = base.substring(0, base.length() - Csv.quote(outcome.message()).length() - 1);
        PlanResponse response = outcome.response();
        if (response == null) {
            return String.join(",", withoutMessage, "n/a", "", "", "", "", Csv.quote(outcome.message()));
        }
        return String.join(",", withoutMessage, recheck(request, response),
                String.valueOf(response.fitness().get("search-initial-best")),
                String.valueOf(response.fitness().get("search-final-best")),
                sha256(withoutBuild(response)), sha256(response.sessions()),
                Csv.quote(outcome.message()));
    }

    /** O motor já roda as invariantes; repetir aqui deixa a coluna explícita. */
    private String recheck(PlanRequest request, PlanResponse response) {
        HardPrerequisiteGraph graph = HardPrerequisiteGraph.of(request.topics(),
                request.prerequisites(), conditions.provenance(request));
        try {
            PlanOutputInvariants.check(request, response, graph);
            return "pass";
        } catch (PlanInvariantViolationException violation) {
            return Csv.quote("fail: " + violation.getMessage());
        }
    }

    private static PlanResponse withoutBuild(PlanResponse response) {
        Map<String, Object> fitness = new HashMap<>(response.fitness());
        fitness.remove(BuildIdentity.FITNESS_KEY);
        return new PlanResponse(response.contractVersion(), response.sessions(), fitness,
                response.metadata());
    }

    private String sha256(Object value) {
        try {
            byte[] json = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
