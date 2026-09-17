package com.ia.project.dynamicstudyplanner.infra.jobs;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Optional;

/**
 * Registros na memória do próprio processo.
 *
 * <p><b>Serve para uma réplica só.</b> Com mais de uma, a consulta de resultado cai numa réplica que
 * pode não conhecer o identificador — ver a nota em {@link JobStore}. A escolha é anunciada no log
 * de inicialização por {@code SharedStateConfig}.
 *
 * <p>O tempo de vida limita a memória: um registro concluído que ninguém buscou é descartado depois
 * da janela configurada. Sem isso, cada pedido deixaria um resultado de ~36 KB para sempre.
 */
public class LocalJobStore implements JobStore {

    private final Cache<String, OptimizationJob> cache;

    public LocalJobStore(Duration tempoDeVida, long maximoDeRegistros) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximoDeRegistros)
                .expireAfterWrite(tempoDeVida)
                .build();
    }

    @Override
    public void save(OptimizationJob job) {
        cache.put(job.id(), job);
    }

    @Override
    public Optional<OptimizationJob> find(String id) {
        return Optional.ofNullable(cache.getIfPresent(id));
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
