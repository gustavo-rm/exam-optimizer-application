package com.ia.project.dynamicstudyplanner.api.exception;

import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Tratamento centralizado de exceções para toda a API, em RFC 7807 (<i>Problem Details</i>).
 *
 * <h2>Por que existem tratadores para exceções do próprio Spring MVC</h2>
 *
 * Esta classe é um {@code @RestControllerAdvice} simples e <b>não</b> estende
 * {@code ResponseEntityExceptionHandler}. Até a etapa 02b isso significava que as exceções padrão do
 * Spring MVC para erro de cliente — corpo ilegível, verbo não suportado, tipo de mídia inválido —
 * não tinham tratador e caíam todas no {@code @ExceptionHandler(Exception.class)}, devolvendo
 * <b>500</b> e registrando a pilha completa em nível {@code ERROR}.
 *
 * <p>O diagnóstico de segurança ({@code docs/qualidade/02-diagnostico-seguranca.md}, achado S8)
 * mediu cinco cenários — tipo incompatível, JSON malformado, {@code GET}, {@code PUT} e
 * {@code DELETE} — e todos devolviam 500. Havia três consequências: o cliente era informado errado;
 * o log de ERRO recebia pilha completa a cada erro banal de cliente; e alerta por taxa de 5xx ficava
 * inútil, porque um varredor automático gerava 5xx em volume.
 *
 * <p>Optou-se por <b>tratadores explícitos</b> em vez de estender
 * {@code ResponseEntityExceptionHandler} porque a classe já declara
 * {@code handleValidationExceptions} para {@code MethodArgumentNotValidException} e a herança
 * criaria sobreposição com o método protegido de mesma responsabilidade da superclasse. Tratadores
 * explícitos mantêm um único formato de resposta e um único lugar onde a decisão é tomada.
 *
 * <h2>Regra de log desta classe</h2>
 *
 * <b>Nenhum tratador de erro de cliente registra o valor enviado pelo usuário.</b> O sistema recebe
 * dado pessoal de estudante — nome, autoavaliação de desempenho e estado psicológico — e o log é
 * JSON estruturado destinado a um agregador externo. Registrar o valor recusado significaria
 * publicar dado pessoal fora do processo (achados S2 e S3 do mesmo diagnóstico). O que se registra é
 * <b>onde</b> o erro ocorreu e <b>que tipo</b> de erro foi, nunca <b>o que</b> foi enviado.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String INVALID_PARAMS_PROPERTY = "invalid_params";

    /**
     * Handles validation errors from @Valid on RequestBody.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<InvalidParam> errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    String fieldName = error instanceof FieldError ? ((FieldError) error).getField() : error.getObjectName();
                    return new InvalidParam(fieldName, error.getDefaultMessage());
                })
                .toList();

        log.warn("Validation error on path {}: {}", request.getRequestURI(), errors);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more validation constraints were violated.");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/validation-failed"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setProperty(INVALID_PARAMS_PROPERTY, errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles validation errors from path variables or request parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<InvalidParam> errors = ex.getConstraintViolations()
                .stream()
                .map(violation -> new InvalidParam(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        log.warn("Constraint violation on path {}: {}", request.getRequestURI(), errors);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more validation constraints were violated.");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/constraint-violation"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setProperty(INVALID_PARAMS_PROPERTY, errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles standard IllegalArgumentExceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Illegal argument on path {}: {}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/illegal-argument"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles custom business logic domain exceptions.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(
            DomainException ex, HttpServletRequest request) {

        log.warn("Domain exception on path {}: {}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problemDetail.setTitle("Unprocessable Entity");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/domain-rule-violation"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problemDetail);
    }

    /**
     * Handles Spring Security Authentication Exceptions (e.g. invalid credentials).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed on path {}: {}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
        problemDetail.setTitle("Unauthorized");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/unauthorized"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    /**
     * Handles Spring Security Access Denied Exceptions (e.g. insufficient roles).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied on path {}: {}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
        problemDetail.setTitle("Forbidden");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/forbidden"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    /**
     * Handles RateLimitExceededException thrown by the RateLimitingFilter.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceededException(
            RateLimitExceededException ex, HttpServletRequest request) {

        log.warn("Rate limit exceeded on path {}: {}", request.getRequestURI(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "You have exceeded the allowed number of requests. Please try again later.");
        problemDetail.setTitle("Too Many Requests");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/too-many-requests"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problemDetail);
    }

    /**
     * Corpo da requisição ilegível: JSON malformado, ou valor de tipo incompatível com o campo.
     *
     * <p>Antes da etapa 02b esta exceção não tinha tratador e caía no catch-all, devolvendo
     * <b>500</b> e registrando a pilha completa em ERRO. A pilha do Jackson <b>repete o valor
     * recusado</b> — medido no diagnóstico como {@code Cannot deserialize value of type
     * `java.lang.Double` from String "..."} —, então cada erro de digitação do estudante publicava
     * o que ele digitou no agregador de logs (achado S2).
     *
     * <p>Agora devolve <b>400</b> e registra apenas o caminho do campo e o tipo esperado, extraídos
     * da cadeia de referências do Jackson. O valor enviado não é lido nem registrado em momento
     * algum.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String campo = extrairCaminhoDoCampo(ex);

        // Log deliberadamente sem ex.getMessage() e sem a pilha: ambos carregam o valor enviado.
        log.warn("Unreadable request body on path {} (field: {})", request.getRequestURI(), campo);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body could not be read. Check that the JSON is well-formed and that "
                        + "every field has the expected type.");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/malformed-body"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        if (campo != null) {
            // Aponta o campo para o cliente conseguir corrigir, sem repetir o valor que ele enviou.
            problemDetail.setProperty(INVALID_PARAMS_PROPERTY,
                    List.of(new InvalidParam(campo, "Malformed value or incompatible type.")));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Extrai apenas o <i>caminho</i> do campo problemático da cadeia de referências do Jackson.
     *
     * <p>Percorre {@link JsonMappingException#getPath()}, que descreve a posição estrutural
     * ({@code studentProfile.knowledgeGaps.Portugues}) sem conter o valor enviado. Devolve
     * {@code null} quando a causa não é do Jackson — JSON sintaticamente quebrado, por exemplo, em
     * que não há campo identificável.
     */
    private String extrairCaminhoDoCampo(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof JsonMappingException mappingException)) {
            return null;
        }
        String caminho = mappingException.getPath().stream()
                .map(referencia -> referencia.getFieldName() != null
                        ? referencia.getFieldName()
                        : "[" + referencia.getIndex() + "]")
                .collect(Collectors.joining("."));
        return caminho.isBlank() ? null : caminho;
    }

    /**
     * Verbo HTTP não suportado pelo endpoint. Devolve <b>405</b> com o cabeçalho {@code Allow},
     * como manda a RFC 9110, em vez do <b>500</b> que o catch-all devolvia.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warn("Method {} not supported on path {}", ex.getMethod(), request.getRequestURI());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
                "The HTTP method used is not supported by this endpoint.");
        problemDetail.setTitle("Method Not Allowed");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/method-not-allowed"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            resposta.allow(ex.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]));
        }
        return resposta.body(problemDetail);
    }

    /**
     * Tipo de mídia não suportado — por exemplo, corpo enviado como {@code text/plain} num endpoint
     * que só aceita JSON. Devolve <b>415</b>.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        log.warn("Unsupported media type on path {}: {}", request.getRequestURI(), ex.getContentType());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "The request content type is not supported. Use application/json.");
        problemDetail.setTitle("Unsupported Media Type");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/unsupported-media-type"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problemDetail);
    }

    /**
     * Parâmetro obrigatório ausente na consulta. Devolve <b>400</b> nomeando o parâmetro — o nome do
     * parâmetro é parte do contrato público, não dado do usuário.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing request parameter '{}' on path {}", ex.getParameterName(), request.getRequestURI());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "A required request parameter is missing.");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/missing-parameter"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setProperty(INVALID_PARAMS_PROPERTY,
                List.of(new InvalidParam(ex.getParameterName(), "Required parameter is missing.")));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Parâmetro com tipo incompatível. Devolve <b>400</b> registrando o nome do parâmetro e o tipo
     * esperado — nunca o valor recebido, que é entrada do usuário.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String tipoEsperado = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        log.warn("Parameter '{}' on path {} has an incompatible type (expected {})",
                ex.getName(), request.getRequestURI(), tipoEsperado);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "A request parameter has an incompatible type.");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/type-mismatch"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setProperty(INVALID_PARAMS_PROPERTY,
                List.of(new InvalidParam(ex.getName(), "Expected type: " + tipoEsperado + ".")));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Rota inexistente. Devolve <b>404</b> em vez de 500, e — importante para não virar ferramenta de
     * mapeamento — não devolve nada além do caminho que o próprio cliente já enviou.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        log.warn("No handler found for path {}", request.getRequestURI());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "The requested resource was not found.");
        problemDetail.setTitle("Not Found");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/not-found"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /**
     * Lacuna de conhecimento citando disciplina que não existe no edital enviado.
     *
     * <p>Devolve <b>400</b> nomeando as disciplinas não reconhecidas, para o cliente conseguir
     * corrigir. Registra os mesmos nomes — que vêm do edital que ele próprio enviou e são parte do
     * contrato público — e <b>nunca</b> as notas de autoavaliação associadas a elas. Ver o Javadoc de
     * {@link UnknownSubjectException} para o vazamento que esta rota substitui (achado S3).
     */
    @ExceptionHandler(UnknownSubjectException.class)
    public ResponseEntity<ProblemDetail> handleUnknownSubject(
            UnknownSubjectException ex, HttpServletRequest request) {

        log.warn("Knowledge gaps reference {} unknown subject(s) on path {}: {}",
                ex.getUnknownSubjects().size(), request.getRequestURI(), ex.getUnknownSubjects());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Every knowledge gap must reference a subject declared in the exam.");
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/unknown-subject"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setProperty(INVALID_PARAMS_PROPERTY, ex.getUnknownSubjects().stream()
                .map(nome -> new InvalidParam("studentProfile.knowledgeGaps." + nome,
                        "Subject is not declared in the exam."))
                .toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Handles timeouts that occur when the CompletableFuture or Async request exceeds its threshold.
     */
    @ExceptionHandler({TimeoutException.class, AsyncRequestTimeoutException.class})
    public ResponseEntity<ProblemDetail> handleTimeoutException(
            Exception ex, HttpServletRequest request) {

        log.error("Request timed out on path {}", request.getRequestURI(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.REQUEST_TIMEOUT, "The computation took too long and timed out.");
        problemDetail.setTitle("Request Timeout");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/request-timeout"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(problemDetail);
    }

    /**
     * Catch-all handler for any unhandled RuntimeExceptions or general Exceptions.
     * Prevents internal implementation details or stack traces from leaking to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllUncaughtException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error occurred on path {}", request.getRequestURI(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred.");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/internal-server-error"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
