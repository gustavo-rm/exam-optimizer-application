package com.ia.project.dynamicstudyplanner.plan;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * As instâncias do catálogo real copiadas da plataforma não carregam mais as três chaves que o Core
 * ignora, e o pedido exatamente como está no arquivo é aceito pela borda do Core.
 *
 * <h2>De onde vem</h2>
 *
 * A plataforma parou de enviar {@code generations}, {@code population-size} e {@code mutation-rate}
 * (decisão SP-8-3b do dono, opção A, 20/10/2026) e regenerou as instâncias com
 * {@code algorithmParams} vazio. O EOA-14 copiou as novas e comparou o conteúdo das respostas com as
 * antigas, três motores × sementes 1 a 10; este teste guarda só o que tem de continuar verdadeiro
 * depois da cópia, sem depender de relógio nem de máquina.
 *
 * <h2>O que não está aqui</h2>
 *
 * O número de sessões de cada plano não é fixado: ele depende do alocador (K4, decisão em aberto),
 * e um teste sobre as chaves do pedido não deve quebrar quando aquilo mudar. Os reprodutores de
 * {@code instances/reproducers/} ficam de fora de propósito: foram derivados das instâncias antigas,
 * ainda carregam as três chaves e não se editam.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("Instancias do catalogo real sem as chaves que o Core ignora")
class InstanciasSemChavesIgnoradasTest {

    private static final String RESOURCE = "instances/plan-request-catalogo-real-%s.json";

    private static final List<String> CHAVES_IGNORADAS =
            List.of("generations", "population-size", "mutation-rate");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Logger logger;
    private ListAppender<ILoggingEvent> coletor;

    @BeforeEach
    void anexarColetor() {
        logger = (Logger) LoggerFactory.getLogger(AlgorithmParamsLog.class);
        coletor = new ListAppender<>();
        coletor.start();
        logger.addAppender(coletor);
    }

    @AfterEach
    void removerColetor() {
        logger.detachAppender(coletor);
        coletor.stop();
    }

    private static byte[] instancia(String perfil) throws IOException {
        try (InputStream stream = new ClassPathResource(String.format(RESOURCE, perfil)).getInputStream()) {
            return stream.readAllBytes();
        }
    }

    private List<ILoggingEvent> eventos(Level nivel) {
        return coletor.list.stream().filter(evento -> evento.getLevel() == nivel).toList();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"apertado", "medio", "folgado"})
    @DisplayName("algorithmParams presente, vazio e sem nenhuma das tres chaves")
    void aInstanciaNaoCarregaAsTresChaves(String perfil) throws Exception {
        JsonNode params = objectMapper.readTree(instancia(perfil)).path("algorithmParams");

        assertThat(params.isObject()).as("algorithmParams e um objeto").isTrue();
        CHAVES_IGNORADAS.forEach(chave -> assertThat(params.has(chave)).as(chave).isFalse());
        assertThat(params.isEmpty()).as("o mapa da configuracao padrao da plataforma").isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"apertado", "medio", "folgado"})
    @DisplayName("o pedido do arquivo, pelo endpoint, devolve plano do motor padrao e nenhum WARN")
    void oPedidoDoArquivoDevolvePlano(String perfil) throws Exception {
        MvcResult result = mockMvc.perform(post(PlanProtocol.PLANS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instancia(perfil)))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("corpo: %s", result.getResponse().getContentAsString())
                .isEqualTo(200);
        JsonNode plano = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(plano.path("sessions")).as("sessoes").isNotEmpty();
        assertThat(plano.path("fitness").path("engine").asText()).isEqualTo(GreedyBaselineEngine.ID);
        assertThat(eventos(Level.WARN)).as("nenhuma chave ignorada a avisar").isEmpty();
        assertThat(eventos(Level.INFO)).as("o coletor ve o log desta requisicao").hasSize(1);
    }
}
