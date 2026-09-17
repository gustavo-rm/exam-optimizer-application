package com.ia.project.dynamicstudyplanner.infra.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import com.ia.project.dynamicstudyplanner.support.RedisDeTeste;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava a diferença entre os dois armazenamentos de baldes — a correção do achado E1.
 *
 * <h2>O que este arquivo prova</h2>
 *
 * O defeito era que o limite de taxa contava separado em cada réplica: com N instâncias, o limite
 * declarado virava N × o valor configurado. O teste simula duas réplicas construindo <b>dois</b>
 * armazenamentos independentes — como dois processos fariam — e verifica que:
 *
 * <ul>
 *   <li>com armazenamento <b>local</b>, o cliente consome o limite <b>duas vezes</b> (o defeito,
 *       registrado aqui como contraprova para que não volte por engano);</li>
 *   <li>com armazenamento <b>compartilhado</b>, o consumo soma e o limite vale uma vez só.</li>
 * </ul>
 *
 * <h2>Por que contra um Redis de verdade, e não um dublê</h2>
 *
 * O que se quer verificar é justamente que dois clientes distintos, apontando para o mesmo servidor,
 * enxergam o mesmo estado. Um dublê em memória compartilhado pelos dois lados provaria apenas que o
 * dublê é compartilhado. A operação real também exercita o <i>compare-and-swap</i> do bucket4j
 * sobre o Redis, que é onde estaria um erro de concorrência.
 *
 * <p>O servidor é <b>embutido</b> ({@code support/RedisDeTeste}), não o Redis da máquina. Depender
 * de um Redis instalado tornaria este teste condicional — pulado em qualquer máquina sem ele,
 * incluindo integração contínua —, e um teste que não roda não protege nada.
 */
@DisplayName("Limite de taxa: local conta por replica, compartilhado conta uma vez so")
class RateLimitBucketsCompartilhadoTest {

    private static final int CAPACIDADE = 5;

    private static RedisDeTeste redis;
    private static RedisClient client;

    @BeforeAll
    static void subirRedis() throws IOException {
        redis = RedisDeTeste.iniciar();
        client = RedisClient.create(redis.uri());
        client.setDefaultTimeout(Duration.ofSeconds(2));
    }

    @AfterAll
    static void derrubarRedis() throws IOException {
        if (client != null) {
            client.shutdown();
        }
        if (redis != null) {
            redis.close();
        }
    }

    private static Supplier<BucketConfiguration> configuracao() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(CAPACIDADE)
                        .refillIntervally(CAPACIDADE, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /** Consome tokens alternando entre os dois armazenamentos e devolve quantos passaram. */
    private static int aceitosAlternando(RateLimitBuckets replicaA, RateLimitBuckets replicaB,
                                         String cliente, int tentativas) {
        int aceitos = 0;
        for (int i = 0; i < tentativas; i++) {
            Bucket balde = (i % 2 == 0 ? replicaA : replicaB).resolve(cliente);
            if (balde.tryConsume(1)) {
                aceitos++;
            }
        }
        return aceitos;
    }

    @Test
    @DisplayName("armazenamento local: duas replicas dobram o limite — o defeito E1")
    void localDobraOLimite() {
        // Contraprova. Se algum dia este teste passar a acusar 5, o armazenamento local deixou de
        // ser local — e o aviso de inicializacao que o anuncia passou a mentir.
        RateLimitBuckets replicaA = new LocalRateLimitBuckets(configuracao());
        RateLimitBuckets replicaB = new LocalRateLimitBuckets(configuracao());

        int aceitos = aceitosAlternando(replicaA, replicaB, "cliente-local", 12);

        assertThat(aceitos)
                .as("duas replicas com baldes locais aceitam 2x o limite — e o comportamento medido "
                        + "na etapa 06 com dois processos reais")
                .isEqualTo(CAPACIDADE * 2);
        assertThat(replicaA.compartilhado()).isFalse();
    }

    @Test
    @DisplayName("armazenamento compartilhado: duas replicas somam no mesmo balde")
    void compartilhadoSomaNoMesmoBalde() {
        // Chave unica por execucao: o teste nao pode depender do estado deixado por outro.
        String cliente = "cliente-" + UUID.randomUUID();
        RateLimitBuckets replicaA = new RedisRateLimitBuckets(client, configuracao(), Duration.ofMinutes(1));
        RateLimitBuckets replicaB = new RedisRateLimitBuckets(client, configuracao(), Duration.ofMinutes(1));

        int aceitos = aceitosAlternando(replicaA, replicaB, cliente, 12);

        assertThat(aceitos)
                .as("com o balde no Redis, o limite vale para o conjunto das replicas")
                .isEqualTo(CAPACIDADE);
        assertThat(replicaA.compartilhado()).isTrue();
    }

    @Test
    @DisplayName("o balde compartilhado sobrevive a troca de replica no meio do consumo")
    void oBaldeSobreviveATrocaDeReplica() {
        String cliente = "cliente-" + UUID.randomUUID();
        RateLimitBuckets replicaA = new RedisRateLimitBuckets(client, configuracao(), Duration.ofMinutes(1));
        RateLimitBuckets replicaB = new RedisRateLimitBuckets(client, configuracao(), Duration.ofMinutes(1));

        // Esgota tudo numa replica so.
        for (int i = 0; i < CAPACIDADE; i++) {
            assertThat(replicaA.resolve(cliente).tryConsume(1)).isTrue();
        }

        // A outra replica tem que ver o balde vazio — e este e o caso que o balanceador produz.
        assertThat(replicaB.resolve(cliente).tryConsume(1))
                .as("a replica B nao pode dar um token novo a quem ja esgotou o limite na replica A")
                .isFalse();
    }
}
