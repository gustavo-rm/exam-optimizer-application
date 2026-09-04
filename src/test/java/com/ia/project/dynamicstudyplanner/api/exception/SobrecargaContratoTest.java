package com.ia.project.dynamicstudyplanner.api.exception;

import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o contrato de resposta sob sobrecarga — a correção do achado E2.
 *
 * <h2>O que estava errado</h2>
 *
 * Quando a fila do executor enchia, o {@code ThreadPoolTaskExecutor} lançava
 * {@link TaskRejectedException}. Sem tratador dedicado, ela caía no genérico e virava
 * <b>500 Internal Server Error</b>, registrado em nível ERROR como "Unexpected error occurred".
 *
 * <p>Medido na etapa 06 com 120 pedidos pesados simultâneos: <b>66 respostas 500</b> (55 % da carga)
 * e <b>99 registros de ERROR</b> para uma condição de capacidade — uma taxa de erro de 55 % num
 * painel de SLO, para um serviço que estava apenas cheio.
 *
 * <h2>Por que o cabeçalho importa tanto quanto o código</h2>
 *
 * Um 503 sem {@code Retry-After} deixa o cliente decidir quando voltar, e muitas bibliotecas
 * decidem "imediatamente" — o que amplifica a carga que causou a recusa. O cabeçalho é o que
 * transforma a recusa em <i>backpressure</i> de verdade: o sinal para desacelerar.
 */
@DisplayName("Sobrecarga: 503 com Retry-After, nunca 500")
class SobrecargaContratoTest {

    private static final String CAMINHO = "/api/v1/optimizer/generate";
    private static final int RETRY_AFTER = 30;

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final InfrastructureErrorAdvice advice =
            new InfrastructureErrorAdvice(registro, RETRY_AFTER);

    private static MockHttpServletRequest requisicao() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", CAMINHO);
        request.setRequestURI(CAMINHO);
        return request;
    }

    @Test
    @DisplayName("fila cheia devolve 503, nao 500")
    void filaCheiaDevolve503() {
        ResponseEntity<ProblemDetail> resposta = advice.handleTaskRejected(
                new TaskRejectedException("queue full"), requisicao());

        assertThat(resposta.getStatusCode())
                .as("500 diz ao balanceador que a replica esta QUEBRADA; 503 diz que esta CHEIA")
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("a resposta diz quando voltar")
    void aRespostaDizQuandoVoltar() {
        ResponseEntity<ProblemDetail> resposta = advice.handleTaskRejected(
                new TaskRejectedException("queue full"), requisicao());

        assertThat(resposta.getHeaders().getFirst("Retry-After"))
                .as("sem este cabecalho, um cliente que repete imediatamente amplifica a sobrecarga")
                .isEqualTo(String.valueOf(RETRY_AFTER));
    }

    @Test
    @DisplayName("a recusa por capacidade e contada numa metrica propria")
    void aRecusaEhContada() {
        // Sem um contador dedicado, a unica evidencia de sobrecarga seria a taxa de 5xx — que e
        // exatamente o sinal que confunde capacidade com defeito.
        advice.handleTaskRejected(new TaskRejectedException("queue full"), requisicao());
        advice.handleTaskRejected(new TaskRejectedException("queue full"), requisicao());

        assertThat(registro.get("dynamicstudyplanner.overload.rejected").counter().count())
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("estado compartilhado indisponivel tambem devolve 503 com Retry-After")
    void estadoCompartilhadoIndisponivelDevolve503() {
        // Deixar passar quando o Redis nao responde transformaria uma indisponibilidade de
        // infraestrutura em ausencia TOTAL de limite de taxa, em todas as replicas ao mesmo tempo.
        ResponseEntity<ProblemDetail> resposta = advice.handleRedisIndisponivel(
                new RedisException("connection refused"), requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resposta.getHeaders().getFirst("Retry-After")).isEqualTo("30");
    }

    @Test
    @DisplayName("o corpo segue o mesmo formato de problema das demais respostas de erro")
    void oCorpoSegueOFormatoDeProblema() {
        ResponseEntity<ProblemDetail> resposta = advice.handleTaskRejected(
                new TaskRejectedException("queue full"), requisicao());

        ProblemDetail corpo = resposta.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.getTitle()).isNotBlank();
        assertThat(corpo.getDetail()).contains("Retry-After");
        assertThat(corpo.getInstance()).hasToString(CAMINHO);
    }
}
