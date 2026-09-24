package com.ia.project.dynamicstudyplanner.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.support.RequestPayloads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trava o conjunto mínimo de métricas que permite detectar um problema de escala antes do incidente.
 *
 * <h2>O que "mínimo" quer dizer aqui</h2>
 *
 * Três perguntas precisam ser respondíveis sem acesso à máquina:
 *
 * <ul>
 *   <li><b>Está lento?</b> — latência por rota, e o tempo interno da otimização separado do resto.</li>
 *   <li><b>Está errando?</b> — contagem por código de status, e recusas por capacidade contadas
 *       <i>à parte</i> dos erros de verdade. Sem essa separação, sobrecarga e defeito se confundem:
 *       o achado E2 media 55 % de taxa de erro para um serviço que estava apenas cheio.</li>
 *   <li><b>Está cheio?</b> — saturação da fila como razão pronta, threads do conector, memória.</li>
 * </ul>
 *
 * <p>Uma métrica que ninguém publica é uma pergunta que ninguém consegue responder às 3 da manhã.
 * Este teste é o que impede que uma delas desapareça sem que alguém perceba.
 *
 * <h2>O que EOA-4b levou junto, e por quê</h2>
 *
 * Quatro instrumentos saíram porque a máquina que eles mediam saiu:
 *
 * <ul>
 *   <li>{@code dynamicstudyplanner_optimization_duration_seconds} — vinha de
 *       {@code service.OptimizationMetrics}, que instrumentava o serviço síncrono de concurso. O
 *       caminho que ficou <b>não tem substituto</b>: {@code GeneticPlanEngine} responde
 *       {@code elapsedMillis: 0} de propósito, e nenhum cronômetro mede a busca. É a única das três
 *       perguntas acima que ficou sem instrumento, e está registrada como tal;</li>
 *   <li>{@code dynamicstudyplanner_overload_rejected_total} e
 *       {@code dynamicstudyplanner_optimizer_queue_saturation} — contavam a fila do executor
 *       assíncrono, que não existe mais: {@code /plans} é síncrono;</li>
 *   <li>{@code dynamicstudyplanner_shared_state_replicavel} e os contadores
 *       {@code dynamicstudyplanner_jobs_*} — mediam o armazenamento compartilhado e o ciclo de vida
 *       dos trabalhos assíncronos, ambos removidos com o endpoint que os usava.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles(PlanProtocol.PROFILE)
@TestPropertySource(properties = {
        "spring.security.user.name=metricas",
        "spring.security.user.password=senha-de-teste"
})
@DisplayName("Observabilidade: o conjunto minimo para diagnosticar escala esta publicado")
class ObservabilidadeMinimaTest {

    @Autowired
    private MockMvc mvc;

    private String coletar() throws Exception {
        return mvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("metricas", "senha-de-teste")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("latencia: por rota")
    void latencia() throws Exception {
        // Uma requisicao real primeiro: http_server_requests so aparece depois que algo passa.
        mvc.perform(post(PlanProtocol.PLANS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RequestPayloads.requisicaoValida()));

        String metricas = coletar();

        assertThat(metricas)
                .as("latencia por requisicao HTTP, com rotulo de metodo e status")
                .contains("http_server_requests_active_seconds");
    }

    @Test
    @DisplayName("taxa de erro: contagem por codigo de status")
    void taxaDeErro() throws Exception {
        String metricas = coletar();

        assertThat(metricas)
                .as("contagem por codigo de status")
                .contains("http_server_requests_seconds_count");
    }

    @Test
    @DisplayName("saturacao: memoria")
    void saturacao() throws Exception {
        String metricas = coletar();

        assertThat(metricas)
                .as("memoria")
                .contains("jvm_memory_used_bytes");
    }

    @Test
    @DisplayName("as metricas de thread do conector estao habilitadas (achado E8)")
    void metricasDoConectorHabilitadas() {
        // Verificado pela CONFIGURACAO, e nao pelo scrape: num teste com servlet simulado nao ha
        // conector Tomcat real, entao tomcat_threads_* nao existe ali por ausencia de conector, e
        // nao por falta da chave. Contra o jar em execucao a metrica aparece — foi assim que a
        // etapa 06b mediu 1 de 200 threads ocupadas com 30 pedidos em voo.
        //
        // Sem esta chave o Micrometer publica so tomcat_sessions_*: seis series, todas zero neste
        // servico, que nao usa sessao. Justamente a metrica que diria se o conector saturou e a que
        // estaria faltando no dia em que isso acontecesse.
        assertThat(propriedade("server.tomcat.mbeanregistry.enabled"))
                .as("sem isto, tomcat_threads_busy e tomcat_threads_config_max nao sao publicadas")
                .isEqualTo("true");
    }

    @Test
    @DisplayName("a amostragem de rastreamento nao esta em 100 % (achado E7)")
    void amostragemDeRastreamentoRazoavel() {
        // Estava em 1.0 sem nenhum exportador no classpath: toda requisicao pagava a instrumentacao
        // por um span que era descartado. A 10x ou 100x de trafego, isso e custo puro.
        String valor = propriedade("management.tracing.sampling.probability");

        assertThat(valor).as("a chave precisa continuar declarada, para ser uma decisao").isNotNull();
        double amostragem = Double.parseDouble(
                valor.replaceAll(".*:", "").replace("}", "").trim());
        assertThat(amostragem)
                .as("amostrar 100 %% sem coletor e pagar por um dado que ninguem le")
                .isLessThan(1.0)
                .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("a compressao de resposta esta ligada (achado E9)")
    void compressaoLigada() {
        // 36,3 KB por resposta, 99 % dela o cronograma dia a dia — repetitivo por natureza.
        // Medido contra o jar: comprime para 2,4 KB, razao de 15,4x. Numa replica saturada isso e
        // 21,4 GB/h de saida contra 1,4 GB/h, e egresso cresce linearmente sem teto.
        assertThat(propriedade("server.compression.enabled")).isEqualTo("true");
        assertThat(propriedade("server.compression.mime-types")).contains("application/json");
    }

    /** Lê a propriedade do {@code application.properties} que vale em produção. */
    private static String propriedade(String chave) {
        try (java.io.InputStream in = ObservabilidadeMinimaTest.class
                .getResourceAsStream("/application.properties")) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            return p.getProperty(chave);
        } catch (Exception e) {
            throw new IllegalStateException("nao foi possivel ler " + chave, e);
        }
    }

}
