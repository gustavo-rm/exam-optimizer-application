package com.ia.project.dynamicstudyplanner.infra.jobs;

import java.util.Optional;

/**
 * Onde moram os registros de trabalho — a segunda peça de estado que decide se o serviço pode rodar
 * replicado.
 *
 * <h2>Por que isto precisa ser compartilhado</h2>
 *
 * O fluxo assíncrono é <b>duas requisições HTTP distintas</b>: uma que envia o pedido e recebe um
 * identificador, outra que consulta o resultado. Nada garante que as duas caiam na mesma réplica —
 * um balanceador distribui como quiser.
 *
 * <p>Com registro local, a consulta cairia numa réplica que nunca ouviu falar daquele
 * identificador e responderia <b>404 para um trabalho que existe</b>, de forma intermitente e
 * proporcional ao número de réplicas: com N réplicas, a chance de acertar é 1/N. Seria um defeito
 * pior que o E1, porque quebra a funcionalidade, não só um limite.
 *
 * <p>Por isso a mesma chave que liga o limite de taxa liga o registro de trabalhos
 * ({@code api.shared-state.redis.enabled}): as duas peças de estado compartilhado sobem e descem
 * juntas, e não há como ligar metade.
 */
public interface JobStore {

    /** Grava o registro, criando ou substituindo. */
    void save(OptimizationJob job);

    /** Busca por identificador. Vazio se não existe ou se já expirou. */
    Optional<OptimizationJob> find(String id);

    /** Nome curto do modo, para log e métrica. */
    String modo();

    /** {@code true} se este armazenamento é visível a todas as réplicas. */
    boolean compartilhado();
}
