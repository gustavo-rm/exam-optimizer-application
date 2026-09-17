package com.ia.project.dynamicstudyplanner.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.support.RequestPayloads;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O fluxo assíncrono ponta a ponta: envia, recebe identificador, busca o resultado.
 *
 * <h2>Por que este caminho existe (achado E6)</h2>
 *
 * O endpoint original mantém a conexão HTTP aberta até o plano ficar pronto — 2,4 a 3,4 s no pior
 * pedido aceito, mais que isso com fila. Balanceadores e <i>gateways</i> têm prazos próprios, e
 * quando um deles estoura a otimização continua gastando CPU para uma conexão que já foi embora.
 *
 * <p>Medido na etapa 06b, pedido pesado: o envio passou de <b>1 527 ms</b> (esperar o plano) para
 * <b>24 ms</b> (receber o identificador). Sob 120 envios simultâneos, o pior tempo de resposta caiu
 * de 30,2 s para <b>0,65 s</b>.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Limite elevado, como nos demais testes que fazem varias requisicoes. Alem do proposito obvio,
// isto ISOLA o contexto: propriedades distintas produzem uma chave distinta no cache de contextos
// do Spring, entao os baldes deste teste nao sao os mesmos de OptimizerControllerTest — que, sem
// isso, passava a receber 429 no lugar do 400 que ele verifica.
@TestPropertySource(properties = {
        "api.rate-limit.capacity=100",
        "api.rate-limit.refill-tokens=100"
})
@DisplayName("Fluxo assincrono: envia, recebe identificador, busca o resultado depois")
class FluxoAssincronoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Consulta ate o trabalho terminar. O prazo generoso e de proposito: o teste roda numa maquina
     * compartilhada, e um limite apertado transformaria lentidao ocasional em reprovacao
     * intermitente — o tipo de teste que as pessoas aprendem a ignorar.
     */
    private JsonNode aguardarConclusao(String id) throws Exception {
        long limite = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
        JsonNode ultimo = null;
        while (System.nanoTime() < limite) {
            String corpo = mvc.perform(get("/api/v1/optimizer/jobs/{id}", id))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ultimo = objectMapper.readTree(corpo);
            String status = ultimo.get("status").asText();
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
                return ultimo;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("o trabalho " + id + " nao terminou em 60 s; ultimo estado: " + ultimo);
    }

    private String enviar() throws Exception {
        String corpo = mvc.perform(post("/api/v1/optimizer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestPayloads.requisicaoValida()))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asText();
    }

    @Test
    @DisplayName("o envio devolve 202 com Location apontando para a consulta")
    void oEnvioDevolve202ComLocation() throws Exception {
        String id = enviar();

        // O Location e o corpo dizem a mesma coisa de proposito: um cliente que ja desserializa
        // JSON nao deveria precisar ler cabecalho para saber o passo seguinte.
        mvc.perform(post("/api/v1/optimizer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestPayloads.requisicaoValida()))
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/api/v1/optimizer/jobs/")))
                .andExpect(jsonPath("$.statusUrl",
                        org.hamcrest.Matchers.startsWith("/api/v1/optimizer/jobs/")));

        assertThat(id).isNotBlank();
    }

    @Test
    @DisplayName("o trabalho chega a SUCCEEDED e traz o plano completo")
    void oTrabalhoChegaAoResultado() throws Exception {
        String id = enviar();

        JsonNode json = aguardarConclusao(id);

        assertThat(json.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(json.has("result")).isTrue();
        assertThat(json.get("result").get("optimizationResult")
                .get("plan").get("daysPerSubject").size()).isPositive();
        // As marcas de tempo contam a historia do trabalho, e sao o que permite medir tempo de
        // fila separado de tempo de calculo.
        assertThat(json.get("submittedAt").asText()).isNotBlank();
        assertThat(json.get("startedAt").asText()).isNotBlank();
        assertThat(json.get("finishedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("identificador desconhecido devolve 404, sem distinguir inexistente de expirado")
    void identificadorDesconhecidoDevolve404() throws Exception {
        mvc.perform(get("/api/v1/optimizer/jobs/{id}", "nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(
                        org.hamcrest.Matchers.containsString("job-not-found")))
                // Distinguir "nunca existiu" de "expirou" revelaria que um identificador existiu, e
                // nao muda nada para quem chama: nos dois casos o caminho e reenviar.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("never existed, or it has expired")));
    }

    @Test
    @DisplayName("payload invalido e recusado no envio, antes de ocupar vaga na fila")
    void payloadInvalidoEhRecusadoNoEnvio() throws Exception {
        // Importa que a validacao venha ANTES do enfileiramento: um pedido malformado nao pode
        // consumir uma das 32 vagas nem uma thread do pool.
        mvc.perform(post("/api/v1/optimizer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("um trabalho que falha vira FAILED com o motivo, nao fica preso em RUNNING")
    void trabalhoQueFalhaViraFailedComOMotivo() throws Exception {
        // Piso de dias maior que o orcamento: a regra de negocio recusa DENTRO do calculo, ja
        // depois do enfileiramento. E o caminho que testa o tratamento de erro do trabalhador.
        //
        // Deixar a excecao subir encheria o log do executor e deixaria o cliente consultando um
        // registro eternamente RUNNING — o pior dos dois mundos: sem resultado e sem explicacao.
        String corpo = mvc.perform(post("/api/v1/optimizer/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestPayloads.requisicaoValida(1)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(corpo).get("id").asText();

        JsonNode json = aguardarConclusao(id);

        assertThat(json.get("status").asText()).isEqualTo("FAILED");
        assertThat(json.has("result")).as("um trabalho que falhou nao carrega resultado").isFalse();
        // A explicacao tem que ser a MESMA que o caminho sincrono da para o mesmo pedido (422 com o
        // texto da regra violada). A primeira versao desta etapa devolvia aqui um generico
        // "could not be completed", e o cliente ficava sem saber o que corrigir.
        assertThat(json.get("error").asText())
                .contains("DomainException")
                .contains("exceeds total available days");
    }

    @Test
    @DisplayName("consultar resultado nao consome o limite de taxa")
    void consultarNaoConsomeOLimite() throws Exception {
        // O limite protege a capacidade de CALCULO. Cobrar as consultas do mesmo balde faria o
        // cliente gastar o limite dele so para descobrir se o trabalho que ja pagou terminou.
        String id = enviar();

        for (int i = 0; i < 20; i++) {
            mvc.perform(get("/api/v1/optimizer/jobs/{id}", id))
                    .andExpect(status().isOk());
        }
    }
}
