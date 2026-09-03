package com.ia.project.dynamicstudyplanner.config;

import com.ia.project.dynamicstudyplanner.infra.ratelimit.LocalRateLimitBuckets;
import com.ia.project.dynamicstudyplanner.infra.ratelimit.RateLimitBuckets;
import com.ia.project.dynamicstudyplanner.infra.ratelimit.RedisRateLimitBuckets;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Onde mora o estado que precisa ser o mesmo em todas as réplicas.
 *
 * <h2>O problema que esta classe resolve (achado E1)</h2>
 *
 * O diagnóstico de escalonamento encontrou uma única peça de estado compartilhado no sistema — os
 * baldes do limite de taxa — e ela vivia na memória de cada processo. Com N réplicas o limite
 * declarado virava N × o valor configurado, <b>em silêncio</b>.
 *
 * <h2>A decisão de projeto: local é o padrão, mas nunca é silencioso</h2>
 *
 * O padrão continua sendo o armazenamento local, porque a maioria das execuções (desenvolvimento,
 * teste, uma instância só) não deve exigir um Redis. O que mudou é que <b>o modo local se anuncia</b>:
 * a inicialização registra um aviso dizendo, em uma linha, o que quebra se houver mais de uma
 * réplica. A causa raiz do E1 não era o cache local — era ninguém saber que ele era local.
 *
 * <p>Além do log, o modo vai para uma métrica ({@code dynamicstudyplanner.shared.state.replicavel}),
 * para que um painel possa alarmar sobre "há mais de uma réplica reportando estado não
 * compartilhado" — a condição exata do defeito.
 *
 * <h2>Conexões ao Redis: por que uma por réplica basta (item 3 da etapa)</h2>
 *
 * A pergunta original sobre pool de conexões não se aplicava, porque não havia banco. Ao introduzir
 * Redis, ela passa a se aplicar — e a resposta do Lettuce é diferente da de um pool JDBC.
 *
 * <p>O Lettuce <b>multiplexa</b>: uma conexão TCP carrega comandos de muitas threads ao mesmo tempo,
 * porque o protocolo do Redis é pipelinado e as respostas voltam em ordem. Não há "uma conexão por
 * requisição em andamento" como em JDBC, onde a conexão fica presa à transação. Por isso o número de
 * conexões cresce com o número de <b>réplicas</b>, não com o de requisições.
 *
 * <p>Isso importa porque é o erro clássico de escala: um pool de 10 conexões por réplica é razoável
 * com 2 réplicas e derruba um Redis padrão ({@code maxclients} de 10 000, mas com limite prático
 * bem menor por memória) muito antes do que se espera. Com multiplexação, 100 réplicas custam ~200
 * conexões. O número real medido nesta etapa está em
 * {@code docs/qualidade/06b-correcao-escalonamento.md}.
 */
@Configuration
public class SharedStateConfig {

    private static final Logger log = LoggerFactory.getLogger(SharedStateConfig.class);

    @Value("${api.rate-limit.capacity:5}")
    private int capacity;

    @Value("${api.rate-limit.refill-tokens:5}")
    private int refillTokens;

    @Value("${api.rate-limit.refill-duration-minutes:1}")
    private int refillDurationMinutes;

    private Supplier<BucketConfiguration> configuracaoDoBalde() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(refillTokens, Duration.ofMinutes(refillDurationMinutes))
                        .build())
                .build();
    }

    /**
     * Cliente Lettuce, criado apenas quando o estado compartilhado está ligado.
     *
     * <p>{@code shutdownTimeout} curto para que o encerramento da réplica não fique preso esperando
     * conexões: numa implantação com autoescalonamento, réplicas sobem e descem o tempo todo.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "api.shared-state.redis.enabled", havingValue = "true")
    public RedisClient redisClient(
            @Value("${api.shared-state.redis.host:localhost}") String host,
            @Value("${api.shared-state.redis.port:6379}") int port,
            @Value("${api.shared-state.redis.timeout-ms:2000}") long timeoutMs) {
        RedisURI uri = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withTimeout(Duration.ofMillis(timeoutMs))
                .build();
        log.info("Estado compartilhado: Redis em {}:{} (timeout {} ms)", host, port, timeoutMs);
        return RedisClient.create(uri);
    }

    @Bean
    @ConditionalOnProperty(name = "api.shared-state.redis.enabled", havingValue = "true")
    public RateLimitBuckets redisRateLimitBuckets(RedisClient redisClient, MeterRegistry registry) {
        registrarModo(registry, "redis", true);
        log.info("Limite de taxa: baldes COMPARTILHADOS no Redis. O limite de {} por {} min vale "
                + "para o conjunto das replicas.", capacity, refillDurationMinutes);
        return new RedisRateLimitBuckets(redisClient, configuracaoDoBalde(),
                Duration.ofMinutes(refillDurationMinutes));
    }

    @Bean
    @ConditionalOnProperty(name = "api.shared-state.redis.enabled", havingValue = "false",
            matchIfMissing = true)
    public RateLimitBuckets localRateLimitBuckets(MeterRegistry registry) {
        registrarModo(registry, "local", false);
        // Este aviso e a correcao central do achado E1. O defeito nao era o cache local; era o
        // cache local ser invisivel. Quem subir uma segunda replica com esta linha no log tem como
        // saber o que vai acontecer.
        log.warn("Limite de taxa: baldes LOCAIS a este processo. Correto com UMA replica. "
                + "Com N replicas o limite de {} por {} min vira {} x {} — cada replica conta "
                + "separado. Para replicar, defina api.shared-state.redis.enabled=true.",
                capacity, refillDurationMinutes, "N", capacity);
        return new LocalRateLimitBuckets(configuracaoDoBalde());
    }

    private void registrarModo(MeterRegistry registry, String modo, boolean compartilhado) {
        io.micrometer.core.instrument.Gauge
                .builder("dynamicstudyplanner.shared.state.replicavel", () -> compartilhado ? 1 : 0)
                .description("1 quando o estado compartilhado permite multiplas replicas, 0 quando nao")
                .tag("store", modo)
                .register(registry);
    }
}
