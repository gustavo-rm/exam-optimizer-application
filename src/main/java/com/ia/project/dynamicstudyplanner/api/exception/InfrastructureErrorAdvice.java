package com.ia.project.dynamicstudyplanner.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.TimeoutException;

/**
 * Falhas que não vêm do conteúdo da requisição nem de uma regra de negócio: segurança, limite de
 * uso, prazo esgotado e a rede de segurança do {@code 500}.
 *
 * <h2>Por que esta classe é consultada por último</h2>
 *
 * Ela declara {@code @ExceptionHandler(Exception.class)}, que casa com <b>qualquer</b> exceção. Num
 * único {@code @RestControllerAdvice} isso não seria problema — o Spring escolhe o tratador de tipo
 * mais específico dentro da mesma classe. Entre classes diferentes, porém, quem decide é a ordem:
 * o Spring percorre os <i>advices</i> na ordem de {@code @Order} e usa o primeiro que tenha um
 * tratador aplicável. Se esta classe fosse consultada antes de {@link RequestErrorAdvice}, o
 * catch-all engoliria todo erro de cliente e devolveria {@code 500}.
 *
 * <p>Daí o {@link Ordered#LOWEST_PRECEDENCE}: {@code RequestErrorAdvice} (mais alta) e
 * {@link BusinessRuleErrorAdvice} (em seguida) são consultados primeiro; esta é o último recurso.
 * {@code api/exception/AdviceOrderTest} trava essa ordem, porque invertê-la por acidente é uma
 * regressão silenciosa: tudo continua compilando e passando a devolver 500.
 *
 * <h2>Nível de log: aqui, sim, {@code ERROR}</h2>
 *
 * É a diferença desta classe para as outras duas. Erro de cliente é ocorrência esperada e vai em
 * {@code WARN}; um {@code 500} ou um estouro de prazo é defeito nosso, e é exatamente o que um
 * alerta por taxa de erro deve pegar. Foi para preservar esse sinal que a etapa 02b tirou os erros
 * de cliente do catch-all: enquanto eles caíam aqui, qualquer varredor automático de rotas gerava
 * 5xx em volume e tornava o alerta inútil (achado S8).
 *
 * <p>As respostas de {@code 401} e {@code 403} são deliberadamente genéricas — dizem que falta
 * autenticação ou permissão, jamais <i>por quê</i>. Detalhar (usuário inexistente contra senha
 * errada, recurso ausente contra recurso proibido) transforma a resposta de erro em oráculo de
 * enumeração.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class InfrastructureErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureErrorAdvice.class);

    private final Counter recusasPorCapacidade;
    private final int retryAfterSegundos;

    public InfrastructureErrorAdvice(MeterRegistry registry,
                                     @Value("${api.overload.retry-after-seconds:30}")
                                     int retryAfterSegundos) {
        this.retryAfterSegundos = retryAfterSegundos;
        this.recusasPorCapacidade = Counter
                .builder("dynamicstudyplanner.overload.rejected")
                .description("Requisicoes recusadas por capacidade esgotada (fila do executor cheia)")
                .register(registry);
    }

    /**
     * Fila do executor cheia. Devolve <b>503</b> com {@code Retry-After} — a correção do achado E2.
     *
     * <h2>O que estava errado</h2>
     *
     * O {@code ThreadPoolTaskExecutor} tem fila limitada e política de rejeição {@code AbortPolicy}:
     * quando a fila enche, ele lança {@link TaskRejectedException}. <b>Rejeitar é o comportamento
     * certo</b> — é o que impede o processo de aceitar trabalho até morrer.
     *
     * <p>O problema era como a rejeição chegava ao cliente. Sem tratador dedicado, ela caía no
     * tratador genérico e virava <b>500 Internal Server Error</b>, registrado em nível ERROR como
     * "Unexpected error occurred". Medido na etapa 06, com 120 pedidos pesados simultâneos:
     * <b>66 respostas 500</b> e <b>99 registros de ERROR</b> para uma condição de capacidade.
     *
     * <h2>Por que 500 e 503 não são a mesma coisa</h2>
     *
     * <ul>
     *   <li><b>Para o balanceador:</b> 500 diz "esta réplica está quebrada" — ele pode tirá-la do
     *       rodízio, concentrando a carga nas que sobraram e derrubando-as em cascata. 503 com
     *       {@code Retry-After} diz "esta réplica está cheia", que é a informação verdadeira.</li>
     *   <li><b>Para o cliente:</b> muitas bibliotecas repetem automaticamente em 5xx sem recuo,
     *       amplificando exatamente a carga que causou a rejeição. {@code Retry-After} diz quando
     *       voltar.</li>
     *   <li><b>Para quem opera:</b> 99 registros de ERROR fazem um painel de SLO ler taxa de erro de
     *       55 % e acordar alguém para procurar um defeito que não existe. WARN mais um contador
     *       dedicado ({@code dynamicstudyplanner.overload.rejected}) dizem a verdade: cheguei ao
     *       limite.</li>
     * </ul>
     */
    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ProblemDetail> handleTaskRejected(
            TaskRejectedException ex, HttpServletRequest request) {
        recusasPorCapacidade.increment();
        // WARN, nao ERROR: capacidade esgotada e uma condicao de carga esperada, nao um defeito.
        log.warn("Capacity exhausted on path {}: optimizer queue is full, refusing with 503",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", String.valueOf(retryAfterSegundos))
                .body(ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "service-overloaded",
                        "The service is at capacity and cannot accept new work right now. "
                                + "Retry after the number of seconds in the Retry-After header.",
                        request));
    }

    /**
     * Redis inalcançável. Devolve <b>503</b> com {@code Retry-After}.
     *
     * <p>O limite de taxa é um controle de segurança (etapa 02) e, no modo compartilhado, depende do
     * Redis. Deixar a requisição passar quando o Redis não responde transformaria uma
     * indisponibilidade da infraestrutura em <b>ausência total de limite em todas as réplicas ao
     * mesmo tempo</b> — exatamente a janela que um abuso espera. Recusar é a escolha segura, e o
     * significado para o cliente é o mesmo da sobrecarga: não consigo te atender agora.
     */
    @ExceptionHandler(RedisException.class)
    public ResponseEntity<ProblemDetail> handleRedisIndisponivel(
            RedisException ex, HttpServletRequest request) {
        log.error("Shared state unavailable on path {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", String.valueOf(retryAfterSegundos))
                .body(ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable",
                        "A dependency required to serve this request is unavailable. "
                                + "Retry after the number of seconds in the Retry-After header.",
                        request));
    }

    /** Autenticação ausente ou inválida. Devolve <b>401</b> sem dizer qual das duas. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed on path {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetails.of(HttpStatus.UNAUTHORIZED, "unauthorized",
                        "Authentication is required to access this resource.", request));
    }

    /** Autenticado, mas sem permissão. Devolve <b>403</b> sem revelar o que falta. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied on path {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ProblemDetails.of(HttpStatus.FORBIDDEN, "forbidden",
                        "You do not have permission to access this resource.", request));
    }

    /**
     * Limite de requisições estourado, sinalizado pelo {@code RateLimitingFilter}. Devolve
     * <b>429</b>.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceededException(
            RateLimitExceededException ex, HttpServletRequest request) {

        log.warn("Rate limit exceeded on path {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ProblemDetails.of(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests",
                        "You have exceeded the allowed number of requests. Please try again later.",
                        request));
    }

    /**
     * Prazo esgotado no cálculo assíncrono. Devolve <b>408</b>.
     *
     * <p>Vai em {@code ERROR} com a pilha: o algoritmo genético demorar mais que o limite é sintoma
     * de defeito nosso — parâmetros mal dimensionados ou entrada patológica —, não erro de quem
     * chamou.
     */
    @ExceptionHandler({TimeoutException.class, AsyncRequestTimeoutException.class})
    public ResponseEntity<ProblemDetail> handleTimeoutException(
            Exception ex, HttpServletRequest request) {

        log.error("Request timed out on path {}", request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(ProblemDetails.of(HttpStatus.REQUEST_TIMEOUT, "request-timeout",
                        "The computation took too long and timed out.", request));
    }

    /**
     * Rede de segurança para qualquer exceção sem tratador próprio. Devolve <b>500</b>.
     *
     * <p>A pilha vai para o log e <b>nada dela</b> vai para a resposta: nome de classe, caminho de
     * arquivo e trecho de consulta em corpo de erro são material de reconhecimento para quem estiver
     * sondando a API. O cliente recebe uma frase fixa.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllUncaughtException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error occurred on path {}", request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error",
                        "An unexpected internal error occurred.", request));
    }
}
