package com.ia.project.dynamicstudyplanner.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Monta os corpos de erro em RFC 7807 (<i>Problem Details for HTTP APIs</i>).
 *
 * <h2>Por que esta classe existe</h2>
 *
 * Cada tratador repetia as mesmas cinco linhas — {@code forStatusAndDetail}, {@code setTitle},
 * {@code setType}, {@code setInstance} e o carimbo de tempo — dezesseis vezes. Além do volume, a
 * repetição deixava o formato do erro sem dono: nada garantia que um tratador novo carimbasse o
 * mesmo conjunto de campos, e o <b>timestamp</b> ou o {@code instance} podiam ser esquecidos sem que
 * nada falhasse.
 *
 * <p>Com uma única montagem, o formato do contrato de erro passa a ter um lugar só. Acrescentar um
 * campo ao corpo — por exemplo um identificador de correlação — passa a ser uma edição em vez de
 * dezesseis.
 *
 * <h2>Convenção dos tipos</h2>
 *
 * O campo {@code type} da RFC 7807 é uma URI que identifica a <i>categoria</i> do erro, e é o que um
 * cliente deve inspecionar programaticamente — nunca o texto de {@code detail}, que é para humanos.
 * Todos ficam sob {@code https://api.dynamicstudyplanner.com/errors/}.
 */
public final class ProblemDetails {

    private static final String BASE_TYPE = "https://api.dynamicstudyplanner.com/errors/";
    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String INVALID_PARAMS_PROPERTY = "invalid_params";

    private ProblemDetails() {
    }

    /**
     * Monta um corpo de erro completo.
     *
     * @param status  código HTTP; também define o {@code title}, a partir da própria razão do status
     * @param typeSlug   último segmento da URI de tipo, por exemplo {@code "malformed-body"}
     * @param detail  explicação legível por humanos; <b>nunca</b> deve conter dado enviado pelo
     *                usuário — ver a regra de log em {@link RequestErrorAdvice}
     * @param request a requisição, de onde sai o {@code instance}
     */
    public static ProblemDetail of(HttpStatus status, String typeSlug, String detail,
                                   HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detail);
        problema.setTitle(status.getReasonPhrase());
        problema.setType(URI.create(BASE_TYPE + typeSlug));
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return problema;
    }

    /**
     * Igual a {@link #of}, mais a lista de parâmetros inválidos.
     *
     * <p>O nome do campo é {@code invalid_params}, como recomenda a RFC 7807 para extensões que
     * apontam campos específicos. Cada entrada nomeia o campo e diz o que ele violou — <b>sem
     * repetir o valor enviado</b>, que é dado pessoal do estudante.
     */
    public static ProblemDetail withInvalidParams(HttpStatus status, String typeSlug, String detail,
                                                  HttpServletRequest request,
                                                  List<InvalidParam> invalidParams) {
        ProblemDetail problema = of(status, typeSlug, detail, request);
        problema.setProperty(INVALID_PARAMS_PROPERTY, invalidParams);
        return problema;
    }
}
