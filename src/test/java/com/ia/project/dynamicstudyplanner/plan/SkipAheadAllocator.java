package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * O alocador de produção com a regra P3: uma tentativa que falha não altera o estado.
 *
 * <h2>Por que sobre a API pública, e não uma cópia do alocador</h2>
 *
 * {@link AvailabilityAllocator} guarda cursor e janela em campos privados, e uma falha de
 * {@code place} avança a janela até o fim ({@code AvailabilityAllocator.java:124}). A decisão D2b não
 * autoriza abrir esse estado. Mas o estado depois de uma sequência de colocações aceitas é função só
 * dessa sequência, porque {@code place} é determinístico. Então desfazer uma falha é refazer, sobre
 * um {@link AvailabilityAllocator#rewound()}, as durações aceitas até ali. Assim P usa o alocador de
 * produção, e não uma reimplementação que poderia divergir dele.
 *
 * <p>O custo é quadrático no número de falhas, não no de colocações: só uma falha refaz.
 *
 * <h2>Uma tentativa é atômica</h2>
 *
 * {@link #placeAll} coloca uma lista de blocos em sequência, todos ou nenhum. É o que a regra P2 pede
 * ao guloso, que coloca estudo e revisão juntos ou nenhum dos dois.
 */
public final class SkipAheadAllocator {

    private final AvailabilityAllocator prepared;
    private final List<Integer> accepted = new ArrayList<>();
    private AvailabilityAllocator live;

    private SkipAheadAllocator(AvailabilityAllocator prepared) {
        this.prepared = prepared;
        this.live = prepared.rewound();
    }

    /**
     * @param request o pedido, para as janelas e o horizonte
     * @return um alocador na primeira janela
     */
    public static SkipAheadAllocator over(PlanRequest request) {
        return new SkipAheadAllocator(AvailabilityAllocator.over(request));
    }

    /**
     * @param prepared janelas já preparadas; o cursor dele não é lido nem movido
     * @return um alocador na primeira janela daquelas janelas
     */
    public static SkipAheadAllocator from(AvailabilityAllocator prepared) {
        return new SkipAheadAllocator(prepared);
    }

    /**
     * Coloca um bloco.
     *
     * @param durationMinutes a duração; positiva
     * @return o início, ou vazio, e nesse caso o estado é o de antes da chamada
     */
    public Optional<Instant> place(int durationMinutes) {
        return placeAll(List.of(durationMinutes)).map(starts -> starts.get(0));
    }

    /**
     * Coloca vários blocos em sequência, todos ou nenhum.
     *
     * @param durations as durações, na ordem
     * @return os inícios, ou vazio, e nesse caso o estado é o de antes da chamada
     */
    public Optional<List<Instant>> placeAll(List<Integer> durations) {
        throw new UnsupportedOperationException("P ainda não implementada");
    }

    /** @return os minutos oferecidos, como {@link AvailabilityAllocator#availableMinutes()} */
    public long availableMinutes() {
        return prepared.availableMinutes();
    }
}
