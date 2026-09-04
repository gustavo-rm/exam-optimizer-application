package com.ia.project.dynamicstudyplanner.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trava o contrato HTTP público contra um retrato versionado.
 *
 * <h2>Qual contrato este arquivo protege</h2>
 *
 * O diagnóstico da etapa 01 §4.1 concluiu que <b>não há contrato entre serviços a proteger</b> —
 * o repositório tem um serviço só, sem nenhuma chamada de rede saindo dele. O contrato que existe e
 * estava desprotegido é outro: o <b>contrato HTTP público</b>, consumido por clientes que este
 * repositório não controla.
 *
 * <p>Ele estava desprotegido em dois sentidos. Primeiro, não havia especificação versionada: a
 * descrição da API só existia em tempo de execução, gerada pelo springdoc, de modo que uma quebra de
 * compatibilidade não aparecia em nenhum <i>diff</i>. Segundo, nenhum teste inspecionava o corpo de
 * uma resposta de sucesso.
 *
 * <h2>Como funciona um teste de retrato</h2>
 *
 * Um <b>teste de retrato</b> (<i>snapshot test</i>) compara a saída atual com um arquivo commitado
 * que registra a saída aceita. Ele não julga se a mudança é boa; ele torna a mudança <b>visível</b>.
 * Quando este teste falha, o autor tem duas saídas legítimas: reverter a alteração acidental, ou
 * aceitar a mudança de contrato de propósito e atualizar o retrato no mesmo commit — momento em que
 * o revisor passa a ver, no <i>diff</i>, exatamente qual campo do contrato mudou.
 *
 * <p>O retrato fica em {@code src/test/resources/contract/openapi-snapshot.json}, com chaves
 * ordenadas para que a comparação não dependa da ordem em que o springdoc monta o documento.
 *
 * <h2>Nota sobre o defeito que este teste encontrou</h2>
 *
 * Ao ser escrito, este teste revelou que {@code GET /v3/api-docs} devolvia <b>500</b>: o springdoc
 * 2.5.0 era binariamente incompatível com o Spring Framework 6.2 que vem no Spring Boot 3.5.5
 * ({@code NoSuchMethodError} em {@code ControllerAdviceBean.<init>}). Toda a documentação da API
 * anunciada no README — Swagger UI inclusive — estava fora do ar. A correção e a justificativa estão
 * em {@code docs/qualidade/01b-correcao-testes.md}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Contrato HTTP publico")
class OpenApiContractTest {

    private static final String RETRATO = "contract/openapi-snapshot.json";

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired
    private MockMvc mockMvc;

    private String normalizar(JsonNode no) throws Exception {
        // A ordenação por chave torna a comparação insensível à ordem em que o springdoc emite os
        // campos, que não faz parte do contrato.
        return JSON.writeValueAsString(JSON.treeToValue(no, java.util.TreeMap.class));
    }

