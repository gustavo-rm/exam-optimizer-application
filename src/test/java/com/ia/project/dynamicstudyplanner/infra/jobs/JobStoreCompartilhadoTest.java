package com.ia.project.dynamicstudyplanner.infra.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ia.project.dynamicstudyplanner.support.RedisDeTeste;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava a propriedade que torna o fluxo assíncrono utilizável com mais de uma réplica.
 *
 * <h2>Por que isto é mais grave que o E1</h2>
 *
 * O fluxo assíncrono são <b>duas requisições HTTP distintas</b>, e nada garante que caiam na mesma
 * réplica. Com registro local, a consulta teria chance <b>1/N</b> de acertar a réplica que conhece o
 * identificador; as outras responderiam 404 para um trabalho que existe.
 *
 * <p>No E1 o defeito afrouxava um limite. Aqui quebraria a funcionalidade — e de forma
 * intermitente, que é a pior maneira de quebrar.
 */
@DisplayName("Registro de trabalhos: local nao atravessa replicas, compartilhado atravessa")
class JobStoreCompartilhadoTest {

    private static RedisDeTeste redis;
    private static RedisClient client;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void subir() throws IOException {
        redis = RedisDeTeste.iniciar();
        client = RedisClient.create(redis.uri());
        client.setDefaultTimeout(Duration.ofSeconds(2));
        // JavaTimeModule: o registro carrega Instant, que sem ele seria serializado como um objeto
        // com campos internos em vez de um instante ISO-8601 — ilegivel para outra versao da app.
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @AfterAll
    static void descer() throws IOException {
        if (client != null) {
            client.shutdown();
        }
        if (redis != null) {
            redis.close();
        }
    }

    private static OptimizationJob trabalhoConcluido(String id) {
        return OptimizationJob.aceito(id, Instant.now())
                .iniciado(Instant.now())
                .concluido(Instant.now(), "{\"plano\":\"ok\"}");
    }

    @Test
    @DisplayName("local: a replica B nao enxerga o trabalho gravado pela replica A")
    void localNaoAtravessaReplicas() {
        // Contraprova do defeito, para que ele nao volte por engano.
        JobStore replicaA = new LocalJobStore(Duration.ofMinutes(60), 1000);
        JobStore replicaB = new LocalJobStore(Duration.ofMinutes(60), 1000);
        String id = UUID.randomUUID().toString();

        replicaA.save(trabalhoConcluido(id));

        assertThat(replicaA.find(id)).isPresent();
        assertThat(replicaB.find(id))
                .as("com registro local, a consulta que cair na replica errada devolve 404 para um "
                        + "trabalho que existe")
                .isEmpty();
        assertThat(replicaA.compartilhado()).isFalse();
    }

    @Test
    @DisplayName("compartilhado: a replica B le o resultado gravado pela replica A")
    void compartilhadoAtravessaReplicas() {
        JobStore replicaA = new RedisJobStore(client, objectMapper, Duration.ofMinutes(60));
        JobStore replicaB = new RedisJobStore(client, objectMapper, Duration.ofMinutes(60));
        String id = UUID.randomUUID().toString();

        replicaA.save(trabalhoConcluido(id));

        assertThat(replicaB.find(id))
                .as("e esta a propriedade que faz o fluxo assincrono funcionar atras de balanceador")
                .isPresent()
                .get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(JobStatus.SUCCEEDED);
                    assertThat(job.resultJson()).isEqualTo("{\"plano\":\"ok\"}");
                    assertThat(job.terminado()).isTrue();
                });
        assertThat(replicaB.compartilhado()).isTrue();
    }

    @Test
    @DisplayName("as marcas de tempo sobrevivem a ida e volta pelo Redis")
    void asMarcasDeTempoSobrevivem() {
        // Instant serializado errado viraria um objeto ilegivel por outra versao da aplicacao —
        // exatamente o cenario de uma implantacao gradual, com replicas novas e antigas convivendo.
        JobStore store = new RedisJobStore(client, objectMapper, Duration.ofMinutes(60));
        String id = UUID.randomUUID().toString();
        OptimizationJob original = trabalhoConcluido(id);

        store.save(original);

        assertThat(store.find(id)).get().satisfies(lido -> {
            assertThat(lido.submittedAt()).isEqualTo(original.submittedAt());
            assertThat(lido.startedAt()).isEqualTo(original.startedAt());
            assertThat(lido.finishedAt()).isEqualTo(original.finishedAt());
        });
    }

    @Test
    @DisplayName("um trabalho que falhou carrega o motivo, e nao o resultado")
    void trabalhoQueFalhouCarregaOMotivo() {
        JobStore store = new RedisJobStore(client, objectMapper, Duration.ofMinutes(60));
        String id = UUID.randomUUID().toString();

        store.save(OptimizationJob.aceito(id, Instant.now())
                .iniciado(Instant.now())
                .falhou(Instant.now(), "IllegalArgumentException", "Total minimum days exceeded"));

        assertThat(store.find(id)).get().satisfies(job -> {
            assertThat(job.status()).isEqualTo(JobStatus.FAILED);
            assertThat(job.errorDetail()).isEqualTo("Total minimum days exceeded");
            assertThat(job.resultJson()).isNull();
            assertThat(job.terminado()).isTrue();
        });
    }

    @Test
    @DisplayName("identificador inexistente devolve vazio, nao erro")
    void identificadorInexistenteDevolveVazio() {
        JobStore store = new RedisJobStore(client, objectMapper, Duration.ofMinutes(60));

        assertThat(store.find("nao-existe")).isEmpty();
    }
}
