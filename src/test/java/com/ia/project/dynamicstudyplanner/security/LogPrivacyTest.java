package com.ia.project.dynamicstudyplanner.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão dos achados S1, S2 e S3: dado pessoal de estudante não pode sair do processo pelo log.
 *
 * <h2>Por que estes testes olham o log, e não a resposta</h2>
 *
 * O corpo da resposta já era seguro antes desta etapa — a etapa 01b travou que o {@code 500} não
 * vaza detalhe interno. O canal que vazava era outro: o log é JSON estruturado com
 * {@code LogstashEncoder}, explicitamente preparado para ingestão em ELK ou Datadog. O que entra
 * nele <b>sai do processo</b> e é indexado e retido por um sistema de terceiros.
 *
 * <p>Estes testes anexam um coletor ao logger do <b>pacote</b> {@code api.exception} e inspecionam
 * a mensagem formatada <b>e</b> a pilha — porque era pela pilha que o vazamento acontecia.
 *
 * <p>O alvo é o pacote, e não uma classe, desde a etapa 03d: o tratamento de erro passou de um
 * {@code GlobalExceptionHandler} único para três {@code @RestControllerAdvice} separados por
 * natureza da causa. Como o Logback propaga cada evento para o logger pai, um coletor no pacote
 * recebe os três. Isso é mais do que conveniência: a regra de privacidade vale para <b>qualquer</b>
 * tratador de erro, inclusive um que venha a ser criado depois — apontar para uma classe deixaria o
 * tratador novo fora da rede.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "api.rate-limit.capacity=2",
        "api.rate-limit.refill-tokens=2"
})
@DisplayName("S1, S2, S3: dado pessoal nao vaza pelo log")
class LogPrivacyTest {

    /** Logger do pacote: recebe, por propagacao, os eventos dos tres tratadores de erro. */
    private static final String LOGGER_ALVO = "com.ia.project.dynamicstudyplanner.api.exception";

    @Autowired
    private MockMvc mockMvc;

    private Logger logger;
    private ListAppender<ILoggingEvent> coletor;

    @BeforeEach
    void anexarColetor() {
        logger = (Logger) LoggerFactory.getLogger(LOGGER_ALVO);
        coletor = new ListAppender<>();
        coletor.start();
        logger.addAppender(coletor);
        logger.setLevel(Level.TRACE);
    }

    @AfterEach
    void removerColetor() {
        logger.detachAppender(coletor);
        coletor.stop();
    }

    /** Todo o texto que este evento de log levaria ao agregador: mensagem formatada mais pilha. */
    private String textoCompletoDoLog() {
        StringBuilder tudo = new StringBuilder();
        for (ILoggingEvent evento : coletor.list) {
            tudo.append(evento.getFormattedMessage()).append('\n');
            var proxy = evento.getThrowableProxy();
            while (proxy != null) {
                tudo.append(proxy.getClassName()).append(' ').append(proxy.getMessage()).append('\n');
                for (var linha : proxy.getStackTraceElementProxyArray()) {
                    tudo.append(linha.getSTEAsString()).append('\n');
                }
                proxy = proxy.getCause();
            }
        }
        return tudo.toString();
    }

    private List<Level> niveisRegistrados() {
        return coletor.list.stream().map(ILoggingEvent::getLevel).toList();
    }

