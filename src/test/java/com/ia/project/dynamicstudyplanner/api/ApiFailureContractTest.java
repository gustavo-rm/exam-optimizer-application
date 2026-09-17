package com.ia.project.dynamicstudyplanner.api;

import com.ia.project.dynamicstudyplanner.support.RequestPayloads;
import com.ia.project.dynamicstudyplanner.usecase.GenerateStudyPlanUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os dois modos de falha do fluxo assíncrono: estouro de prazo e erro inesperado.
 *
 * <h2>Por que o caso de uso é substituído por um dublê</h2>
 *
 * O {@code 408} do controller vem de {@code .orTimeout(30, TimeUnit.SECONDS)}. Provocá-lo de
 * verdade custaria trinta segundos de suíte por teste, e a única forma de reduzir esse tempo seria
 * tornar o prazo configurável — mudança em código de produção, fora do escopo de uma etapa de
 * testes. Substituir o {@code GenerateStudyPlanUseCase} por um dublê que já devolve o futuro
 * fracassado exercita exatamente o que interessa aqui: o transporte da falha pelo redespacho
 * assíncrono do Servlet e a tradução dela em RFC 7807 pelo {@code InfrastructureErrorAdvice}.
 *
 * <p>O que este recorte <b>não</b> cobre é o próprio {@code orTimeout} disparar no prazo certo. Isso
 * está registrado como pendência P4 em {@code docs/qualidade/01b-correcao-testes.md}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "api.rate-limit.capacity=100",
        "api.rate-limit.refill-tokens=100"
})
@DisplayName("Contrato de falha do fluxo assincrono")
class ApiFailureContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerateStudyPlanUseCase useCase;

    private MvcResult dispararEObterResposta() throws Exception {
        MvcResult inicial = mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestPayloads.requisicaoValida()))
                .andExpect(request().asyncStarted())
                .andReturn();

        return mockMvc.perform(asyncDispatch(inicial)).andReturn();
    }

    @Test
    @DisplayName("408: o estouro de prazo chega ao cliente como Request Timeout em RFC 7807")
    void estouroDePrazoDevolve408() throws Exception {
        when(useCase.generateFullStudyPlan(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(
                        new TimeoutException("The computation exceeded its budget.")));

        MvcResult inicial = mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestPayloads.requisicaoValida()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(inicial))
                .andExpect(status().isRequestTimeout())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type")
                        .value("https://api.dynamicstudyplanner.com/errors/request-timeout"))
                .andExpect(jsonPath("$.title").value("Request Timeout"))
                .andExpect(jsonPath("$.status").value(408))
                .andExpect(jsonPath("$.detail").value("The computation took too long and timed out."))
                .andExpect(jsonPath("$.instance").value("/api/v1/optimizer/generate"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("500: um erro inesperado nao vaza detalhe interno para o cliente")
    void erroInesperadoDevolve500SemVazarDetalhe() throws Exception {
        String segredoInterno = "NullPointerException em HybridRetentionEngine linha 42";

        when(useCase.generateFullStudyPlan(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException(segredoInterno)));

        MvcResult resposta = dispararEObterResposta();

        org.assertj.core.api.Assertions.assertThat(resposta.getResponse().getStatus())
                .as("falha inesperada deve virar 500, nao 200 nem estouro de pilha")
                .isEqualTo(500);

        String corpo = resposta.getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(corpo)
                .as("o corpo do 500 nao pode conter a mensagem interna nem o nome da classe de erro")
                .doesNotContain(segredoInterno)
                .doesNotContain("IllegalStateException")
                .doesNotContain("at com.ia.project");

        org.assertj.core.api.Assertions.assertThat(corpo)
                .as("o 500 tambem segue RFC 7807")
                .contains("\"status\":500")
                .contains("Internal Server Error")
                .contains("An unexpected internal error occurred.")
                .contains("https://api.dynamicstudyplanner.com/errors/internal-server-error");
    }

    @Test
    @DisplayName("500: excecao lancada de forma sincrona pelo caso de uso tambem e contida")
    void excecaoSincronaTambemDevolve500() throws Exception {
        when(useCase.generateFullStudyPlan(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("falha antes de qualquer futuro ser criado"));

        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestPayloads.requisicaoValida()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    @DisplayName("o caso de uso recebe exatamente os parametros do gaConfig enviado")
    void osParametrosDoPedidoChegamAoCasoDeUso() throws Exception {
        when(useCase.generateFullStudyPlan(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("irrelevante aqui")));

        dispararEObterResposta();

        // Fecha o buraco entre "o controller respondeu" e "o controller pediu a coisa certa":
        // um gaConfig lido do campo errado passaria por qualquer asserção sobre o corpo da resposta.
        org.mockito.Mockito.verify(useCase).generateFullStudyPlan(
                org.mockito.ArgumentMatchers.argThat(exam ->
                        exam != null && exam.getAllSubjects().size() == 3),
                org.mockito.ArgumentMatchers.argThat(profile ->
                        profile != null && "Aluno de Teste".equals(profile.getName())),
                org.mockito.ArgumentMatchers.eq(RequestPayloads.TOTAL_STUDY_DAYS),
                org.mockito.ArgumentMatchers.eq(30),
                org.mockito.ArgumentMatchers.eq(20));
    }
}
