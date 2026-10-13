package com.ia.project.dynamicstudyplanner.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.config.GeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.generator.PopulationGenerator;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.RequestConditions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * {@code generations}, {@code population-size} e {@code mutation-rate} em {@code algorithmParams} não
 * mudam o plano: os hiperparâmetros do AG são do Core (D4, Variante 1, ADR 0009).
 *
 * <h2>Executado, não lido</h2>
 *
 * Até o EOA-13 a afirmação vinha de um {@code grep} em {@code src/main}. Aqui o mesmo pedido vai pelo
 * endpoint de verdade, com o contexto de produção, uma vez com cada chave num valor mínimo e num
 * máximo plausíveis, e o plano tem de sair idêntico ao da chave como a plataforma a envia.
 *
 * <h2>Por que o catálogo real folgado, com a semente 20260903</h2>
 *
 * No pedido de referência o {@code ga} produz uma única sessão: um plano que nenhum parâmetro teria
 * como mudar não prova nada. Das instâncias copiadas da plataforma, o folgado com esta semente é a
 * combinação em que o {@code ga} aceita o pedido e produz seis sessões (medido no EOA-13).
 *
 * <h2>O controle positivo</h2>
 *
 * Uma igualdade só significa algo se o instrumento conseguiria ver uma diferença. O último teste
 * monta o MESMO motor, com os beans de produção, mudando só o orçamento do Core para uma geração — e
 * o plano muda. Logo o pedido é sensível ao orçamento, e o que o {@code algorithmParams} não move é
 * porque não é lido. {@code mutation-rate} não tem botão equivalente fora do código: é constante de
 * {@code DefaultGeneticAlgorithmFactory}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("Hiperparametros do AG sao do Core: algorithmParams nao os muda")
class HiperparametrosDoCoreHttpTest {

    private static final String FOLGADO = "instances/plan-request-catalogo-real-folgado.json";
    private static final long SEMENTE = 20260903L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GeneticAlgorithmFactory algorithms;

    @Autowired
    private PopulationGenerator populations;

    @Autowired
    @Qualifier("sinapseFitnessComposition")
    private FitnessComposition composition;

    @Autowired
    private RetentionAlgorithm retention;

    @Autowired
    private RequestConditions conditions;

    @Autowired
    private GeneticSearchBudget budget;

    @Value("${baseline.core.version}")
    private String coreVersion;

    private ObjectNode pedido(Consumer<ObjectNode> edit) throws Exception {
        try (InputStream stream = new ClassPathResource(FOLGADO).getInputStream()) {
            ObjectNode document = (ObjectNode) objectMapper.readTree(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            document.put("randomSeed", SEMENTE);
            ObjectNode params = (ObjectNode) document.get("algorithmParams");
            params.put(PlanEngineSelector.ENGINE_PARAM, GeneticPlanEngine.ID);
            edit.accept(params);
            return document;
        }
    }

    private JsonNode resposta(ObjectNode document) throws Exception {
        MvcResult result = mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(document)))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("corpo: %s", result.getResponse().getContentAsString())
                .isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    static Stream<Arguments> valoresExtremos() {
        return Stream.of(
                Arguments.of("generations", 1), Arguments.of("generations", 200),
                Arguments.of("population-size", 2), Arguments.of("population-size", 1000),
                Arguments.of("mutation-rate", 0.0), Arguments.of("mutation-rate", 1.0));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("valoresExtremos")
    @DisplayName("o mesmo pedido e a mesma semente dao o mesmo plano, qualquer que seja o valor")
    void oValorEnviadoNaoMudaOPlano(String chave, Number valor) throws Exception {
        JsonNode comoAPlataformaEnvia = resposta(pedido(params -> { }));
        JsonNode comOutroValor = resposta(pedido(params ->
                params.set(chave, objectMapper.valueToTree(valor))));

        assertThat(comOutroValor.path("sessions")).as("sessoes")
                .isEqualTo(comoAPlataformaEnvia.path("sessions"));
        assertThat(comOutroValor.path("fitness")).as("fitness")
                .isEqualTo(comoAPlataformaEnvia.path("fitness"));
        assertThat(comOutroValor.path("metadata")).as("metadata")
                .isEqualTo(comoAPlataformaEnvia.path("metadata"));
        assertThat(comOutroValor.path("sessions")).as("um plano vazio nao provaria nada")
                .hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("metadata.generations e o orcamento do Core, nao o numero enviado")
    void geracoesRelatadasSaoAsDoCore() throws Exception {
        JsonNode plano = resposta(pedido(params -> params.put("generations", 400)));

        assertThat(plano.path("metadata").path("generations").asInt())
                .isEqualTo(budget.generations())
                .isNotEqualTo(400);
    }

    @Test
    @DisplayName("controle positivo: o orcamento do Core, ao contrario, muda o plano")
    void oOrcamentoDoCoreMudaOPlano() throws Exception {
        PlanRequest request = objectMapper.treeToValue(pedido(params -> { }), PlanRequest.class);
        GeneticPlanEngine umaGeracao = new GeneticPlanEngine(algorithms, populations, composition,
                retention, conditions, coreVersion, new GeneticSearchBudget(1, budget.populationSize()));
        GeneticPlanEngine producao = new GeneticPlanEngine(algorithms, populations, composition,
                retention, conditions, coreVersion, budget);

        PlanResponse curto = umaGeracao.plan(request);
        PlanResponse normal = producao.plan(request);

        assertThat(normal.sessions()).as("o motor montado aqui reproduz o do endpoint")
                .isEqualTo(objectMapper.treeToValue(resposta(pedido(params -> { })),
                        PlanResponse.class).sessions());
        assertThat(curto.sessions()).isNotEqualTo(normal.sessions());
    }
}
