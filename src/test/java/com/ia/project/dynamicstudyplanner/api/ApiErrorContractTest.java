package com.ia.project.dynamicstudyplanner.api;

import com.ia.project.dynamicstudyplanner.support.RequestPayloads;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O contrato de erro da API, verificado como resposta HTTP de verdade.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * O {@code README.md} promete RFC 7807 (<i>Problem Details for HTTP APIs</i>, o padrão que define um
 * corpo de erro com os campos {@code type}, {@code title}, {@code status}, {@code detail} e
 * {@code instance}) para todos os erros. Antes da etapa 01b, das nove funções do
 * {@code GlobalExceptionHandler} apenas a de validação executava em algum teste — as outras oito
 * tinham zero instruções cobertas. Ou seja, o formato de erro que o cliente recebe não era
 * verificado em lugar nenhum, exceto no {@code 400}.
 *
 * <h2>O que fica de fora e por quê</h2>
 *
 * O {@code 408} e o {@code 500} exigem substituir o caso de uso por um dublê e estão em
 * {@code ApiFailureContractTest}. O {@code 422}, o {@code 401} e o {@code 403} não são alcançáveis
 * pela porta HTTP com a configuração atual — a razão está documentada em
 * {@code GlobalExceptionHandlerTest}, que os cobre diretamente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // O padrão de produção é 5/minuto. Fixado aqui para que o teste do 429 não dependa
        // do valor configurado em application.properties.
        "api.rate-limit.capacity=3",
        "api.rate-limit.refill-tokens=3",
        "api.rate-limit.refill-duration-minutes=1"
})
@DisplayName("Contrato de erro da API")
class ApiErrorContractTest {

    /** Carga sintaticamente válida como JSON, mas reprovada pela validação. Barata de processar. */
    private static final String CARGA_INVALIDA = """
            {
              "exam": null,
              "studentProfile": null,
              "gaConfig": null
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("400: o corpo segue RFC 7807 completo, com invalid_params e timestamp")
    void erroDeValidacaoSegueRfc7807() throws Exception {
        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.1");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CARGA_INVALIDA))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type")
                        .value("https://api.dynamicstudyplanner.com/errors/validation-failed"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value("One or more validation constraints were violated."))
                .andExpect(jsonPath("$.instance").value("/api/v1/optimizer/generate"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.invalid_params").isArray())
                .andExpect(jsonPath("$.invalid_params[0].name").exists())
                .andExpect(jsonPath("$.invalid_params[0].reason").exists());
    }

    @Test
    @DisplayName("429: a requisicao acima do limite recebe Too Many Requests em RFC 7807")
    void limiteDeRequisicoesDevolve429() throws Exception {
        String ip = "10.0.0.99";

        // O filtro consome um token por requisicao ao endpoint do otimizador, independentemente de
        // a carga ser valida. Usar carga invalida mantem o teste rapido: nenhuma delas chega a
        // rodar o algoritmo genetico.
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/optimizer/generate")
                            .with(req -> {
                                req.setRemoteAddr(ip);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CARGA_INVALIDA))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .with(req -> {
                            req.setRemoteAddr(ip);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CARGA_INVALIDA))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type")
                        .value("https://api.dynamicstudyplanner.com/errors/too-many-requests"))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail")
                        .value("You have exceeded the allowed number of requests. Please try again later."))
                .andExpect(jsonPath("$.instance").value("/api/v1/optimizer/generate"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("429: o balde e por IP — outro cliente nao e afetado pelo vizinho estourado")
    void oLimiteEhIsoladoPorIp() throws Exception {
        String ipQueEstoura = "10.0.1.1";
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/v1/optimizer/generate")
                    .with(req -> {
                        req.setRemoteAddr(ipQueEstoura);
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CARGA_INVALIDA));
        }

        // Um IP diferente comeca com o balde cheio: a punicao do vizinho nao vaza.
        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .with(req -> {
                            req.setRemoteAddr("10.0.1.2");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CARGA_INVALIDA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("429: o cabecalho X-Forwarded-For define o balde quando presente")
    void oCabecalhoXForwardedForDefineOBalde() throws Exception {
        // Exercita o ramo de getClientIP() que le X-Forwarded-For — o caminho realmente usado
        // atras de um balanceador de carga, e que nenhum teste percorria antes da etapa 01b.
        // O comportamento verificado aqui e o atual: o primeiro valor da lista vira a chave.
        // Se essa origem deve ou nao ser confiada e questao de seguranca (R15 do inventario),
        // nao de teste.
        String encaminhado = "203.0.113.7, 70.41.3.18";

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/optimizer/generate")
                            .header("X-Forwarded-For", encaminhado)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CARGA_INVALIDA))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .header("X-Forwarded-For", encaminhado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CARGA_INVALIDA))
                .andExpect(status().isTooManyRequests());

        // Outro valor no mesmo cabecalho e outro balde.
        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .header("X-Forwarded-For", "198.51.100.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CARGA_INVALIDA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("o limite nao se aplica a caminhos fora de /api/v1/optimizer/")
    void oLimiteNaoAlcancaOutrosCaminhos() throws Exception {
        String ip = "10.0.2.1";
        for (int i = 1; i <= 6; i++) {
            mockMvc.perform(post("/api/v1/outro-recurso")
                            .with(req -> {
                                req.setRemoteAddr(ip);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }
    }

    @Test
    @DisplayName("carga valida acima do limite tambem e barrada, antes de gastar CPU no AG")
    void cargaValidaTambemEhBarradaPeloLimite() throws Exception {
        String ip = "10.0.3.1";
        String valida = RequestPayloads.requisicaoValida();

        // Esgota o balde com requisicoes baratas...
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/optimizer/generate")
                    .with(req -> {
                        req.setRemoteAddr(ip);
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CARGA_INVALIDA));
        }

        // ...e confirma que a carga valida seguinte e recusada pelo filtro, sem iniciar o
        // processamento assincrono. E esta a protecao de CPU que o README descreve.
        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .with(req -> {
                            req.setRemoteAddr(ip);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valida))
                .andExpect(status().isTooManyRequests());
    }
}
