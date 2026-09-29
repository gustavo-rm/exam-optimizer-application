package com.ia.project.dynamicstudyplanner.ga.tactical;

import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A ordem canônica dos slots de um cromossomo tático: o calendário, e nunca o {@code hashCode}.
 *
 * <h2>Por que esta classe existe</h2>
 *
 * É o análogo tático de {@code PlanningItemIndex}, e existe pela mesma razão que aquela: <b>um
 * operador genético que itera um mapa por ordem de hash acopla o sorteio ao layout interno do
 * mapa</b>. O Javadoc de {@code PlanningItemIndex} já registra o argumento para o cromossomo macro;
 * os operadores táticos não tinham equivalente e faziam exatamente o que ele proíbe.
 *
 * <p>{@code MethodologyMutation} iterava {@code new HashMap<>(plan.getSchedule())} sorteando <b>um
 * número por entrada</b>, então qual slot recebia qual sorteio era decidido por
 * {@code TimeSlot.hashCode()}. {@code SpacedRepetitionRepairer} escolhia o bloco de menor valor
 * iterando um {@code HashMap}, então um empate era desfeito pela mesma coisa.
 *
 * <h2>Isto era reprodutível, e ainda assim era defeito</h2>
 *
 * A distinção importa e é fácil de errar. A ordem de iteração de um {@code HashMap} é
 * <b>determinística</b> dados os mesmos {@code hashCode} e a mesma ordem de inserção — ela não é
 * aleatorizada por execução, ao contrário de {@code Map.copyOf}, cuja semente muda por JVM e que
 * causou a falha intermitente de 1 em 3 registrada em {@code GoalPriorityImportance}.
 *
 * <p>Então o defeito não é irreprodutibilidade hoje. É que o plano entregue ao aluno passa a depender
 * de {@code TimeSlot.hashCode()}: acrescentar um campo ao registro, ou uma atualização de JDK que
 * mude a dispersão, altera silenciosamente todo plano produzido — e nenhum teste apontaria para a
 * causa. Ordenar pelo calendário troca uma dependência invisível por uma declarada.
 */
public final class TacticalSlots {

    /** Calendário: começo, depois fim. Total, porque dois slots não podem coincidir nos dois. */
    public static final Comparator<TimeSlot> BY_CALENDAR =
            Comparator.comparing(TimeSlot::startTime).thenComparing(TimeSlot::endTime);

    private TacticalSlots() {
    }

    /**
     * Os slots de um plano, em ordem de calendário.
     *
     * @param plan o cromossomo
     * @return os slots, ordenados; lista imutável
     */
    public static List<TimeSlot> ordered(TacticalStudyPlan plan) {
        return plan.getSchedule().keySet().stream().sorted(BY_CALENDAR).toList();
    }

    /**
     * O cronograma reindexado em ordem de calendário.
     *
     * <p>{@code LinkedHashMap} e não {@code HashMap}: {@code TacticalStudyPlan} preserva a ordem do
     * mapa que recebe, e essa ordem alcança tanto os operadores quanto
     * {@code extractDaysPerItem}, que a repassa a {@code PlanningItemIndex.of}. Um mapa ordenado por
     * hash ali faz a ordem canônica dos genes do plano depender de hash.
     *
     * @param plan o cromossomo
     * @return um mapa mutável, em ordem de calendário
     */
    public static Map<TimeSlot, TacticalStudyBlock> byCalendar(TacticalStudyPlan plan) {
        Map<TimeSlot, TacticalStudyBlock> ordered = new LinkedHashMap<>();
        ordered(plan).forEach(slot -> ordered.put(slot, plan.getSchedule().get(slot)));
        return ordered;
    }
}