    @Test
    @DisplayName("GET /v3/api-docs responde 200 — a documentacao da API precisa estar no ar")
    void aDocumentacaoDaApiEstaNoAr() throws Exception {
        // Asserção deliberadamente separada da comparação de retrato: se a documentação voltar a
        // quebrar por incompatibilidade de dependência, a mensagem de falha aponta a causa em vez
        // de exibir um diff gigante contra um corpo de erro.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("o contrato publicado e identico ao retrato versionado")
    void oContratoNaoMudouSemQueAlguemPercebesse() throws Exception {
        String corpo = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String atual = normalizar(JSON.readTree(corpo));
        String esperado = normalizar(JSON.readTree(
                new String(getClass().getClassLoader().getResourceAsStream(RETRATO).readAllBytes(),
                        StandardCharsets.UTF_8)));

        if (!atual.equals(esperado)) {
            // Deixa o contrato atual em disco para que a atualização do retrato seja uma cópia,
            // e não uma reconstrução manual sujeita a erro.
            Path saida = Path.of("target", "openapi-atual.json");
            Files.createDirectories(saida.getParent());
            Files.writeString(saida, atual, StandardCharsets.UTF_8);
        }

        assertThat(atual)
                .as("""
                        O contrato HTTP publico mudou em relacao a src/test/resources/contract/openapi-snapshot.json.

                        Se a mudanca foi intencional, copie target/openapi-atual.json por cima do retrato
                        e inclua a alteracao NO MESMO COMMIT, para que o revisor veja no diff qual campo
                        do contrato mudou.

                        Se nao foi intencional, esta falha acabou de evitar uma quebra de compatibilidade
                        com clientes que este repositorio nao controla.""")
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("o retrato cobre o endpoint e os esquemas que o cliente precisa conhecer")
    void oRetratoCobreOQueImporta() throws Exception {
        JsonNode spec = JSON.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString());

        assertThat(spec.path("openapi").asText())
                .as("versao da especificacao OpenAPI")
                .startsWith("3.");

        // Tres caminhos desde a etapa 06b: o sincrono original, que continua sendo o contrato
        // publicado, mais o par do fluxo assincrono (achado E6). O sincrono NAO foi substituido —
        // troca-lo quebraria todo cliente existente de uma vez.
        assertThat(spec.path("paths").fieldNames())
                .toIterable()
                .as("os endpoints de negocio precisam estar documentados")
                .containsExactlyInAnyOrder(
                        "/api/v1/optimizer/generate",
                        "/api/v1/optimizer/jobs",
                        "/api/v1/optimizer/jobs/{id}");

        assertThat(spec.path("paths").path("/api/v1/optimizer/generate").fieldNames())
                .toIterable()
                .as("o endpoint sincrono aceita apenas POST")
                .containsExactly("post");

        assertThat(spec.path("paths").path("/api/v1/optimizer/jobs").fieldNames())
                .toIterable()
                .as("enviar um trabalho e POST")
                .containsExactly("post");

        assertThat(spec.path("paths").path("/api/v1/optimizer/jobs/{id}").fieldNames())
                .toIterable()
                .as("consultar um trabalho e GET — e por ser GET nao consome o limite de taxa")
                .containsExactly("get");

        JsonNode esquemas = spec.path("components").path("schemas");
        assertThat(esquemas.fieldNames()).toIterable()
                .as("todo DTO do contrato de entrada e de saida precisa aparecer no documento")
                .contains("OptimizationRequest", "ExamDto", "SubjectDto", "ThematicAxisDto",
                        "StudentProfileDto", "StudentStateDto", "GaConfigDto",
                        "PlannerResponseDto", "OptimizationResultDto", "StudyPlanDto",
                        "ScheduleResultDto", "StudyBlockDto", "ProblemDetail");

        JsonNode respostas = spec.path("paths").path("/api/v1/optimizer/generate")
                .path("post").path("responses");
        assertThat(respostas.fieldNames()).toIterable()
                .as("os codigos de resposta anunciados ao cliente")
                .contains("200", "400", "408", "422", "500");
    }

    @Test
    @DisplayName("os limites de validacao fazem parte do contrato e estao publicados")
    void osLimitesDeValidacaoEstaoNoContrato() throws Exception {
        JsonNode esquemas = JSON.readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andReturn().getResponse().getContentAsString())
                .path("components").path("schemas");

        // Estes tetos existem para proteger CPU e memoria (README, secao Security). Se alguem
        // afrouxa um deles, a mudanca precisa ser visivel: e uma alteracao de contrato E de
        // superficie de ataque ao mesmo tempo.
        JsonNode gaConfig = esquemas.path("GaConfigDto").path("properties");
        assertThat(gaConfig.path("totalStudyDays").path("maximum").asInt()).isEqualTo(365);
        assertThat(gaConfig.path("numGenerations").path("maximum").asInt()).isEqualTo(1000);
        assertThat(gaConfig.path("populationSize").path("maximum").asInt()).isEqualTo(500);

        JsonNode subject = esquemas.path("SubjectDto").path("properties");
        assertThat(subject.path("questionCount").path("maximum").asInt()).isEqualTo(500);
        assertThat(subject.path("cognitiveLoad").path("maximum").asInt()).isEqualTo(5);
    }
}