    @Test
    @DisplayName("S2: o valor recusado pelo Jackson nao aparece no log")
    void valorRecusadoNaoVaiParaOLog() throws Exception {
        String segredoDoAluno = "VALOR_PESSOAL_DO_ALUNO_4242";
        String payload = """
                { "exam": {"name":"C","examDate":"2030-01-01","generalKnowledgeTotalScore":10,
                  "generalKnowledgeSubjects":[],"specificKnowledgeAxes":[]},
                  "studentProfile": {"name":"Maria Silva","knowledgeGaps":{"X":"%s"},
                  "weeklyAvailability":{"MONDAY":3}},
                  "gaConfig": {"totalStudyDays":100,"numGenerations":30,"populationSize":20} }
                """.formatted(segredoDoAluno);

        mockMvc.perform(post("/api/v1/optimizer/generate")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        String log = textoCompletoDoLog();

        assertThat(log)
                .as("o valor enviado pelo aluno nao pode chegar ao agregador de logs")
                .doesNotContain(segredoDoAluno);
        assertThat(log)
                .as("o nome do aluno tambem nao")
                .doesNotContain("Maria Silva");
        assertThat(log)
                .as("o caminho do campo e informacao de contrato, e continua util para diagnostico")
                .contains("knowledgeGaps.X");
        assertThat(niveisRegistrados())
                .as("erro de cliente e WARN, nao ERROR: nao deve poluir alerta de 5xx")
                .containsExactly(Level.WARN);
    }

    @Test
    @DisplayName("S3: a autoavaliacao do aluno nao aparece no log quando ele erra o nome da disciplina")
    void autoavaliacaoNaoVaiParaOLog() throws Exception {
        // Duas disciplinas fora do edital: e o cenario que antes produzia
        // "Duplicate key null (attempted merging values 4.5 and 2.0)" na pilha, em nivel ERROR.
        String payload = """
                { "exam": {"name":"C","examDate":"2030-01-01","generalKnowledgeTotalScore":10,
                  "generalKnowledgeSubjects":[{"name":"Portugues","questionCount":10,"cognitiveLoad":2}],
                  "specificKnowledgeAxes":[]},
                  "studentProfile": {"name":"Maria Silva","knowledgeGaps":{"Portuges":4.5,"Matematica":2.0},
                  "weeklyAvailability":{"MONDAY":3}},
                  "gaConfig": {"totalStudyDays":100,"numGenerations":30,"populationSize":20} }
                """;

        mockMvc.perform(post("/api/v1/optimizer/generate")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        String log = textoCompletoDoLog();

        assertThat(log)
                .as("as notas de autoavaliacao sao o dado mais sensivel do payload")
                .doesNotContain("4.5")
                .doesNotContain("2.0")
                .doesNotContain("Duplicate key")
                .doesNotContain("Maria Silva");
        assertThat(log)
                .as("os nomes nao reconhecidos vem do edital que o proprio cliente enviou "
                        + "e sao o que ele precisa corrigir")
                .contains("Portuges");
        assertThat(niveisRegistrados())
                .as("erro do cliente e WARN, nao ERROR")
                .containsExactly(Level.WARN);
    }

    @Test
    @DisplayName("S1: o IP registrado no bloqueio por limite vem mascarado")
    void ipNoBloqueioVemMascarado() throws Exception {
        String ip = "203.0.113.77";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/optimizer/generate")
                    .with(req -> { req.setRemoteAddr(ip); return req; })
                    .contentType(MediaType.APPLICATION_JSON).content("{}"));
        }

        String log = textoCompletoDoLog();

        assertThat(log)
                .as("o endereco completo identifica o assinante e nao deve sair do processo")
                .doesNotContain(ip);
        assertThat(log)
                .as("a granularidade de rede basta para reconhecer abuso, que e a finalidade")
                .contains("203.0.113.x");
    }

    @Test
    @DisplayName("o catch-all continua existindo para falha genuinamente inesperada")
    void oCatchAllContinuaCobrindoOInesperado() throws Exception {
        // Nao ha como provocar uma falha inesperada por requisicao valida sem dubles, entao este
        // teste apenas fixa que o tratador segue registrado e nao foi removido junto com S8.
        StringWriter sw = new StringWriter();
        new RuntimeException("marcador").printStackTrace(new PrintWriter(sw));

        assertThat(com.ia.project.dynamicstudyplanner.api.exception.InfrastructureErrorAdvice.class
                .getDeclaredMethods())
                .as("o catch-all e a rede de seguranca do 500 e precisa continuar existindo")
                .anyMatch(m -> m.getName().equals("handleAllUncaughtException"));
    }
}
