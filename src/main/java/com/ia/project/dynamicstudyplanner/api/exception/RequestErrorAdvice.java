package com.ia.project.dynamicstudyplanner.api.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Erros causados pelo que o cliente enviou: sintaxe, tipo, verbo, rota e campos inválidos.
 *
 * <h2>Por que o tratamento de erro está dividido em três classes</h2>
 *
 * Até a etapa 03d havia um único {@code GlobalExceptionHandler} com <b>447 linhas e 16
 * tratadores</b> — a maior classe do repositório, e ela mesma um achado do diagnóstico de estrutura
 * (E7). O tamanho não era o problema principal: era a mistura. Uma classe só decidia sobre erro de
 * sintaxe do cliente, violação de regra de negócio e falha de infraestrutura, três assuntos com
 * públicos e consequências diferentes.
 *
 * <p>A divisão segue a <b>natureza da causa</b>, e não o código HTTP:
 *
 * <ul>
 *   <li><b>{@code RequestErrorAdvice}</b> (esta) — o cliente enviou algo que a API não aceita.
 *       Sempre 4xx, sempre corrigível por quem chamou.</li>
 *   <li><b>{@link BusinessRuleErrorAdvice}</b> — a requisição está correta, mas viola uma regra de
 *       negócio. É onde vive o {@code 422}.</li>
 *   <li><b>{@link InfrastructureErrorAdvice}</b> — segurança, limite de uso, prazo e a rede de
 *       segurança do {@code 500}.</li>
 * </ul>
 *
 * <p>A ordem importa: {@link InfrastructureErrorAdvice} contém o tratador de {@code Exception}, que
 * casaria com tudo. As anotações {@code @Order} garantem que ele seja consultado por último.
 *
 * <h2>Regra de log desta classe</h2>
 *
 * <b>Nenhum tratador registra o valor enviado pelo usuário.</b> O sistema recebe dado pessoal de
 * estudante — nome, autoavaliação de desempenho e estado psicológico — e o log é JSON estruturado
 * destinado a um agregador externo. Registra-se <b>onde</b> o erro ocorreu e <b>que tipo</b> de erro
 * foi, nunca <b>o que</b> foi enviado. Os achados S2 e S3 de
 * {@code docs/qualidade/02-diagnostico-seguranca.md} descrevem os dois vazamentos que essa regra
 * fechou, e {@code security/LogPrivacyTest} os trava.
 *
 * <p>Todo erro de cliente é registrado em {@code WARN}, nunca em {@code ERROR}: erro de quem chama é
 * ocorrência esperada, e poluí-lo com nível de erro inutiliza qualquer alerta por taxa de falha.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(RequestErrorAdvice.class);

    /** Erros de validação de campo do corpo da requisição. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<InvalidParam> erros = ex.getBindingResult().getAllErrors().stream()
                .map(erro -> new InvalidParam(
                        erro instanceof FieldError campo ? campo.getField() : erro.getObjectName(),
                        erro.getDefaultMessage()))
                .toList();

        // A mensagem da restricao descreve a REGRA violada, nao o valor enviado — por isso pode ser
        // registrada. Verificado em LogPrivacyTest.
        log.warn("Validation error on path {}: {}", request.getRequestURI(), erros);

        return ResponseEntity.badRequest().body(ProblemDetails.withInvalidParams(
                HttpStatus.BAD_REQUEST, "validation-failed",
                "One or more validation constraints were violated.", request, erros));
    }

    /** Violações de restrição em parâmetros de rota ou de consulta. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<InvalidParam> erros = ex.getConstraintViolations().stream()
                .map(violacao -> new InvalidParam(
                        violacao.getPropertyPath().toString(), violacao.getMessage()))
                .toList();

        log.warn("Constraint violation on path {}: {}", request.getRequestURI(), erros);

        return ResponseEntity.badRequest().body(ProblemDetails.withInvalidParams(
                HttpStatus.BAD_REQUEST, "constraint-violation",
                "One or more validation constraints were violated.", request, erros));
    }

    /**
     * Corpo ilegível: JSON malformado, ou valor de tipo incompatível com o campo.
     *
     * <p>Registra apenas o caminho do campo, extraído da cadeia de referências do Jackson. Nem a
     * mensagem nem a pilha são lidas: ambas repetem o valor recusado, que é entrada do estudante
     * (achado S2).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String campo = extrairCaminhoDoCampo(ex);
        log.warn("Unreadable request body on path {} (field: {})", request.getRequestURI(), campo);

        String detalhe = "The request body could not be read. Check that the JSON is well-formed "
                + "and that every field has the expected type.";

        ProblemDetail problema = campo == null
                ? ProblemDetails.of(HttpStatus.BAD_REQUEST, "malformed-body", detalhe, request)
                : ProblemDetails.withInvalidParams(HttpStatus.BAD_REQUEST, "malformed-body", detalhe,
                        request, List.of(new InvalidParam(campo, "Malformed value or incompatible type.")));

        return ResponseEntity.badRequest().body(problema);
    }

    /**
     * Extrai apenas o <i>caminho</i> do campo problemático da cadeia de referências do Jackson.
     *
     * <p>{@link JsonMappingException#getPath()} descreve a posição estrutural
     * ({@code studentProfile.knowledgeGaps.Portugues}) sem conter o valor enviado. Devolve
     * {@code null} quando a causa não é do Jackson — JSON sintaticamente quebrado, por exemplo, em
     * que não há campo identificável.
     */
    private String extrairCaminhoDoCampo(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof JsonMappingException mapeamento)) {
            return null;
        }
        String caminho = mapeamento.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
        return caminho.isBlank() ? null : caminho;
    }

    /** Verbo HTTP não suportado. Devolve o cabeçalho {@code Allow}, como manda a RFC 9110. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warn("Method {} not supported on path {}", ex.getMethod(), request.getRequestURI());

        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            resposta.allow(ex.getSupportedHttpMethods().toArray(new HttpMethod[0]));
        }
        return resposta.body(ProblemDetails.of(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed",
                "The HTTP method used is not supported by this endpoint.", request));
    }

    /** Tipo de mídia não suportado — corpo enviado como algo que não é JSON. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        log.warn("Unsupported media type on path {}: {}", request.getRequestURI(), ex.getContentType());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ProblemDetails.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type",
                        "The request content type is not supported. Use application/json.", request));
    }

    /** Parâmetro obrigatório ausente. O nome do parâmetro é contrato público, não dado do usuário. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing request parameter '{}' on path {}",
                ex.getParameterName(), request.getRequestURI());

        return ResponseEntity.badRequest().body(ProblemDetails.withInvalidParams(
                HttpStatus.BAD_REQUEST, "missing-parameter",
                "A required request parameter is missing.", request,
                List.of(new InvalidParam(ex.getParameterName(), "Required parameter is missing."))));
    }

    /** Parâmetro com tipo incompatível. Registra o tipo esperado, nunca o valor recebido. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String tipoEsperado = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName() : "unknown";
        log.warn("Parameter '{}' on path {} has an incompatible type (expected {})",
                ex.getName(), request.getRequestURI(), tipoEsperado);

        return ResponseEntity.badRequest().body(ProblemDetails.withInvalidParams(
                HttpStatus.BAD_REQUEST, "type-mismatch",
                "A request parameter has an incompatible type.", request,
                List.of(new InvalidParam(ex.getName(), "Expected type: " + tipoEsperado + "."))));
    }

    /** Rota inexistente. Não revela nada além do caminho que o próprio cliente enviou. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        log.warn("No handler found for path {}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetails.of(HttpStatus.NOT_FOUND, "not-found",
                        "The requested resource was not found.", request));
    }

    /**
     * Lacuna de conhecimento citando disciplina ausente do edital.
     *
     * <p>É erro de referência, não violação de regra: o cliente apontou para algo que não existe no
     * que ele mesmo enviou, e por isso continua sendo {@code 400} e não {@code 422}. Registra os
     * nomes não reconhecidos — que vêm do edital dele e são contrato público — e <b>nunca</b> as
     * notas de autoavaliação associadas (achado S3).
     */
    @ExceptionHandler(UnknownSubjectException.class)
    public ResponseEntity<ProblemDetail> handleUnknownSubject(
            UnknownSubjectException ex, HttpServletRequest request) {

        log.warn("Knowledge gaps reference {} unknown subject(s) on path {}: {}",
                ex.getUnknownSubjects().size(), request.getRequestURI(), ex.getUnknownSubjects());

        return ResponseEntity.badRequest().body(ProblemDetails.withInvalidParams(
                HttpStatus.BAD_REQUEST, "unknown-subject",
                "Every knowledge gap must reference a subject declared in the exam.", request,
                ex.getUnknownSubjects().stream()
                        .map(nome -> new InvalidParam("studentProfile.knowledgeGaps." + nome,
                                "Subject is not declared in the exam."))
                        .toList()));
    }

    /**
     * Argumento inválido que escapou da validação declarativa.
     *
     * <p>Continua sendo {@code 400}: depois da etapa 03d, {@code IllegalArgumentException} sinaliza
     * <b>defeito de quem chama</b> e não situação de negócio — as duas regras que eram de negócio
     * foram reclassificadas para {@code DomainException} e vivem em {@link BusinessRuleErrorAdvice}.
     * O critério está no ADR-0005.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Illegal argument on path {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "illegal-argument", ex.getMessage(), request));
    }
}
