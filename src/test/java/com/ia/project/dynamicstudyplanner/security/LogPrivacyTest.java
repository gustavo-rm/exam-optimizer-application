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
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
 *
 * <h2>Dois dos três achados deixaram de ter como acontecer em EOA-4b</h2>
 *
 * <b>S3</b> era a autoavaliação do aluno aparecendo na pilha quando ele errava o nome de uma
 * disciplina. O contrato que o serviço fala hoje <b>não carrega autoavaliação, nem nome, nem estado
 * psicológico</b>: {@code PlanRequest} traz identificadores, durações e datas, e é por isso que
 * {@code SinapseEvolutionContexts} teve de re-derivar a demanda em vez de inventar um valor neutro.
 * O risco foi removido na fonte, não no tratador — e {@code SinapseAdapterIsolationTest} é o que
 * impede que ele volte.
 *
 * <p><b>S1</b> era o endereço do cliente mascarado na linha de bloqueio por limite de taxa. O filtro
 * de limite existia para os endpoints de concurso, precificava o pedido por
 * {@code gaConfig} e pela contagem de disciplinas do edital, e saiu junto com eles. Nenhum endereço
 * de cliente é registrado hoje.
 *
 * <p><b>S2</b> continua valendo integralmente, e é o que este arquivo trava: o valor que o Jackson
 * recusou é entrada de quem chama e não pode chegar ao agregador, nem pela mensagem nem pela pilha.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("S2: valor recusado pelo Jackson nao vaza pelo log")
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
        String segredoDoCliente = "VALOR_PESSOAL_DO_ALUNO_4242";
        String payload = """
                {"contractVersion":"1.0",
                 "horizon":{"start":"2026-09-01","end":"2026-09-28"},
                 "availability":[],"goals":[],
                 "topics":[{"id":"a0000001-0000-4000-8000-000000000001",
                            "subjectId":"11111111-1111-4111-8111-111111111111",
                            "position":1,"effortTier":"STANDARD","estimatedMinutes":"%s"}],
                 "prerequisites":[],"history":[],"algorithmParams":{},"randomSeed":1}
                """.formatted(segredoDoCliente);

        mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        String log = textoCompletoDoLog();

        assertThat(log)
                .as("o valor enviado por quem chama nao pode chegar ao agregador de logs")
                .doesNotContain(segredoDoCliente);
        assertThat(log)
                .as("o caminho do campo e informacao de contrato, e continua util para diagnostico")
                .contains("estimatedMinutes");
        assertThat(niveisRegistrados())
                .as("erro de cliente e WARN, nao ERROR: nao deve poluir alerta de 5xx")
                .containsExactly(Level.WARN);
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
