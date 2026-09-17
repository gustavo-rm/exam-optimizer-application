package com.ia.project.dynamicstudyplanner.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O aluno que não tem tempo suficiente até a prova.
 *
 * <h2>Por que este cenário merece teste próprio</h2>
 *
 * É o caso realista — quem procura um otimizador de estudos normalmente está com o prazo apertado, e
 * não sobrando. O código de produção trata esse caso num ramo separado de
 * {@code StudyScheduleGenerator.prepareScheduleContext}: quando as horas disponíveis são menores que
 * as requeridas, o plano é reduzido proporcionalmente e o status vira
 * {@code WARNING_TIME_DEFICIT}. Esse ramo não era percorrido por nenhum teste.
 *
 * <p>A diferença prática para o aluno é grande: no caminho de folga ele recebe o plano ideal; aqui
 * ele recebe um plano encolhido, e o campo {@code status} é a única coisa que avisa. Se essa redução
 * quebrasse — devolvendo cronograma vazio, ou o status errado —, ele receberia silenciosamente um
 * plano que não cabe no tempo que tem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "api.rate-limit.capacity=100",
        "api.rate-limit.refill-tokens=100"
})
@DisplayName("Cenario de deficit de tempo")
class TimeDeficitScenarioTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    /**
     * Prova em 40 dias, com apenas 3 horas semanais disponíveis, contra um orçamento de 60 dias de
     * estudo. As horas requeridas superam com folga as disponíveis, o que força o ramo de déficit.
     *
     * <p>A data é relativa a hoje pelo mesmo motivo descrito em {@code RequestPayloads}: o início do
     * cronograma vem de {@code LocalDate.now()} dentro do código de produção.
     */
    private String requisicaoApertada() {
        LocalDate dataProva = LocalDate.now().plusDays(40);
        return """
                {
                  "exam": {
                    "name": "Prova Proxima",
                    "examDate": "%s",
                    "generalKnowledgeTotalScore": 30.0,
                    "generalKnowledgeSubjects": [
                      { "name": "Portugues", "questionCount": 15, "cognitiveLoad": 3 }
                    ],
                    "specificKnowledgeAxes": [
                      { "id": 1, "name": "Eixo Unico", "weight": 2.0,
                        "subjects": [ { "name": "Direito", "questionCount": 20, "cognitiveLoad": 5 } ] }
                    ]
                  },
                  "studentProfile": {
                    "name": "Aluno Sem Tempo",
                    "knowledgeGaps": { "Portugues": 3.0, "Direito": 5.0 },
                    "weeklyAvailability": { "SATURDAY": 3 }
                  },
                  "gaConfig": {
                    "totalStudyDays": 60,
                    "numGenerations": 20,
                    "populationSize": 20
                  }
                }
                """.formatted(dataProva);
    }

    @Test
    @DisplayName("o aluno sem tempo recebe WARNING_TIME_DEFICIT, e nao um plano silenciosamente cortado")
    void deficitDeTempoEhSinalizadoAoAluno() throws Exception {
        MvcResult inicial = mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requisicaoApertada()))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult resultado = mockMvc.perform(asyncDispatch(inicial))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode agenda = JSON.readTree(resultado.getResponse().getContentAsString())
                .path("scheduleResult");

        assertThat(agenda.path("status").asText())
                .as("o aviso de deficit e a unica coisa que informa o aluno de que o plano nao cabe")
                .isEqualTo("WARNING_TIME_DEFICIT");

        double requeridas = agenda.path("requiredHours").asDouble();
        double disponiveis = agenda.path("availableHours").asDouble();

        assertThat(disponiveis)
                .as("o cenario so exercita o ramo pretendido se houver mesmo deficit")
                .isLessThan(requeridas);

        assertThat(agenda.path("schedule").size())
                .as("deficit reduz o plano, nao cancela o estudo: o cronograma continua acionavel")
                .isPositive();
    }

    @Test
    @DisplayName("mesmo com deficit, o plano estrategico continua somando o orcamento pedido")
    void oPlanoEstrategicoNaoEhAlteradoPeloDeficit() throws Exception {
        MvcResult inicial = mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requisicaoApertada()))
                .andExpect(request().asyncStarted())
                .andReturn();

        JsonNode dias = JSON.readTree(mockMvc.perform(asyncDispatch(inicial))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("optimizationResult").path("plan").path("daysPerSubject");

        int soma = 0;
        for (JsonNode v : dias) {
            soma += v.asInt();
        }

        // A reducao por deficit acontece na fase tatica. A fase estrategica continua alocando o
        // orcamento inteiro — separacao que o cliente enxerga no corpo da resposta e que uma
        // mudanca acidental poderia embaralhar.
        assertThat(soma)
                .as("o plano macro segue o orcamento; quem encolhe e o cronograma diario")
                .isEqualTo(60);
        assertThat(dias.size()).isEqualTo(2);
    }
}
