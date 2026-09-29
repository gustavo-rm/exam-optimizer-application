package com.ia.project.dynamicstudyplanner.ga.tactical;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation.MethodologyMutation;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O operador de metodologia — que <b>não tinha teste nenhum</b> antes de ser ligado.
 *
 * <h2>Por que a ausência deste teste era um problema a resolver antes, e não depois</h2>
 *
 * Um operador genético sem teste é fonte silenciosa de perda de determinismo, e este já era um caso:
 * ele iterava {@code new HashMap<>(plan.getSchedule())} sorteando um número por entrada, então
 * <b>qual slot recebia qual sorteio</b> saía de {@code TimeSlot.hashCode()}. Nada era irreprodutível
 * hoje — a ordem de um {@code HashMap} é estável para hashes fixos —, mas todo plano produzido passava
 * a depender da dispersão do registro, e nenhum teste apontaria para a causa se ela mudasse.
 *
 * <p>{@link #osSorteiosSaoAtribuidosEmOrdemDeCalendario()} é o teste que fixa a correção, e ele a fixa
 * de forma direta: reconstrói a sequência de sorteios a partir da semente e exige que o i-ésimo
 * sorteio caia no i-ésimo slot <b>do calendário</b>. Sob a versão anterior, o i-ésimo sorteio caía no
 * i-ésimo slot da ordem de hash.
 */
@DisplayName("MethodologyMutation")
class MethodologyMutationTest {

    private static final long SEED = 20260929L;
    private static final int SLOTS = 8;
    private static final PlanningItem ITEM = new PlanningItem("t-1", "Algebra", 3);

    private final MethodologyMutation mutation = new MethodologyMutation();

    /**
     * Um plano com {@value #SLOTS} slots de uma hora, inseridos <b>fora</b> da ordem de calendário.
     *
     * <p>Inseridos ao contrário de propósito: {@code TacticalStudyPlan} preserva a ordem do mapa que
     * recebe, então um operador que confiasse na ordem de inserção passaria num plano montado em
     * ordem e falharia neste. O operador tem de ordenar por conta própria.
     */
    private static TacticalStudyPlan reversedInsertionOrder() {
        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();
        for (int index = SLOTS - 1; index >= 0; index--) {
            LocalDateTime start = LocalDateTime.parse("2026-09-01T08:00").plusHours(index);
            schedule.put(new TimeSlot(start, start.plusHours(1)),
                    new TacticalStudyBlock(ITEM, StudyMethodology.PASSIVE_READING, 60));
        }
        return new TacticalStudyPlan(schedule);
    }

    /** As metodologias na ordem em que o operador as sorteia, replicadas da mesma semente. */
    private static List<StudyMethodology> drawnInOrder() {
        Random replay = new Random(SEED);
        StudyMethodology[] values = StudyMethodology.values();
        List<StudyMethodology> drawn = new ArrayList<>(SLOTS);
        for (int index = 0; index < SLOTS; index++) {
            replay.nextDouble();                       // o teste de taxa, sempre aprovado com 1.0
            drawn.add(values[replay.nextInt(values.length)]);
        }
        return drawn;
    }

    @Test
    @DisplayName("os sorteios sao atribuidos aos slots em ordem de calendario, nao de hash")
    void osSorteiosSaoAtribuidosEmOrdemDeCalendario() {
        RandomProvider.setInstance(new Random(SEED));
        TacticalStudyPlan mutated = mutation.mutate(reversedInsertionOrder(), 1.0, null);

        List<TimeSlot> calendar = TacticalSlots.ordered(mutated);
        List<StudyMethodology> drawn = drawnInOrder();

        for (int index = 0; index < SLOTS; index++) {
            StudyMethodology expected = drawn.get(index) == StudyMethodology.SPACED_REPETITION_REVIEW
                    ? StudyMethodology.PASSIVE_READING   // sorteio descartado, bloco fica como estava
                    : drawn.get(index);

            assertThat(mutated.getSchedule().get(calendar.get(index)).methodology())
                    .as("slot %d do calendario (%s) recebe o sorteio %d",
                            index, calendar.get(index).startTime(), index)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("a mesma semente produz o mesmo plano")
    void aMesmaSementeProduzOMesmoPlano() {
        RandomProvider.setInstance(new Random(SEED));
        TacticalStudyPlan first = mutation.mutate(reversedInsertionOrder(), 0.5, null);
        RandomProvider.setInstance(new Random(SEED));
        TacticalStudyPlan second = mutation.mutate(reversedInsertionOrder(), 0.5, null);

        assertThat(signature(second)).isEqualTo(signature(first));
    }

    @Test
    @DisplayName("nunca cria revisao espacada, que e competencia do reparador de retencao")
    void nuncaCriaRevisaoEspacada() {
        RandomProvider.setInstance(new Random(SEED));

        // Taxa 1.0 e muitas passadas: se o operador pudesse criar revisao, criaria aqui.
        TacticalStudyPlan plan = reversedInsertionOrder();
        for (int pass = 0; pass < 50; pass++) {
            plan = mutation.mutate(plan, 1.0, null);
        }

        assertThat(plan.getSchedule().values())
                .noneMatch(block ->
                        block.methodology() == StudyMethodology.SPACED_REPETITION_REVIEW);
    }

    @Test
    @DisplayName("mexe so na metodologia: slots, item e duracao ficam intactos")
    void mexeSoNaMetodologia() {
        RandomProvider.setInstance(new Random(SEED));
        TacticalStudyPlan original = reversedInsertionOrder();
        TacticalStudyPlan mutated = mutation.mutate(original, 1.0, null);

        assertThat(mutated.getSchedule().keySet()).isEqualTo(original.getSchedule().keySet());
        assertThat(mutated.getSchedule().values())
                .allSatisfy(block -> {
                    assertThat(block.item()).isEqualTo(ITEM);
                    assertThat(block.durationMinutes()).isEqualTo(60L);
                });
    }

    /** Assinatura em ordem de calendário, imune à ordem de iteração do mapa. */
    private static String signature(TacticalStudyPlan plan) {
        StringBuilder text = new StringBuilder();
        TacticalSlots.ordered(plan).forEach(slot -> text
                .append(slot.startTime()).append('=')
                .append(plan.getSchedule().get(slot).methodology()).append('|'));
        return text.toString();
    }
}
