package com.ia.project.dynamicstudyplanner.security;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O {@code 422} deixou de ser decorativo — achado E3.
 *
 * <h2>O que estava errado</h2>
 *
 * A etapa 02 concluiu que o tratador de {@code 422} era <b>inalcançável pela API</b>, e a etapa 03
 * encontrou a causa raiz: {@code DomainException} era lançada uma única vez no código inteiro,
 * enquanto treze {@code IllegalArgumentException} carregavam regras de negócio e viravam
 * {@code 400}. Metade do contrato de erro documentado no {@code README.md} era decorativa.
 *
 * <h2>A distinção que este teste trava</h2>
 *
 * A RFC 9110 separa as duas situações com precisão: <b>400</b> é "não consegui entender a
 * requisição"; <b>422</b> é "entendi perfeitamente, mas não posso processar". Um aluno que declara
 * um edital de 25 disciplinas e 365 dias de estudo enviou uma requisição impecável — é o edital que
 * exige mais dias do que ele tem. Devolver 400 sugere que ele digitou algo errado; devolver 422 diz
 * a verdade.
 *
 * <p>Só a etapa 03d tornou isso alcançável, reclassificando <b>duas</b> das treze exceções — não
 * todas. As outras onze continuam {@code IllegalArgumentException} de propósito, porque são defeitos
 * de quem chama e não situações em que o aluno possa estar. O critério está no ADR-0005.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "api.rate-limit.capacity=100",
        "api.rate-limit.refill-tokens=100"
})
@DisplayName("E3: regra de negocio violada devolve 422")
class BusinessRuleStatusTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Edital de 25 disciplinas de peso igual. O piso de dias mínimos cresce linearmente com o número
     * de disciplinas, então passa de 365 — que é o teto que {@code GaConfigDto} aceita. É a situação
     * medida em {@code GaEdgeCasesTest.minimumDaysFloorGrowsWithSubjectCount}, agora vista pela porta
     * HTTP.
     */
    private String editalImpossivel() {
        StringBuilder disciplinas = new StringBuilder();
        StringBuilder lacunas = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            if (i > 1) {
                disciplinas.append(",");
                lacunas.append(",");
            }
            disciplinas.append("{\"name\":\"Disciplina").append(i)
                    .append("\",\"questionCount\":10,\"cognitiveLoad\":3}");
            lacunas.append("\"Disciplina").append(i).append("\":3.0");
        }
        return """
                {"exam":{"name":"Concurso Impossivel","examDate":"%s","generalKnowledgeTotalScore":100.0,
                  "generalKnowledgeSubjects":[%s],"specificKnowledgeAxes":[]},
                 "studentProfile":{"name":"Aluno","knowledgeGaps":{%s},"weeklyAvailability":{"MONDAY":4}},
                 "gaConfig":{"totalStudyDays":365,"numGenerations":10,"populationSize":10}}
                """.formatted(LocalDate.now().plusDays(400), disciplinas, lacunas);
    }

    @Test
    @DisplayName("piso de dias minimos acima do orcamento devolve 422, nao 400")
    void pisoAcimaDoOrcamentoDevolve422() throws Exception {
        MvcResult inicial = mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editalImpossivel()))
                .andReturn();

        MvcResult resultado = inicial.getRequest().isAsyncStarted()
                ? mockMvc.perform(asyncDispatch(inicial)).andReturn()
                : inicial;

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getStatus())
                .as("""
                        A requisicao e bem formada: o cliente nao tem nada a corrigir na sintaxe.
                        O que a impede e uma regra de negocio — o edital exige mais dias do que o
                        aluno declarou ter. Isso e 422, nao 400 (RFC 9110).""")
                .isEqualTo(422);

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getContentAsString())
                .as("o corpo segue RFC 7807 e diz qual regra foi violada, com os numeros")
                .contains("\"status\":422")
                .contains("Unprocessable Entity")
                .contains("domain-rule-violation")
                .contains("Total minimum study days required");
    }

    @Test
    @DisplayName("o 400 continua sendo 400 para erro de sintaxe — a distincao nao foi perdida")
    void erroDeSintaxeContinua400() throws Exception {
        // Contraprova. Reclassificar demais seria tao errado quanto reclassificar de menos: se todo
        // erro virasse 422, o cliente perderia a informacao de que ha algo a corrigir na requisicao.
        mockMvc.perform(post("/api/v1/optimizer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exam\":null,\"studentProfile\":null,\"gaConfig\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
