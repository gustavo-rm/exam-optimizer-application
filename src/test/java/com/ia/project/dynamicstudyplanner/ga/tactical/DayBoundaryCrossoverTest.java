package com.ia.project.dynamicstudyplanner.ga.tactical;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.crossover.DayBoundaryCrossover;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rede de segurança para a refatoração de {@link DayBoundaryCrossover} (etapa 04b).
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * {@code DayBoundaryCrossover#crossover} era o método de <b>maior complexidade ciclomática do
 * repositório</b> — 10 caminhos distintos — e a classe estava a <b>0 % de cobertura</b>: nenhum
 * teste, em lugar nenhum, executava uma única linha dela. Refatorar assim é reescrever no escuro.
 *
 * <p>Estes testes fixam o comportamento medido no código anterior, incluindo dois casos-limite que
 * eu não teria adivinhado sem executá-los — ver {@code paiUmVazioProduzFilhoVazio}.
 *
 * <h2>Determinismo</h2>
 *
 * O operador sorteia duas vezes: se o cruzamento ocorre, e onde é o corte. Os testes instalam uma
 * semente fixa em {@link RandomProvider}; a extensão {@code RandomProviderIsolation}, registrada
 * automaticamente, devolve a fonte original ao fim de cada teste.
 */
@DisplayName("DayBoundaryCrossover: caracterizacao antes da refatoracao")
class DayBoundaryCrossoverTest {

    private static final Subject PORTUGUES = new Subject("Portugues", 10, 3);
    private static final Subject MATEMATICA = new Subject("Matematica", 12, 4);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 3, 2);

    private final DayBoundaryCrossover operador = new DayBoundaryCrossover();

    @BeforeEach
    void semeiaAleatoriedade() {
        RandomProvider.setInstance(new Random(20260903L));
    }

    /** Um bloco por dia, do dia {@code primeiroDia} ao {@code ultimoDia} (deslocamentos a partir de segunda). */
    private static TacticalStudyPlan planoDeDias(Subject disciplina, int primeiroDia, int ultimoDia) {
        Map<TimeSlot, TacticalStudyBlock> agenda = new LinkedHashMap<>();
        for (int d = primeiroDia; d <= ultimoDia; d++) {
            LocalDateTime de = SEGUNDA.plusDays(d).atTime(9, 0);
            agenda.put(new TimeSlot(de, de.plusHours(1)),
                    new TacticalStudyBlock(disciplina, StudyMethodology.ACTIVE_RECALL, 60));
        }
        return new TacticalStudyPlan(agenda);
    }

    private static int diaDoAno(TimeSlot slot) {
        return slot.startTime().getDayOfYear();
    }

    @Test
    @DisplayName("taxa zero devolve o proprio pai 1, sem copiar")
    void taxaZeroDevolveOPai() {
        TacticalStudyPlan pai1 = planoDeDias(PORTUGUES, 0, 4);
        TacticalStudyPlan pai2 = planoDeDias(MATEMATICA, 0, 4);

        TacticalStudyPlan filho = operador.crossover(pai1, pai2, 0.0, null);

        assertThat(filho)
                .as("com taxa 0 o sorteio nunca passa, e o operador devolve a MESMA instancia")
                .isSameAs(pai1);
    }

    @Test
    @DisplayName("taxa um sempre cruza: o filho herda de ambos os pais")
    void taxaUmSempreCruza() {
        TacticalStudyPlan pai1 = planoDeDias(PORTUGUES, 0, 6);
        TacticalStudyPlan pai2 = planoDeDias(MATEMATICA, 0, 6);

        TacticalStudyPlan filho = operador.crossover(pai1, pai2, 1.0, null);

        assertThat(filho).isNotSameAs(pai1).isNotSameAs(pai2);
        assertThat(filho.getSchedule())
                .as("os dois pais cobrem os mesmos 7 dias, entao o filho tambem cobre os 7")
                .hasSize(7);
    }

    @Test
    @DisplayName("o corte separa: ate o dia de corte vem do pai 1, depois vem do pai 2")
    void oCorteSeparaOsPais() {
        TacticalStudyPlan pai1 = planoDeDias(PORTUGUES, 0, 6);
        TacticalStudyPlan pai2 = planoDeDias(MATEMATICA, 0, 6);

        TacticalStudyPlan filho = operador.crossover(pai1, pai2, 1.0, null);

        // O dia de corte nao e exposto; deduz-se dele que todo bloco de Portugues precede todo
        // bloco de Matematica. Essa e a invariante que a refatoracao nao pode quebrar.
        int ultimoDoPai1 = Integer.MIN_VALUE;
        int primeiroDoPai2 = Integer.MAX_VALUE;
        for (Map.Entry<TimeSlot, TacticalStudyBlock> e : filho.getSchedule().entrySet()) {
            int dia = diaDoAno(e.getKey());
            if (e.getValue().subject().equals(PORTUGUES)) {
                ultimoDoPai1 = Math.max(ultimoDoPai1, dia);
            } else {
                primeiroDoPai2 = Math.min(primeiroDoPai2, dia);
            }
        }
        if (ultimoDoPai1 != Integer.MIN_VALUE && primeiroDoPai2 != Integer.MAX_VALUE) {
            assertThat(ultimoDoPai1)
                    .as("nao pode haver bloco do pai 1 depois de um bloco do pai 2")
                    .isLessThan(primeiroDoPai2);
        }
    }

    @Test
    @DisplayName("pais de um unico dia: o filho fica identico ao pai 1")
    void paisDeUmDiaSoHerdamDoPai1() {
        TacticalStudyPlan pai1 = planoDeDias(PORTUGUES, 0, 0);
        TacticalStudyPlan pai2 = planoDeDias(MATEMATICA, 0, 0);

        TacticalStudyPlan filho = operador.crossover(pai1, pai2, 1.0, null);

        // Com um dia so, minDay == maxDay, o corte e esse dia, e a condicao do pai 2 (dia > corte)
        // nunca e satisfeita.
        assertThat(filho.getSchedule().values())
                .extracting(TacticalStudyBlock::subject)
                .containsOnly(PORTUGUES);
    }

    @Test
    @DisplayName("pai 1 vazio produz filho vazio — mesmo com o pai 2 cheio")
    void paiUmVazioProduzFilhoVazio() {
        // Caso-limite que so aparece executando: o intervalo de dias e calculado SO a partir do
        // pai 1. Com ele vazio, minDay fica em Integer.MAX_VALUE e vira o corte, e a condicao
        // "dia > corte" do pai 2 nunca e verdadeira. O pai 2 e descartado por inteiro.
        TacticalStudyPlan pai1 = new TacticalStudyPlan(Map.of());
        TacticalStudyPlan pai2 = planoDeDias(MATEMATICA, 0, 6);

        TacticalStudyPlan filho = operador.crossover(pai1, pai2, 1.0, null);

        assertThat(filho.getSchedule())
                .as("comportamento medido, nao desejado: o material do pai 2 se perde")
                .isEmpty();
    }

    @Test
    @DisplayName("com a mesma semente, o resultado se repete")
    void aMesmaSementeProduzOMesmoFilho() {
        TacticalStudyPlan pai1 = planoDeDias(PORTUGUES, 0, 9);
        TacticalStudyPlan pai2 = planoDeDias(MATEMATICA, 0, 9);

        RandomProvider.setInstance(new Random(42L));
        Map<TimeSlot, TacticalStudyBlock> primeiro = operador.crossover(pai1, pai2, 1.0, null).getSchedule();

        RandomProvider.setInstance(new Random(42L));
        Map<TimeSlot, TacticalStudyBlock> segundo = operador.crossover(pai1, pai2, 1.0, null).getSchedule();

        assertThat(segundo).isEqualTo(primeiro);
    }

    @Test
    @DisplayName("o filho nunca inventa horario que nenhum dos pais tinha")
    void oFilhoNaoInventaHorarios() {
        TacticalStudyPlan pai1 = planoDeDias(PORTUGUES, 0, 5);
        TacticalStudyPlan pai2 = planoDeDias(MATEMATICA, 3, 8);

        for (int tentativa = 0; tentativa < 50; tentativa++) {
            TacticalStudyPlan filho = operador.crossover(pai1, pai2, 1.0, null);
            assertThat(filho.getSchedule().keySet())
                    .allSatisfy(slot -> assertThat(
                            pai1.getSchedule().containsKey(slot) || pai2.getSchedule().containsKey(slot))
                            .as("horario %s nao veio de nenhum pai", slot)
                            .isTrue());
        }
    }
}
