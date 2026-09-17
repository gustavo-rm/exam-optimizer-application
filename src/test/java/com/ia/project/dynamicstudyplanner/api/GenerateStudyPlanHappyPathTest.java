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
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O fluxo de sucesso do produto, ponta a ponta pela porta HTTP real.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * Até a etapa 01b <b>nenhum teste do repositório recebia uma resposta 200</b>. Os dois testes que
 * existiam em {@code OptimizerControllerTest} enviavam cargas inválidas e eram rejeitados pela
 * validação antes de o corpo do controller executar, o que deixava
 * {@code OptimizerController.generateFullStudyPlan} e
 * {@code DynamicStudyPlannerService.generateFullStudyPlan} com <b>zero instruções cobertas</b>
 * (medição em {@code docs/qualidade/01-diagnostico-testes.md} §1.2). Todo o caminho entre receber o
 * pedido e devolver o plano — mapeamento, orquestração, execução assíncrona, agendamento e
 * serialização — podia quebrar sem que a suíte percebesse.
 *
 * <h2>O que este teste cobre que um teste de unidade não cobriria</h2>
 *
 * A cadeia inteira montada pelo Spring, e não uma reprodução dela feita à mão — os três arquivos que
 * hoje remontam a composição de produção manualmente estão listados em
 * {@code 01-diagnostico-testes.md} §2.4. Em particular, este teste exercita a fiação real do
 * {@code FitnessEvaluator} com os beans de produção, o desvio para o pool
 * {@code optimizerTaskExecutor} via {@code @Async}, o redespacho assíncrono do Servlet e a
 * serialização do {@code PlannerResponseDto} em JSON.
 *
 * <h2>Sobre o limite de requisições</h2>
 *
 * O {@code RateLimitingFilter} permite 5 requisições por minuto por IP no padrão, e esta classe faz
 * mais do que isso. O teto é elevado por propriedade para que o limite não interfira no que está
 * sendo medido aqui; o comportamento do limite em si tem teste dedicado em
 * {@code ApiErrorContractTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "api.rate-limit.capacity=100",
        "api.rate-limit.refill-tokens=100"
})
@DisplayName("Fluxo de sucesso: POST /api/v1/optimizer/generate")
class GenerateStudyPlanHappyPathTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    /**
     * Executa a requisição completa, incluindo o redespacho assíncrono, e devolve o resultado já
     * verificado como 200.
     *
     * <p>O controller devolve {@code CompletableFuture}, então o MockMvc precisa de dois passos: a
     * requisição inicial, que apenas inicia o processamento assíncrono, e o {@code asyncDispatch},
     * que entrega o resultado. Sem o segundo passo o teste inspecionaria um corpo vazio e passaria
     * sem exercitar nada — que é precisamente o modo de falha que este arquivo existe para evitar.
     */
    private JsonNode gerarPlano() throws Exception {
        MvcResult inicial = mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestPayloads.requisicaoValida()))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult finalizado = mockMvc.perform(asyncDispatch(inicial))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        return JSON.readTree(finalizado.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("devolve 200 com as tres secoes do corpo preenchidas")
    void devolve200ComOCorpoCompleto() throws Exception {
        MvcResult inicial = mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestPayloads.requisicaoValida()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(inicial))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Full study plan generated successfully."))
                .andExpect(jsonPath("$.optimizationResult").exists())
                .andExpect(jsonPath("$.optimizationResult.plan.daysPerSubject").exists())
                .andExpect(jsonPath("$.scheduleResult").exists())
                .andExpect(jsonPath("$.scheduleResult.schedule").exists());
    }

    @Test
    @DisplayName("o plano estrategico aloca exatamente o orcamento pedido, entre as tres disciplinas")
    void planoEstrategicoRespeitaOOrcamentoEOEdital() throws Exception {
        JsonNode dias = gerarPlano().path("optimizationResult").path("plan").path("daysPerSubject");

        assertThat(dias.isObject())
                .as("daysPerSubject deve chegar ao cliente como um objeto nome -> dias")
                .isTrue();

        List<String> disciplinas = new ArrayList<>();
        dias.fieldNames().forEachRemaining(disciplinas::add);

        assertThat(disciplinas)
                .as("toda disciplina do edital precisa aparecer no plano entregue ao aluno")
                .containsExactlyInAnyOrder(
                        RequestPayloads.DISCIPLINA_GK,
                        RequestPayloads.DISCIPLINA_ESP_1,
                        RequestPayloads.DISCIPLINA_ESP_2);

        int soma = 0;
        for (String disciplina : disciplinas) {
            int valor = dias.path(disciplina).asInt();
            assertThat(valor).as("dias alocados para %s", disciplina).isNotNegative();
            soma += valor;
        }

        assertThat(soma)
                .as("a soma dos dias por disciplina deve ser exatamente o totalStudyDays pedido")
                .isEqualTo(RequestPayloads.TOTAL_STUDY_DAYS);
    }

    @Test
    @DisplayName("os metadados da otimizacao chegam ao cliente com valores plausiveis")
    void metadadosDaOtimizacaoSaoSerializados() throws Exception {
        JsonNode otimizacao = gerarPlano().path("optimizationResult");

        assertThat(otimizacao.path("fitness").asDouble())
                .as("a fitness agregada e normalizada em [0,1] — docs/revisao-ag/05-fitness-function.md")
                .isBetween(0.0, 1.0);
        assertThat(otimizacao.path("generationsRun").asInt())
                .as("o numero de geracoes pedido no gaConfig deve ser o reportado")
                .isEqualTo(30);
        assertThat(otimizacao.path("executionTimeMillis").asLong())
                .as("tempo de execucao reportado nao pode ser negativo")
                .isNotNegative();
    }

    @Test
    @DisplayName("o cronograma tatico chega preenchido, com blocos coerentes com o edital")
    void cronogramaTaticoEhEntregueAoAluno() throws Exception {
        JsonNode agenda = gerarPlano().path("scheduleResult");

        assertThat(agenda.path("status").asText())
                .as("o status precisa ser um dos valores declarados em ScheduleStatus")
                .isIn("SUCCESS_IDEAL_PLAN", "SUCCESS_WITH_SURPLUS_TIME", "WARNING_TIME_DEFICIT");
        assertThat(agenda.path("requiredHours").asDouble())
                .as("horas requeridas pelo plano ideal")
                .isPositive();
        assertThat(agenda.path("availableHours").asDouble())
                .as("horas disponiveis ate a prova, derivadas da disponibilidade semanal")
                .isPositive();

        JsonNode cronograma = agenda.path("schedule");
        assertThat(cronograma.size())
                .as("o cronograma entregue ao aluno nao pode vir vazio: e o produto final")
                .isPositive();

        Set<String> disciplinasDoEdital = Set.of(
                RequestPayloads.DISCIPLINA_GK,
                RequestPayloads.DISCIPLINA_ESP_1,
                RequestPayloads.DISCIPLINA_ESP_2);

        int blocos = 0;
        Iterator<String> datas = cronograma.fieldNames();
        while (datas.hasNext()) {
            String data = datas.next();
            assertThat(data)
                    .as("as chaves do cronograma sao datas no formato ISO-8601")
                    .matches("\\d{4}-\\d{2}-\\d{2}");

            for (JsonNode bloco : cronograma.path(data)) {
                blocos++;
                assertThat(bloco.path("subjectName").asText())
                        .as("nenhum bloco pode citar disciplina fora do edital enviado")
                        .isIn(disciplinasDoEdital);
                assertThat(bloco.path("hours").asInt())
                        .as("um bloco de estudo com zero ou menos horas nao e acionavel pelo aluno")
                        .isPositive();
            }
        }

        assertThat(blocos)
                .as("o cronograma precisa conter pelo menos um bloco de estudo")
                .isPositive();
    }
}
