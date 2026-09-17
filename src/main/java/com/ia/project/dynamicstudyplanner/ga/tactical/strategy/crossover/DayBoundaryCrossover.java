package com.ia.project.dynamicstudyplanner.ga.tactical.strategy.crossover;

import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Recombina dois cronogramas cortando na virada de um dia.
 *
 * <p>O filho herda os dias até o corte do primeiro pai e os dias seguintes do segundo — por exemplo,
 * segunda a quarta de um, quinta a domingo do outro. Cortar na fronteira do dia, e não em qualquer
 * ponto, preserva a estrutura interna de cada dia: a acumulação de fadiga e as restrições que valem
 * dentro de uma jornada continuam coerentes em ambos os lados do corte.
 *
 * <h2>Limitação conhecida: o intervalo vem só do primeiro pai</h2>
 *
 * O dia de corte é sorteado dentro do intervalo de dias do <b>primeiro</b> pai. Se ele estiver
 * vazio, o intervalo degenera e o filho sai vazio — <b>o segundo pai é descartado por inteiro</b>.
 * Não é o comportamento que se desejaria, mas é o que este operador faz hoje, e está travado por
 * {@code DayBoundaryCrossoverTest.paiUmVazioProduzFilhoVazio} para que a mudança seja uma decisão
 * e não um efeito colateral. Registrado como pendência <b>P13</b> em
 * {@code docs/qualidade/04b-correcao-escrita.md}.
 */
public class DayBoundaryCrossover implements TacticalCrossoverStrategy {

    @Override
    public TacticalStudyPlan crossover(TacticalStudyPlan parent1, TacticalStudyPlan parent2,
                                       double crossoverRate, EvolutionContext context) {
        if (RandomProvider.getInstance().nextDouble() > crossoverRate) {
            return parent1;
        }

        int cutoffDayOfYear = drawCutoffDay(parent1);

        Map<TimeSlot, TacticalStudyBlock> childSchedule = new HashMap<>();
        copyDays(parent1, childSchedule, day -> day <= cutoffDayOfYear);
        copyDays(parent2, childSchedule, day -> day > cutoffDayOfYear);

        return new TacticalStudyPlan(childSchedule);
    }

    /**
     * Sorteia o dia de corte dentro do intervalo coberto pelo plano.
     *
     * <p>Com um único dia — ou com o plano vazio — não há o que sortear e o corte é o próprio
     * primeiro dia. No caso vazio isso vale {@link Integer#MAX_VALUE}, que é o que produz a
     * limitação descrita no Javadoc da classe.
     */
    private int drawCutoffDay(TacticalStudyPlan plan) {
        int firstDay = Integer.MAX_VALUE;
        int lastDay = Integer.MIN_VALUE;

        for (TimeSlot slot : plan.getSchedule().keySet()) {
            int day = slot.startTime().getDayOfYear();
            firstDay = Math.min(firstDay, day);
            lastDay = Math.max(lastDay, day);
        }

        if (lastDay <= firstDay) {
            return firstDay;
        }
        return firstDay + RandomProvider.getInstance().nextInt(lastDay - firstDay + 1);
    }

    /** Copia para {@code target} os blocos do plano cujo dia satisfaz {@code dayFilter}. */
    private void copyDays(TacticalStudyPlan source, Map<TimeSlot, TacticalStudyBlock> target,
                          IntPredicate dayFilter) {
        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : source.getSchedule().entrySet()) {
            if (dayFilter.test(entry.getKey().startTime().getDayOfYear())) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
