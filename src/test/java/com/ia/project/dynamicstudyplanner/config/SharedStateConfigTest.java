package com.ia.project.dynamicstudyplanner.config;

import com.ia.project.dynamicstudyplanner.infra.ratelimit.RateLimitBuckets;
import com.ia.project.dynamicstudyplanner.support.RedisDeTeste;
import io.lettuce.core.RedisClient;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava a escolha do armazenamento de estado compartilhado e o sinal que ela emite.
 *
 * <h2>Por que a métrica importa tanto quanto o comportamento</h2>
 *
 * O achado E1 não era só "o cache é local". Era "o cache é local e <b>ninguém tem como saber</b>":
 * nenhum log, nenhuma métrica e nenhum teste acusavam, e o serviço seguia respondendo 200 com o
 * limite valendo N vezes mais.
 *
 * <p>Por isso a correção publica {@code dynamicstudyplanner.shared.state.replicavel} — 1 quando o
 * estado permite replicar, 0 quando não. Um painel que veja duas réplicas reportando 0 está vendo o
 * defeito acontecer. Este teste trava esse sinal.
 */
@DisplayName("Estado compartilhado: a escolha do armazenamento e explicita e observavel")
class SharedStateConfigTest {

    private static SharedStateConfig configuracao() {
        SharedStateConfig config = new SharedStateConfig();
        ReflectionTestUtils.setField(config, "capacity", 5);
        ReflectionTestUtils.setField(config, "refillTokens", 5);
        ReflectionTestUtils.setField(config, "refillDurationMinutes", 1);
        return config;
    }

    private static RedisDeTeste redis;

    @BeforeAll
    static void subirRedis() throws IOException {
        redis = RedisDeTeste.iniciar();
    }

    @AfterAll
    static void derrubarRedis() throws IOException {
        if (redis != null) {
            redis.close();
        }
    }

    @Test
    @DisplayName("modo local: funciona, e reporta que NAO pode ser replicado")
    void modoLocalReportaQueNaoPodeSerReplicado() {
        SimpleMeterRegistry registro = new SimpleMeterRegistry();

        RateLimitBuckets buckets = configuracao().localRateLimitBuckets(registro);

        assertThat(buckets.modo()).isEqualTo("local");
        assertThat(buckets.compartilhado())
                .as("um cache na memoria do processo nunca e compartilhado entre replicas")
                .isFalse();

        Gauge sinal = registro.find("dynamicstudyplanner.shared.state.replicavel").gauge();
        assertThat(sinal).as("a metrica precisa existir — e ela que torna o defeito E1 visivel")
                .isNotNull();
        assertThat(sinal.value()).isZero();
        assertThat(sinal.getId().getTag("store")).isEqualTo("local");
    }

    @Test
    @DisplayName("o balde local realmente limita, e o limite e o configurado")
    void oBaldeLocalLimita() {
        RateLimitBuckets buckets = configuracao().localRateLimitBuckets(new SimpleMeterRegistry());

        int aceitos = 0;
        for (int i = 0; i < 10; i++) {
            if (buckets.resolve("cliente").tryConsume(1)) {
                aceitos++;
            }
        }

        assertThat(aceitos).isEqualTo(5);
    }

    @Test
    @DisplayName("o cliente Redis e criado com o endereco e o prazo configurados")
    void oClienteRedisUsaOEnderecoConfigurado() {
        // Nao conecta: RedisClient.create so monta o cliente. Por isso este teste roda sem Redis.
        RedisClient client = configuracao().redisClient("192.0.2.10", 6380, 1500);

        try {
            assertThat(client.getDefaultTimeout()).isEqualTo(Duration.ofMillis(1500));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("modo Redis: funciona, e reporta que PODE ser replicado")
    void modoRedisReportaQuePodeSerReplicado() {
        SimpleMeterRegistry registro = new SimpleMeterRegistry();
        RedisClient client = configuracao().redisClient("localhost", redis.porta(), 2000);

        try {
            RateLimitBuckets buckets = configuracao().redisRateLimitBuckets(client, registro);

            assertThat(buckets.modo()).isEqualTo("redis");
            assertThat(buckets.compartilhado()).isTrue();

            Gauge sinal = registro.find("dynamicstudyplanner.shared.state.replicavel").gauge();
            assertThat(sinal).isNotNull();
            assertThat(sinal.value()).isEqualTo(1.0);
            assertThat(sinal.getId().getTag("store")).isEqualTo("redis");
        } finally {
            client.shutdown();
        }
    }
}
