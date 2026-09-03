package com.ia.project.dynamicstudyplanner.infra.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Baldes num Redis compartilhado — a correção do achado E1.
 *
 * <h2>Como o limite passa a valer entre réplicas</h2>
 *
 * Cada cliente tem <b>uma</b> chave no Redis. Toda réplica que atende uma requisição desse cliente
 * lê e atualiza a mesma chave, então o consumo soma em vez de multiplicar. Com o mesmo limite de 5
 * por minuto e duas réplicas, o sexto pedido é recusado — venha ele de qual réplica vier.
 *
 * <h2>Por que <i>compare-and-swap</i>, e não um contador simples</h2>
 *
 * Duas réplicas podem tentar consumir o mesmo token no mesmo instante. O {@code bucket4j} resolve
 * isso com CAS (<i>compare-and-swap</i>: lê o estado do balde com sua versão, calcula o novo estado
 * e só grava se a versão não mudou; se mudou, refaz a partir do estado novo). Um {@code INCR} solto
 * contaria certo, mas não implementa reposição por tempo — que é o que transforma "5 no total" em
 * "5 por minuto".
 *
 * <h2>Expiração: por que a chave não vaza</h2>
 *
 * {@code basedOnTimeForRefillingBucketUpToMax} faz o Redis expirar a chave quando o balde já teria
 * voltado ao máximo — ou seja, quando guardá-la deixou de significar alguma coisa. Sem isso, cada
 * endereço que aparecesse uma vez deixaria uma chave para sempre, e o Redis viraria o vazamento que
 * o Caffeine local evitava por despejo.
 *
 * <h2>O que acontece se o Redis cair</h2>
 *
 * A chamada falha e a exceção sobe. <b>Isso é deliberado e é a escolha segura</b>: o limite de taxa
 * é um controle de segurança (etapa 02), e um controle de segurança que se desliga sozinho quando a
 * infraestrutura tem problema é pior que nenhum — vira exatamente a janela que um abuso espera. A
 * alternativa (deixar passar quando o Redis não responde) transformaria uma indisponibilidade do
 * Redis em ausência total de limite em todas as réplicas ao mesmo tempo.
 *
 * <p>O tratamento da falha fica em {@code InfrastructureErrorAdvice}, que a devolve como
 * <b>503 com {@code Retry-After}</b> — a mesma resposta de sobrecarga, porque é o mesmo significado
 * para o cliente: não consigo te atender agora, tente daqui a pouco.
 */
public class RedisRateLimitBuckets implements RateLimitBuckets {

    private final ProxyManager<byte[]> proxyManager;
    private final Supplier<BucketConfiguration> configuracao;

    public RedisRateLimitBuckets(RedisClient redisClient, Supplier<BucketConfiguration> configuracao,
                                 Duration janelaDeReposicao) {
        this.configuracao = configuracao;
        this.proxyManager = LettuceBasedProxyManager.builderFor(redisClient)
                .withExpirationStrategy(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(janelaDeReposicao))
                .build();
    }

    @Override
    public Bucket resolve(String clientKey) {
        // Prefixo de espaco de nomes: o Redis pode ser compartilhado com outros usos, e uma chave
        // sem prefixo colidiria em silencio.
        byte[] chave = ("dsp:rate-limit:" + clientKey).getBytes(StandardCharsets.UTF_8);
        return proxyManager.builder().build(chave, configuracao);
    }

    @Override
    public String modo() {
        return "redis";
    }

    @Override
    public boolean compartilhado() {
        return true;
    }
}
