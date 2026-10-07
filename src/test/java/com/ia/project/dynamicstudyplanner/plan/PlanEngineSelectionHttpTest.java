package com.ia.project.dynamicstudyplanner.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Como {@code POST /plans} trata {@code algorithmParams.engine}, por HTTP e pelo contexto Spring de
 * verdade, no estilo de {@code SinapsePlanEndpointTest}.
 *
 * <h2>O corpo é o de referência, alterado só em memória</h2>
 *
 * Toda requisição parte de {@code contract/plan-request-v1.0.json}, o documento que o
 * {@code sinapse-platform} mantém byte a byte, e mexe apenas na chave {@code engine} do
 * {@code algorithmParams} lido. O arquivo nunca é editado: uma divergência nele é divergência entre
 * os dois repositórios, não um ajuste de teste.
 *
 * <h2>Caracterização, não especificação</h2>
 *
 * Os três casos de valor (vazio, {@code null} e ausente) descrevem o comportamento <b>medido</b> em
 * 07/10/2026 e não o que se gostaria que fosse. Se um deles mudar, a mudança tem de ser deliberada,
 * e a plataforma, avisada: ela vai passar a enviar esta chave a partir de configuração própria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("POST /plans: escolha do motor por algorithmParams.engine")
class PlanEngineSelectionHttpTest {

    private static final String REQUEST_GOLDEN = "contract/plan-request-v1.0.json";
    private static final String ERROR_TYPE_BASE = "https://api.dynamicstudyplanner.com/errors/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Posta o documento de referência depois de {@code edit} alterar o seu {@code algorithmParams}. */
    private MvcResult postWith(Consumer<ObjectNode> edit) throws Exception {
        try (InputStream stream = new ClassPathResource(REQUEST_GOLDEN).getInputStream()) {
            JsonNode document = objectMapper.readTree(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            edit.accept((ObjectNode) document.get("algorithmParams"));
            return mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(document)))
                    .andReturn();
        }
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static void assertStatus(MvcResult result, int expected) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as("corpo: %s", result.getResponse().getContentAsString())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("o padrão nomeado explicitamente dá o MESMO plano que a chave ausente")
    void padraoExplicitoEhIdenticoAoAusente() throws Exception {
        MvcResult absent = postWith(params -> params.remove(PlanEngineSelector.ENGINE_PARAM));
        MvcResult explicit = postWith(params ->
                params.put(PlanEngineSelector.ENGINE_PARAM, GreedyBaselineEngine.ID));
        assertStatus(absent, 200);
        assertStatus(explicit, 200);

        JsonNode semChave = identity(body(absent));
        JsonNode comChave = identity(body(explicit));

        // As tres asercoes parciais existem para a falha dizer ONDE divergiu; a ultima e a que
        // garante a identidade inteira.
        assertThat(comChave.path("sessions")).as("sessoes, todos os campos")
                .isEqualTo(semChave.path("sessions"));
        assertThat(comChave.path("fitness")).as("mapa de fitness, inclusive fitness.engine")
                .isEqualTo(semChave.path("fitness"));
        assertThat(comChave.path("fitness").path(PlanEngine.FITNESS_ENGINE_KEY).asText())
                .isEqualTo(GreedyBaselineEngine.ID);
        assertThat(comChave).as("a resposta inteira, exceto metadata.elapsedMillis")
                .isEqualTo(semChave);
    }

    /**
     * A resposta sem o que não é identidade do plano.
     *
     * <p>Só {@code metadata.elapsedMillis} sai: é medição de tempo, não propriedade do plano. Hoje
     * todo motor publica 0, mas o EOA-13 vai torná-lo real, e então duas execuções do mesmo pedido
     * passarão a diferir nele. A exclusão fica pronta para isso e não esconde mais nada.
     */
    private static JsonNode identity(JsonNode response) {
        JsonNode copy = response.deepCopy();
        ((ObjectNode) copy.path("metadata")).remove("elapsedMillis");
        return copy;
    }

    @Test
    @DisplayName("engine vazio: 422 unknown-engine, e não o padrão")
    void engineVazioEhRecusadoComoDesconhecido() throws Exception {
        MvcResult result = postWith(params -> params.put(PlanEngineSelector.ENGINE_PARAM, ""));

        assertStatus(result, 422);
        assertThat(result.getResponse().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode problem = body(result);
        assertThat(problem.path("type").asText()).isEqualTo(ERROR_TYPE_BASE + "unknown-engine");
        assertThat(problem.path("offending")).hasSize(1);
        assertThat(problem.path("offending").get(0).asText()).isEmpty();
    }

    /**
     * {@code engine: null} não chega ao seletor.
     *
     * <p>O ramo "{@code null} usa o padrão" de {@link PlanEngineSelector} existe, mas é inalcançável
     * por HTTP: o construtor compacto de {@code PlanRequest} copia o mapa com
     * {@code Map.copyOf(algorithmParams)} ({@code PlanRequest.java:87}), que recusa valor nulo com
     * {@code NullPointerException}. O Jackson a embrulha em {@code ValueInstantiationException}, e
     * {@code RequestErrorAdvice} responde {@code 400 malformed-body} antes de qualquer motor ser
     * escolhido. Para usar o padrão, a plataforma omite a chave.
     */
    @Test
    @DisplayName("engine null: 400 malformed-body, recusado na desserialização")
    void engineNuloEhRecusadoNaDesserializacao() throws Exception {
        MvcResult result = postWith(params -> params.putNull(PlanEngineSelector.ENGINE_PARAM));

        assertStatus(result, 400);
        assertThat(result.getResponse().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body(result).path("type").asText()).isEqualTo(ERROR_TYPE_BASE + "malformed-body");
    }

    @Test
    @DisplayName("engine ausente: 200 com o motor padrão, greedy-baseline")
    void engineAusenteUsaOPadrao() throws Exception {
        MvcResult result = postWith(params -> params.remove(PlanEngineSelector.ENGINE_PARAM));

        assertStatus(result, 200);
        JsonNode plan = body(result);
        assertThat(plan.path("fitness").path(PlanEngine.FITNESS_ENGINE_KEY).asText())
                .isEqualTo(GreedyBaselineEngine.ID);
        assertThat(plan.path("sessions")).isNotEmpty();
    }
}
