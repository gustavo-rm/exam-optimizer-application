package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Opção P (EOA-12b): o alocador cuja falha não altera o estado")
class SkipAheadAllocatorTest {

    private final PlanRequest request = SkipAheadCases.neverFitsInTheMiddle();

    @Test
    @DisplayName("uma falha não consome a janela: o bloco seguinte usa o que sobrou dela")
    void falhaNaoConsomeJanela() {
        SkipAheadAllocator allocator = SkipAheadAllocator.over(request);

        assertThat(allocator.place(40)).contains(SkipAheadCases.FIRST_WINDOW);
        assertThat(allocator.place(70)).isEmpty();
        assertThat(allocator.place(20))
                .contains(SkipAheadCases.FIRST_WINDOW.plus(40, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("na produção a mesma falha consome todas as janelas restantes")
    void naProducaoAFalhaConsome() {
        AvailabilityAllocator allocator = AvailabilityAllocator.over(request);

        assertThat(allocator.place(40)).contains(SkipAheadCases.FIRST_WINDOW);
        assertThat(allocator.place(70)).isEmpty();
        assertThat(allocator.place(20)).isEmpty();
    }

    @Test
    @DisplayName("uma tentativa de vários blocos é atômica: se o segundo não cabe, o primeiro volta")
    void tentativaAtomica() {
        SkipAheadAllocator allocator = SkipAheadAllocator.over(request);

        assertThat(allocator.placeAll(List.of(50, 70))).isEmpty();
        assertThat(allocator.place(10)).contains(SkipAheadCases.FIRST_WINDOW);
    }

    @Test
    @DisplayName("na mesma sequência de pedidos, devolve os mesmos instantes do alocador de produção")
    void mesmaSequenciaIgualAProducao() {
        AvailabilityAllocator production = AvailabilityAllocator.over(request);
        SkipAheadAllocator skipAhead = SkipAheadAllocator.over(request);

        for (int minutes : new int[] {30, 20, 45, 15, 60}) {
            Optional<Instant> expected = production.place(minutes);
            assertThat(skipAhead.place(minutes)).isEqualTo(expected);
        }
        assertThat(skipAhead.availableMinutes()).isEqualTo(production.availableMinutes());
    }
}
