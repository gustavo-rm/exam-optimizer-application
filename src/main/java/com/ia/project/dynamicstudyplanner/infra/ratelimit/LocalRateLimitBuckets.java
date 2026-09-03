package com.ia.project.dynamicstudyplanner.infra.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Baldes na memória do próprio processo. É o comportamento que existia até a etapa 06.
 *
 * <p><b>Correta com uma réplica; incorreta a partir da segunda.</b> Não há nada a consertar aqui —
 * um cache local é local por definição. O que a etapa 06b mudou é que a escolha passou a ser
 * explícita e anunciada no log, em vez de ser a única possibilidade e silenciosa. Para replicar,
 * use {@link RedisRateLimitBuckets}.
 *
 * <p>A política de despejo (10 000 clientes, 60 minutos sem acesso) é a original e continua sendo o
 * que impede o mapa de crescer sem teto sob tráfego de muitos endereços distintos.
 */
public class LocalRateLimitBuckets implements RateLimitBuckets {

    private final Cache<String, Bucket> cache;
    private final Supplier<BucketConfiguration> configuracao;

    public LocalRateLimitBuckets(Supplier<BucketConfiguration> configuracao) {
        this.configuracao = configuracao;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(60))
                .build();
    }

    @Override
    public Bucket resolve(String clientKey) {
        return cache.get(clientKey, chave -> Bucket.builder()
                .addLimit(configuracao.get().getBandwidths()[0])
                .build());
    }

    @Override
    public String modo() {
        return "local";
    }

    @Override
    public boolean compartilhado() {
        return false;
    }
}
