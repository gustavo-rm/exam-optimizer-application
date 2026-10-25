package com.ia.project.dynamicstudyplanner.plan;

/**
 * As duas colocações medidas no EOA-12b ({@code docs/MEDICAO_ALOCADOR.md}).
 *
 * <p>Só existe em código de teste. A produção não tem variante: ela é {@link #BASE}, e a decisão D2b
 * (ADR-0012) não autoriza adotar a outra.
 */
public enum PlacementVariant {

    /** A colocação de produção: para no primeiro tópico ou bloco que não cabe. */
    BASE,

    /** A opção P: pula o tópico que não cabe e os seus dependentes {@code HARD}, e segue. */
    SKIP_AHEAD
}
