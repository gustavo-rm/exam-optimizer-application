package com.ia.project.dynamicstudyplanner.api.exception;

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
 * Os demais códigos do contrato de erro têm teste de ponta a ponta em {@code ApiErrorContractTest} e
 * {@code ApiFailureContractTest}. Os três desta classe não têm, e o motivo é o mesmo em cada caso:
 * <b>não existe requisição HTTP capaz de provocá-los na configuração atual</b>.
 *
 * <ul>
 *   <li><b>422 ({@code DomainException}).</b> A única origem em todo o código de produção é
 *       {@code Exam.calculateBaseImportance}, que exige {@code totalGKQuestions <= 0}. Como
 *       {@code SubjectDto.questionCount} é validado com {@code @Min(1)}, a soma sobre uma lista não
 *       vazia é sempre positiva — e sobre uma lista vazia o ramo nem é alcançado, porque
 *       {@code generalKnowledgeSubjects.contains(subject)} é falso. O {@code 422} anunciado no
 *       README é, hoje, inalcançável pela API.</li>
 *   <li><b>401 e 403.</b> {@code SecurityConfig} aplica {@code permitAll()} a todo
 *       {@code /api/v1/**} e à Swagger UI. Nenhuma rota sob o contrato público chega a produzir
 *       {@code AuthenticationException} ou {@code AccessDeniedException}.</li>
 *   <li><b>{@code ConstraintViolationException}.</b> Só surge de validação em parâmetros de rota ou
 *       de consulta. O único endpoint recebe apenas corpo, validado por
 *       {@code MethodArgumentNotValidException}.</li>
 * </ul>
 *
 * <p>Testar o tratador diretamente é o recorte honesto: garante que o formato RFC 7807 está certo
 * caso o caminho passe a existir, sem fingir que existe hoje. Que estes tratadores existam para
 * situações inalcançáveis está registrado como pendência P6 em
 * {@code docs/qualidade/01b-correcao-testes.md} — a decisão sobre removê-los ou tornar o caminho
 * alcançável é de estrutura e de segurança, não de teste.
 */
@DisplayName("GlobalExceptionHandler: os tratadores fora do alcance da porta HTTP")
class GlobalExceptionHandlerTest {

    private static final String CAMINHO = "/api/v1/optimizer/generate";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest requisicao() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", CAMINHO);
        request.setRequestURI(CAMINHO);
        return request;
    }

    /** Asserções comuns a todo corpo RFC 7807 produzido por este tratador. */
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
        String mensagem = "General Knowledge total questions must be positive to calculate importance.";

        ResponseEntity<ProblemDetail> resposta =
                handler.handleDomainException(new DomainException(mensagem), requisicao());

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
        ResponseEntity<ProblemDetail> resposta = handler.handleAuthenticationException(
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
        ResponseEntity<ProblemDetail> resposta = handler.handleAccessDeniedException(
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

        ResponseEntity<ProblemDetail> resposta = handler.handleConstraintViolationException(
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
    @DisplayName("400: IllegalArgumentException devolve a mensagem — e por aqui que passa o piso de dias")
    void illegalArgumentVira400() {
        // Caminho realmente alcancavel em producao: StudyOptimizerService lanca
        // IllegalArgumentException quando o orcamento fica abaixo do piso de dias minimos
        // (GaEdgeCasesTest.budgetBelowMinimumDaysFloorFails prova que a situacao ocorre).
        String mensagem = "Total minimum study days required (375) exceeds total available days (365).";

        ResponseEntity<ProblemDetail> resposta =
                handler.handleIllegalArgumentException(new IllegalArgumentException(mensagem), requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problema = resposta.getBody();
        assertThat(problema).isNotNull();
        verificaFormatoComum(problema, HttpStatus.BAD_REQUEST, "Bad Request", "illegal-argument");
        assertThat(problema.getDetail())
                .as("a mensagem precisa ser acionavel: diz o piso e o teto")
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

            ResponseEntity<ProblemDetail> resposta = handler.handleTimeoutException(excecao, requisicao());

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
                handler.handleAllUncaughtException(new IllegalStateException(segredo), requisicao());

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
