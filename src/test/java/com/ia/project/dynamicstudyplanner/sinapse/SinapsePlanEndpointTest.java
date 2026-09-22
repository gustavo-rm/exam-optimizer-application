package com.ia.project.dynamicstudyplanner.sinapse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.plan.PlanEngine;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * {@code POST /plans} com {@code engine: "ga"}, pelo contexto Spring de verdade.
 *
 * <h2>O que este teste cobre que os unitários não cobrem</h2>
 *
 * Os testes de {@code sinapse} e de {@code plan} constroem o motor à mão, o que é deliberado: falham
 * pelo algoritmo, não pela fiação. O preço é que um {@code @Qualifier} errado, uma propriedade
 * ausente ou um seletor não injetado no controlador passariam por eles inteiros.
 *
 * <p>Este sobe o contexto e faz um POST. É o único lugar que prova que
 * {@code sinapseFitnessComposition} resolve pelo nome, que {@code plan.engine.ga.*} existe no
 * {@code application-baseline-core.properties}, e que o controlador chama o seletor em vez de um
 * motor fixo.
 *
 * <p>O documento postado é o de referência que o {@code sinapse-platform} mantém byte a byte, com
 * {@code algorithmParams.engine} acrescentado — uma requisição escrita aqui poderia descrever um
 * payload que a plataforma nunca envia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("POST /plans com o motor genetico")
class SinapsePlanEndpointTest {

    private static final String REQUEST_GOLDEN = "contract/plan-request-v1.0.json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** O documento de referência com o motor genético pedido em {@code algorithmParams}. */
    private String requestAskingForGa() throws Exception {
        try (InputStream stream = new ClassPathResource(REQUEST_GOLDEN).getInputStream()) {
            JsonNode document = objectMapper.readTree(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            ((com.fasterxml.jackson.databind.node.ObjectNode) document.get("algorithmParams"))
                    .put(PlanEngineSelector.ENGINE_PARAM, GeneticPlanEngine.ID);
            return objectMapper.writeValueAsString(document);
        }
    }

    private MvcResult postAskingForGa() throws Exception {
        return mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestAskingForGa()))
                .andReturn();
    }

    @Test
    @DisplayName("responde 200 e o plano declara que o motor genetico rodou")
    void respondeEDeclaraOMotor() throws Exception {
        MvcResult result = postAskingForGa();

        assertThat(result.getResponse().getStatus())
                .as("corpo: %s", result.getResponse().getContentAsString())
                .isEqualTo(200);

        JsonNode answer = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(answer.path("fitness").path(PlanEngine.FITNESS_ENGINE_KEY).asText())
                .isEqualTo(GeneticPlanEngine.ID);
        assertThat(answer.path("sessions")).isNotEmpty();
        assertThat(answer.path("metadata").path("coreVersion").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a semente volta ecoada, como a plataforma exige")
    void aSementeVoltaEcoada() throws Exception {
        JsonNode sent = objectMapper.readTree(requestAskingForGa());
        JsonNode answer = objectMapper.readTree(
                postAskingForGa().getResponse().getContentAsString());

        assertThat(answer.path("metadata").path("randomSeed").asLong())
                .isEqualTo(sent.path("randomSeed").asLong());
    }

    @Test
    @DisplayName("os dois motores estao registrados no contexto")
    void osDoisMotoresEstaoRegistrados(@Autowired PlanEngineSelector selector) {
        // Ordenados pelo id, para a mensagem de erro de um motor desconhecido ser estavel.
        assertThat(selector.registeredIds())
                .containsExactly(GeneticPlanEngine.ID,
                        com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine.ID);
    }
}
