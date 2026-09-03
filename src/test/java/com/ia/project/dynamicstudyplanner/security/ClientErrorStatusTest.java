package com.ia.project.dynamicstudyplanner.security;

import com.ia.project.dynamicstudyplanner.support.RequestPayloads;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão do achado S8: erro de cliente não pode voltar como erro de servidor.
 *
 * <h2>Os cinco cenários</h2>
 *
 * São exatamente os cinco medidos em {@code docs/qualidade/02-diagnostico-seguranca.md} §4.2, todos
 * devolvendo <b>500</b> antes da etapa 02b, porque o então único {@code GlobalExceptionHandler}
 * não tinha tratador para as exceções padrão de erro de cliente do Spring MVC e todas caíam no
 * catch-all. Esses tratadores vivem hoje em {@code api.exception.RequestErrorAdvice}, e
 * {@code api.exception.AdviceOrderTest} trava a ordem que impede o catch-all de voltar a engoli-los.
 *
 * <p>Cada 500 indevido tinha três custos: informava o cliente errado, despejava pilha completa no
 * log de ERRO — que é o veículo dos vazamentos S2 e S3 — e inutilizava qualquer alerta baseado em
 * taxa de 5xx, porque um varredor automático batendo com {@code GET} gerava 5xx em volume.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "api.rate-limit.capacity=100",
        "api.rate-limit.refill-tokens=100"
})
@DisplayName("S8: erro de cliente devolve 4xx, nunca 500")
class ClientErrorStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("cenario 1: valor de tipo incompativel devolve 400")
    void tipoIncompativelDevolve400() throws Exception {
        String payload = """
                { "exam": {"name":"C","examDate":"2030-01-01","generalKnowledgeTotalScore":10,
                  "generalKnowledgeSubjects":[],"specificKnowledgeAxes":[]},
                  "studentProfile": {"name":"Aluno","knowledgeGaps":{"X":"NAO_E_NUMERO"},
                  "weeklyAvailability":{"MONDAY":3}},
                  "gaConfig": {"totalStudyDays":100,"numGenerations":30,"populationSize":20} }
                """;

        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.type")
                        .value("https://api.dynamicstudyplanner.com/errors/malformed-body"))
                // O campo problematico e nomeado por caminho completo, para o cliente conseguir
                // corrigir sem adivinhar onde esta.
                .andExpect(jsonPath("$.invalid_params[0].name")
                        .value("studentProfile.knowledgeGaps.X"))
                // ...e o valor que ele enviou NAO aparece em lugar nenhum da resposta.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("NAO_E_NUMERO"))));
    }

    @Test
    @DisplayName("cenario 2: JSON sintaticamente malformado devolve 400")
    void jsonMalformadoDevolve400() throws Exception {
        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"exam\": { isto nao e json valido "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("cenarios 3, 4 e 5: GET, PUT e DELETE devolvem 405 com cabecalho Allow")
    void verbosNaoSuportadosDevolvem405() throws Exception {
        for (HttpMethod metodo : new HttpMethod[]{HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE}) {
            MvcResult resultado = mockMvc.perform(request(metodo, "/api/v1/optimizer/generate"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.title").value("Method Not Allowed"))
                    .andReturn();

            assertThat(resultado.getResponse().getHeader("Allow"))
                    .as("a RFC 9110 exige o cabecalho Allow numa resposta 405 (metodo %s)", metodo)
                    .isNotNull()
                    .contains("POST");
        }
    }

    @Test
    @DisplayName("tipo de midia nao suportado devolve 415")
    void tipoDeMidiaNaoSuportadoDevolve415() throws Exception {
        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.TEXT_PLAIN).content("texto puro"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("rota inexistente devolve 404")
    void rotaInexistenteDevolve404() throws Exception {
        mockMvc.perform(post("/api/v1/rota/que/nao/existe")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("o caminho de sucesso continua intacto depois das mudancas")
    void oCaminhoDeSucessoContinuaIntacto() throws Exception {
        MvcResult inicial = mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestPayloads.requisicaoValida()))
                .andReturn();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .asyncDispatch(inicial))
                .andExpect(status().isOk());
    }
}
