package com.ia.project.dynamicstudyplanner.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Cargas JSON para o endpoint {@code POST /plans}.
 *
 * <h2>O corpo vem do documento de referência, e não de um literal escrito aqui</h2>
 *
 * {@code src/test/resources/contract/plan-request-v1.0.json} é mantido byte a byte igual à cópia do
 * {@code sinapse-platform}. Uma requisição escrita à mão neste arquivo poderia descrever um
 * <i>payload</i> que a plataforma nunca envia, e os testes de contorno passariam a proteger uma
 * forma inventada.
 *
 * <p>Até EOA-4b esta classe montava um edital de concurso para
 * o endpoint síncrono do caminho de concurso. Ele saiu; o que os testes de erro, de privacidade
 * e de observabilidade precisam é um corpo válido para o endpoint que ficou.
 */
public final class RequestPayloads {

    private static final String GOLDEN = "contract/plan-request-v1.0.json";

    private static final ObjectMapper JSON = new ObjectMapper();

    private RequestPayloads() {
    }

    /** O documento de referência, exatamente como está em disco. */
    public static String requisicaoValida() {
        try (InputStream stream = new ClassPathResource(GOLDEN).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("nao foi possivel ler " + GOLDEN, e);
        }
    }

    /**
     * O mesmo documento, pedindo um motor por {@code algorithmParams.engine}.
     *
     * @param engine o identificador do motor, como {@code PlanEngineSelector} o lê
     * @return o corpo, com o parâmetro acrescentado
     */
    public static String requisicaoValida(String engine) {
        try {
            ObjectNode raiz = (ObjectNode) JSON.readTree(requisicaoValida());
            JsonNode parametros = raiz.path("algorithmParams");
            ObjectNode destino = parametros.isObject()
                    ? (ObjectNode) parametros
                    : raiz.putObject("algorithmParams");
            destino.put("engine", engine);
            return JSON.writeValueAsString(raiz);
        } catch (IOException e) {
            throw new UncheckedIOException("nao foi possivel montar o corpo", e);
        }
    }
}
