package com.ia.project.dynamicstudyplanner.benchmark.metric;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rank correlation used by every report, with emphasis on tie handling.
 * <p>
 * Ties are not an edge case here: the business metric is a count of subjects over a total, so
 * planners routinely land on the same value, and whether those ties are detected changes the
 * published coefficient. See docs/revisao-ag/08-saturacao-e-amostragem.md §2.
 */
@DisplayName("Correlacao de Spearman")
class SpearmanTest {

    @Test
    @DisplayName("empates a menos de um ulp sao empates, nao ordens distintas")
    void tiesWithinOneUlpAreTreatedAsTies() {
        // Exactly the shape that produced the defect: three planners at 0.975, but one of them is
        // the mean of seven repetitions and therefore lands one ulp low.
        double meanOfSevenIdentical = meanOfSeven(0.975);
        assertThat(meanOfSevenIdentical)
                .as("pre-condicao: somar 0,975 sete vezes num laco e dividir - exatamente como "
                        + "StrategyOutcome faz - NAO devolve o valor exato")
                .isNotEqualTo(0.975);

        List<Double> fitness = List.of(0.5524, 0.5456, 0.5434, 0.5336, 0.5295, 0.5418);
        List<Double> window = List.of(meanOfSevenIdentical, 0.975, 0.975, 1.0, 0.996429, 0.982143);

        assertThat(Spearman.correlation(fitness, window))
                .as("Com deteccao exata de empate este valor era -0,928; o correto e -0,880, que e o "
                        + "que a mesma serie devolve quando passa por um round-trip em CSV.")
                .isCloseTo(-0.880, Offset.offset(0.001));
    }

    @Test
    @DisplayName("o resultado nao depende de a serie ter passado por arredondamento")
    void resultDoesNotDependOnHavingBeenRoundTrippedThroughText() {
        List<Double> fitness = List.of(0.552424, 0.545580, 0.543408, 0.533587, 0.529482, 0.541844);
        List<Double> inMemory = List.of(meanOfSeven(0.975), 0.975, 0.975, 1.0, 0.996429, 0.982143);
        List<Double> roundTripped = List.of(0.9750, 0.9750, 0.9750, 1.0000, 0.9964, 0.9821);

        assertThat(Spearman.correlation(fitness, inMemory))
                .as("Dois caminhos de codigo sobre os mesmos dados tem de publicar o mesmo numero.")
                .isCloseTo(Spearman.correlation(fitness, roundTripped), Offset.offset(1e-9));
    }

    @Test
    @DisplayName("diferencas reais na metrica de negocio continuam sendo diferencas")
    void genuineDifferencesAreStillRanked() {
        // The metric is k/N with N = 40, so the smallest real gap is 0.025 - seven orders of
        // magnitude above the tie tolerance.
        List<Double> fitness = List.of(0.10, 0.20, 0.30);
        List<Double> window = List.of(0.950, 0.975, 1.000);

        assertThat(Spearman.correlation(fitness, window))
                .as("A tolerancia de empate nao pode achatar diferencas de 1/40.")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("serie constante devolve n/d, nao zero")
    void aConstantSeriesIsUndefinedRatherThanZero() {
        assertThat(Spearman.correlation(List.of(0.1, 0.2, 0.3), List.of(1.0, 1.0, 1.0))).isNaN();
    }

    @Test
    @DisplayName("saturacao no teto continua indefinida, com ou sem media")
    void ceilingSaturationStaysUndefined() {
        // The four excluded instances sit at exactly 100% for every planner. Seven 1.0 values do sum
        // exactly, so these were never exposed to the tie defect - asserted here so that stays true
        // if the averaging changes.
        double meanOfSevenOnes = meanOfSeven(1.0);
        assertThat(meanOfSevenOnes).isEqualTo(1.0);

        assertThat(Spearman.correlation(
                List.of(0.1, 0.2, 0.3, 0.4), List.of(meanOfSevenOnes, 1.0, 1.0, 1.0)))
                .as("Saturacao no teto tem de continuar sendo indefinida.")
                .isNaN();
    }

    /** Averages seven copies of a value the way {@code StrategyOutcome} does: loop sum, then divide. */
    private static double meanOfSeven(double value) {
        double sum = 0.0;
        for (int i = 0; i < 7; i++) {
            sum += value;
        }
        return sum / 7;
    }
}
