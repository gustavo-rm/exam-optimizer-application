package com.ia.project.dynamicstudyplanner.api.exception;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Os tratadores de erro que a porta HTTP não alcança hoje.
 *
 * <h2>Por que estes casos são testados aqui, e não por MockMvc</h2>
 *
 * Os demais códigos do contrato de erro têm teste de ponta a ponta em {@code ApiErrorContractTest},
 * {@code ApiFailureContractTest} e {@code security/ClientErrorStatusTest}. Os desta classe não têm,
 * e o motivo é o mesmo em cada caso: <b>não existe requisição HTTP capaz de provocá-los na
 * configuração atual</b>.
 *
 * <ul>
 *   <li><b>401 e 403.</b> {@code SecurityConfig} aplica {@code permitAll()} a todo
 *       {@code /api/v1/**} e à Swagger UI. Nenhuma rota sob o contrato público chega a produzir
 *       {@code AuthenticationException} ou {@code AccessDeniedException}.</li>
 *   <li><b>Tudo que depende de parâmetro de rota ou de consulta</b> —
 *       {@code ConstraintViolationException}, parâmetro obrigatório ausente e parâmetro de tipo
 *       incompatível. O único endpoint da API recebe apenas corpo, validado por
 *       {@code MethodArgumentNotValidException}; nenhuma requisição chega a produzi-las.</li>
 *   <li><b>{@code AsyncRequestTimeoutException} e o catch-all de {@code Exception}.</b> Dependem de
 *       o contêiner ou um defeito nosso dispararem — não há entrada de cliente que os produza.</li>
 * </ul>
 *
 * <p>Testar o tratador diretamente é o recorte honesto: garante que o formato RFC 7807 está certo
 * caso o caminho passe a existir, sem fingir que existe hoje. Que estes tratadores existam para
 * situações inalcançáveis está registrado como pendência P6 em
 * {@code docs/qualidade/01b-correcao-testes.md} — a decisão sobre removê-los ou tornar o caminho
 * alcançável é de estrutura e de segurança, não de teste.
 *
 * <h2>O que mudou na etapa 03d</h2>
 *
 * O {@code 422} <b>saiu</b> da lista de inalcançáveis. Até a etapa 03c a única origem de
 * {@code DomainException} em produção era {@code Exam.calculateBaseImportance}, num ramo que a
 * validação {@code @Min(1)} tornava impossível. A reclassificação do achado E3 passou duas
 * verificações de {@code StudyPlanFactory} de {@code IllegalArgumentException} para
 * {@code DomainException}, e {@code security/BusinessRuleStatusTest} prova o caminho de ponta a
 * ponta. O teste de formato continua aqui por proximidade com os demais.
 *
 * <p>Esta classe se chamava {@code GlobalExceptionHandlerTest} e instanciava um único tratador. Com
 * a divisão do achado E7 em três {@code @RestControllerAdvice}, ela passou a instanciar os três —
 * o recorte dela nunca foi "uma classe de produção", e sim "o que o MockMvc não alcança".
 */
@DisplayName("Contrato de erro: os tratadores fora do alcance da porta HTTP")
class ErrorAdviceContractTest {

    private static final String CAMINHO = "/api/v1/optimizer/generate";

    private final RequestErrorAdvice requisicoes = new RequestErrorAdvice();
    private final BusinessRuleErrorAdvice regrasDeNegocio = new BusinessRuleErrorAdvice();
    private final InfrastructureErrorAdvice infraestrutura =
            new InfrastructureErrorAdvice(new SimpleMeterRegistry(), 30);

    private MockHttpServletRequest requisicao() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", CAMINHO);
        request.setRequestURI(CAMINHO);
        return request;
    }

    /** Asserções comuns a todo corpo RFC 7807, venha de qual dos três tratadores vier. */
    private void verificaFormatoComum(ProblemDetail problema, HttpStatus esperado,
                                      String titulo, String sufixoDoTipo) {
        assertThat(problema.getStatus()).isEqualTo(esperado.value());
        assertThat(problema.getTitle()).isEqualTo(titulo);
        assertThat(problema.getType())
                .isEqualTo(URI.create("https://api.dynamicstudyplanner.com/errors/" + sufixoDoTipo));
        assertThat(problema.getInstance()).isEqualTo(URI.create(CAMINHO));
        assertThat(problema.getProperties())
                .as("todo corpo de erro carrega um timestamp")
                .containsKey("timestamp");
        assertThat(problema.getProperties().get("timestamp")).isInstanceOf(Instant.class);
    }

    @Test
    @DisplayName("422: DomainException vira Unprocessable Entity, com a mensagem de dominio no detail")
    void domainExceptionVira422() {
        String mensagem = "Total minimum study days required (375) exceeds total available days (365).";

        ResponseEntity<ProblemDetail> resposta =
                regrasDeNegocio.handleDomainException(new DomainException(mensagem), requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ProblemDetail problema = resposta.getBody();
        assertThat(problema).isNotNull();
        verificaFormatoComum(problema, HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity", "domain-rule-violation");
        assertThat(problema.getDetail())
                .as("a regra de negocio violada e informacao util para o cliente, e vai no detail")
                .isEqualTo(mensagem);
    }

    @Test
    @DisplayName("401: AuthenticationException vira Unauthorized sem repetir a causa interna")
    void authenticationExceptionVira401() {
        ResponseEntity<ProblemDetail> resposta = infraestrutura.handleAuthenticationException(
                new BadCredentialsException("senha do usuario tecnico invalida"), requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ProblemDetail problema = resposta.getBody();
        assertThat(problema).isNotNull();
        verificaFormatoComum(problema, HttpStatus.UNAUTHORIZED, "Unauthorized", "unauthorized");
        assertThat(problema.getDetail())
                .as("mensagem generica: detalhe de autenticacao nao volta para o cliente")
                .isEqualTo("Authentication is required to access this resource.")
                .doesNotContain("senha");
    }

    @Test
    @DisplayName("403: AccessDeniedException vira Forbidden sem repetir a causa interna")
    void accessDeniedExceptionVira403() {
        ResponseEntity<ProblemDetail> resposta = infraestrutura.handleAccessDeniedException(
                new AccessDeniedException("falta a role ADMIN"), requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ProblemDetail problema = resposta.getBody();
        assertThat(problema).isNotNull();
        verificaFormatoComum(problema, HttpStatus.FORBIDDEN, "Forbidden", "forbidden");
        assertThat(problema.getDetail())
                .isEqualTo("You do not have permission to access this resource.")
                .doesNotContain("ADMIN");
    }

    @Test
    @DisplayName("400: ConstraintViolationException lista cada violacao em invalid_params")
    void constraintViolationVira400ComListaDeParametros() {
        ConstraintViolation<?> violacao = mock(ConstraintViolation.class);
        Path caminho = mock(Path.class);
        when(caminho.toString()).thenReturn("gerar.totalStudyDays");
        when(violacao.getPropertyPath()).thenReturn(caminho);
        when(violacao.getMessage()).thenReturn("deve ser no maximo 365");

        ResponseEntity<ProblemDetail> resposta = requisicoes.handleConstraintViolationException(
                new ConstraintViolationException(Set.of(violacao)), requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problema = resposta.getBody();
        assertThat(problema).isNotNull();
        verificaFormatoComum(problema, HttpStatus.BAD_REQUEST, "Bad Request", "constraint-violation");
        assertThat(problema.getProperties().get("invalid_params"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(InvalidParam.class))
                .as("cada violacao vira uma entrada nomeada, para o cliente saber o que corrigir")
                .containsExactly(new InvalidParam("gerar.totalStudyDays", "deve ser no maximo 365"));
    }

    @Test
    @DisplayName("400: parametro obrigatorio ausente e nomeado em invalid_params")
    void parametroAusenteVira400() {
        // O nome do parametro e contrato publico — vem da assinatura do endpoint, nao do usuario —,
        // entao pode ir tanto para a resposta quanto para o log.
        ResponseEntity<ProblemDetail> resposta = requisicoes.handleMissingParameter(
                new MissingServletRequestParameterException("totalStudyDays", "int"), requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problema = resposta.getBody();
        assertThat(problema).isNotNull();
        verificaFormatoComum(problema, HttpStatus.BAD_REQUEST, "Bad Request", "missing-parameter");
        assertThat(problema.getProperties().get("invalid_params"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(InvalidParam.class))
                .containsExactly(new InvalidParam("totalStudyDays", "Required parameter is missing."));
    }

    @Test
    @DisplayName("400: parametro de tipo incompativel diz o tipo esperado, nunca o valor recebido")
    void tipoIncompativelDeParametroVira400SemOValor() {
        String valorDoUsuario = "VALOR_PESSOAL_DO_ALUNO";

        ResponseEntity<ProblemDetail> resposta = requisicoes.handleTypeMismatch(
                new MethodArgumentTypeMismatchException(
                        valorDoUsuario, Integer.class, "totalStudyDays", null, null),
                requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problema = resposta.getBody();
        assertThat(problema).isNotNull();
        verificaFormatoComum(problema, HttpStatus.BAD_REQUEST, "Bad Request", "type-mismatch");
        assertThat(problema.getProperties().get("invalid_params"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(InvalidParam.class))
                .as("o cliente precisa do tipo esperado para corrigir")
                .containsExactly(new InvalidParam("totalStudyDays", "Expected type: Integer."));
        assertThat(problema.getDetail())
                .as("o valor recusado e entrada do usuario e nao volta na resposta (achado S2)")
                .doesNotContain(valorDoUsuario);
    }

    @Test
    @DisplayName("400: IllegalArgumentException segue devolvendo a mensagem — agora so para defeito de chamada")
    void illegalArgumentVira400() {
        // Depois da etapa 03d este tratador NAO recebe mais o piso de dias minimos: aquilo virou
        // regra de negocio (422) e esta no teste de 422 acima. O que resta aqui e defeito de quem
        // chama — por exemplo dias disponiveis negativos, que a validacao declarativa da borda nao
        // ve quando o valor chega por um caminho interno. Ver ADR-0005.
        String mensagem = "Total available days cannot be negative (received -1).";

        ResponseEntity<ProblemDetail> resposta = requisicoes.handleIllegalArgumentException(
                new IllegalArgumentException(mensagem), requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problema = resposta.getBody();
        assertThat(problema).isNotNull();
        verificaFormatoComum(problema, HttpStatus.BAD_REQUEST, "Bad Request", "illegal-argument");
        assertThat(problema.getDetail())
                .as("a mensagem precisa ser acionavel: diz o que estava errado")
                .isEqualTo(mensagem);
    }

    @Test
    @DisplayName("408: AsyncRequestTimeoutException usa o mesmo tratador do TimeoutException")
    void asyncRequestTimeoutUsaOMesmoTratador() {
        // O tratador declara os dois tipos. O TimeoutException tem cobertura de ponta a ponta em
        // ApiFailureContractTest; o AsyncRequestTimeoutException, disparado pelo container quando o
        // proprio Servlet estoura o prazo, so e alcancavel aqui.
        for (Exception excecao : new Exception[]{
                new TimeoutException("futuro estourou"), new AsyncRequestTimeoutException()}) {

            ResponseEntity<ProblemDetail> resposta =
                    infraestrutura.handleTimeoutException(excecao, requisicao());

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
            ProblemDetail problema = resposta.getBody();
            assertThat(problema).isNotNull();
            verificaFormatoComum(problema, HttpStatus.REQUEST_TIMEOUT, "Request Timeout", "request-timeout");
            assertThat(problema.getDetail()).isEqualTo("The computation took too long and timed out.");
        }
    }

    @Test
    @DisplayName("500: o catch-all nao deixa vazar mensagem nem tipo da excecao original")
    void catchAllNaoVazaDetalheInterno() {
        String segredo = "jdbc:postgresql://interno:5432 senha=abc123";

        ResponseEntity<ProblemDetail> resposta =
                infraestrutura.handleAllUncaughtException(new IllegalStateException(segredo), requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail problema = resposta.getBody();
        assertThat(problema).isNotNull();
        verificaFormatoComum(problema, HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error", "internal-server-error");
        assertThat(problema.getDetail())
                .isEqualTo("An unexpected internal error occurred.")
                .doesNotContain(segredo);
    }
}
