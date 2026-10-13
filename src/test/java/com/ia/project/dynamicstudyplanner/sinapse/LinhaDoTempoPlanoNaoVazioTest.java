package com.ia.project.dynamicstudyplanner.sinapse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O {@code ga-timeline} devolve plano não vazio e válido onde existe plano (EOA-12, decisão D1).
 *
 * <h2>O que é portão aqui, e o que não é</h2>
 *
 * Portão, pela ADR-0010: nas três instâncias do catálogo real e nas sementes 1 a 10, as 30 respostas
 * são não vazias e passam por {@link PlanOutputInvariants}. A qualidade comparada com o guloso é
 * hipótese medida em {@code docs/REPARO_ESCALA_REAL.md}, e não entra neste teste.
 *
 * <h2>Por que o reprodutor viável, e não o degenerado</h2>
 *
 * {@code ga-timeline-empty-minimal-feasible.json} tem um tópico de 50 min e um de 150 min numa única
 * janela de 70 min: existe um plano de uma sessão, que o guloso encontra. Antes deste reparo o
 * {@code ga-timeline} o recusava com {@code plan-would-be-empty} nas dez sementes, porque o plano
 * vazio pontua {@code NaN} e {@code Population.getFittest} põe {@code NaN} acima de todo número. O
 * reprodutor degenerado ({@code ga-timeline-empty-minimal.json}) não serve: nele nada cabe, e a recusa
 * está certa.
 *
 * <h2>Motor de produção, orçamento de produção</h2>
 *
 * Sobe o contexto e chama o {@link PlanEngineSelector} injetado, com 60 gerações e população 40, e
 * não o {@code PlanEngines} de teste, que reduz o orçamento: o portão é sobre o motor que a plataforma
 * chama.
 */
@SpringBootTest
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("ga-timeline: plano nao vazio e valido no catalogo real (EOA-12, D1)")
class LinhaDoTempoPlanoNaoVazioTest {

    private static final String REPRODUTOR_VIAVEL =
            "/instances/reproducers/ga-timeline-empty-minimal-feasible.json";

    private static final String INSTANCIA = "/instances/plan-request-catalogo-real-%s.json";

    /** O único tópico do reprodutor viável que cabe na janela de 70 min: 50 min, posição 31. */
    private static final UUID TOPICO_QUE_CABE = UUID.fromString("303030b9-7738-3905-b9ee-eb82c107fa05");

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private PlanEngineSelector selector;

    @Autowired
    private RequestConditions conditions;

    static Stream<Long> sementes() {
        return LongStream.rangeClosed(1, 10).boxed();
    }

    static Stream<Arguments> instanciasESementes() {
        return Stream.of("apertado", "medio", "folgado")
                .flatMap(perfil -> sementes().map(semente -> Arguments.of(perfil, semente)));
    }

    @ParameterizedTest(name = "semente {0}")
    @MethodSource("sementes")
    @DisplayName("reprodutor viavel: devolve a sessao de 50 min que cabe, em vez de recusar")
    void reprodutorViavelDevolveASessaoQueCabe(long semente) throws IOException {
        PlanRequest pedido = comMotorESemente(ler(REPRODUTOR_VIAVEL), semente);

        PlanResponse resposta = planejaSemRecusa(pedido);

        assertThat(resposta.sessions()).as("semente %d", semente).isNotEmpty();
        assertThat(resposta.sessions()).as("so o topico de 50 min cabe numa janela de 70 min")
                .allMatch(sessao -> sessao.topicId().equals(TOPICO_QUE_CABE));
        conferirInvariantes(pedido, resposta);
    }

    @ParameterizedTest(name = "{0}, semente {1}")
    @MethodSource("instanciasESementes")
    @DisplayName("catalogo real: resposta nao vazia que passa pelas invariantes de saida")
    void catalogoRealDevolvePlanoNaoVazioEValido(String perfil, long semente) throws IOException {
        PlanRequest pedido = comMotorESemente(ler(String.format(INSTANCIA, perfil)), semente);

        PlanResponse resposta = planejaSemRecusa(pedido);

        assertThat(resposta.sessions()).as("%s, semente %d", perfil, semente).isNotEmpty();
        conferirInvariantes(pedido, resposta);
    }

    /**
     * Planeja e transforma uma recusa em falha que nomeia o motivo.
     *
     * <p>Sem isto, a falha seria só "exceção inesperada"; com isto, o vermelho diz
     * {@code plan-would-be-empty}, que é o motivo que este teste existe para cobrar.
     */
    private PlanResponse planejaSemRecusa(PlanRequest pedido) {
        try {
            return selector.plan(pedido);
        } catch (PlanRejectedException recusa) {
            throw new AssertionError("ga-timeline recusou com " + recusa.reason() + " (semente "
                    + pedido.randomSeed() + "): " + recusa.getMessage(), recusa);
        }
    }

    /**
     * O motor já roda as invariantes antes de responder; repeti-las aqui torna o portão explícito em
     * vez de depender de que o motor continue a chamá-las.
     */
    private void conferirInvariantes(PlanRequest pedido, PlanResponse resposta) {
        HardPrerequisiteGraph grafo = HardPrerequisiteGraph.of(pedido.topics(),
                pedido.prerequisites(), conditions.provenance(pedido));
        PlanOutputInvariants.check(pedido, resposta, grafo);
    }

    private PlanRequest ler(String recurso) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(recurso)) {
            assertThat(in).as("recurso ausente: %s", recurso).isNotNull();
            return mapper.readValue(in, PlanRequest.class);
        }
    }

    /** O mesmo pedido, pedindo o {@code ga-timeline} pelo nome e com outra semente. */
    private static PlanRequest comMotorESemente(PlanRequest pedido, long semente) {
        Map<String, Object> params = new HashMap<>(pedido.algorithmParams());
        params.put(PlanEngineSelector.ENGINE_PARAM, TimelinePlanEngine.ID);
        return new PlanRequest(pedido.contractVersion(), pedido.horizon(), pedido.availability(),
                pedido.goals(), pedido.topics(), pedido.prerequisites(), pedido.history(),
                params, semente);
    }
}
