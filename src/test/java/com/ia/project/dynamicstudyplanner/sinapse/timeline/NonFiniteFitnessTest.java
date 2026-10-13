package com.ia.project.dynamicstudyplanner.sinapse.timeline;

import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.Population;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra do EOA-12 (ADR-0011): aptidão não finita vale o pior valor, e todo valor finito sai
 * idêntico bit a bit.
 */
@DisplayName("NonFiniteFitness: nao finito vira o pior valor, finito passa intacto")
class NonFiniteFitnessTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("NaN, +Infinity e -Infinity viram o pior valor")
    void naoFinitoViraOPiorValor(double naoFinito) {
        assertThat(NonFiniteFitness.orWorst(naoFinito)).isEqualTo(NonFiniteFitness.WORST);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(doubles = {0.0, -0.0, 1.0, -1.0, 0.3928, -0.6056942, -0.0389576,
        Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE, -Double.MAX_VALUE})
    @DisplayName("todo valor finito sai identico bit a bit")
    void finitoSaiIdenticoBitABit(double finito) {
        assertThat(Double.doubleToRawLongBits(NonFiniteFitness.orWorst(finito)))
                .isEqualTo(Double.doubleToRawLongBits(finito));
    }

    @Test
    @DisplayName("o pior valor fica abaixo de todo finito na ordem que getFittest e o torneio usam")
    void oPiorValorFicaAbaixoDeTodoFinito() {
        assertThat(Double.compare(NonFiniteFitness.WORST, -Double.MAX_VALUE)).isNegative();
        assertThat(-Double.MAX_VALUE > NonFiniteFitness.WORST).isTrue();
    }

    @Test
    @DisplayName("na populacao, so a aptidao nao finita e regravada, e o vazio deixa de ser o mais apto")
    void naPopulacaoSoONaoFinitoEhRegravado() {
        Individual vazio = individuo(Double.NaN);
        Individual preenchido = individuo(-0.6056942);
        Individual infinito = individuo(Double.POSITIVE_INFINITY);
        Population populacao = new Population(3);
        populacao.addIndividual(vazio);
        populacao.addIndividual(preenchido);
        populacao.addIndividual(infinito);

        assertThat(populacao.getFittest()).as("antes: Double.compare poe NaN no topo").isSameAs(vazio);

        NonFiniteFitness.demote(populacao);

        assertThat(vazio.getFitness()).isEqualTo(NonFiniteFitness.WORST);
        assertThat(infinito.getFitness()).isEqualTo(NonFiniteFitness.WORST);
        assertThat(Double.doubleToRawLongBits(preenchido.getFitness()))
                .isEqualTo(Double.doubleToRawLongBits(-0.6056942));
        assertThat(populacao.getFittest()).isSameAs(preenchido);
    }

    private static Individual individuo(double aptidao) {
        Individual individuo = new Individual(new TacticalStudyPlan(Map.of()));
        individuo.setFitness(aptidao);
        return individuo;
    }
}
