package com.ia.project.dynamicstudyplanner.infra.ratelimit;

import io.github.bucket4j.Bucket;

/**
 * Onde moram os baldes de limite de taxa — a peça de estado que decide se o serviço pode rodar em
 * mais de uma réplica.
 *
 * <h2>Por que esta interface existe (achado E1)</h2>
 *
 * Até a etapa 06 o {@code RateLimitingFilter} construía o próprio cache Caffeine no construtor. Cada
 * instância da aplicação ficava com <b>seu</b> mapa de baldes, e um cliente distribuído pelo
 * balanceador entre N réplicas recebia N baldes independentes: <b>o limite efetivo virava N × o
 * valor configurado</b>.
 *
 * <p>Isso não foi deduzido. Foi executado, com dois processos reais em portas distintas e limite de
 * 5 por minuto — o mesmo cliente alternando entre eles teve <b>10 requisições aceitas</b>
 * ({@code docs/qualidade/06-diagnostico-escalonamento.md}, §1.2).
 *
 * <p>O agravante era o silêncio: nenhum log, métrica ou teste acusava. O serviço seguia respondendo
 * 200; só o limite deixara de valer — desfazendo, sem tocar numa linha do filtro, o endurecimento
 * feito na etapa 02.
 *
 * <h2>As duas implementações, e como escolher</h2>
 *
 * <ul>
 *   <li>{@link RedisRateLimitBuckets} — baldes num Redis compartilhado. <b>É a única que permite
 *       mais de uma réplica.</b></li>
 *   <li>{@link LocalRateLimitBuckets} — baldes na memória do processo. Correta para uma instância
 *       só; <b>quebra o limite</b> a partir da segunda.</li>
 * </ul>
 *
 * <p>A escolha é a propriedade {@code api.shared-state.redis.enabled}. O padrão é local, porque é o
 * que serve para desenvolvimento — mas {@code SharedStateConfig} <b>registra um aviso no log de
 * inicialização</b> quando o modo local está ativo, dizendo exatamente o que quebra se houver mais
 * de uma réplica. O defeito original era invisível; este não é.
 */
public interface RateLimitBuckets {

    /**
     * Devolve o balde do cliente, criando-o na primeira vez.
     *
     * @param clientKey chave do cliente — o endereço completo, para não juntar clientes distintos
     * @return o balde, já configurado com a capacidade e a taxa de reposição vigentes
     */
    Bucket resolve(String clientKey);

    /** Nome curto do modo, para log e para a métrica de diagnóstico. */
    String modo();

    /** {@code true} se este armazenamento é compartilhado entre réplicas. */
    boolean compartilhado();
}
