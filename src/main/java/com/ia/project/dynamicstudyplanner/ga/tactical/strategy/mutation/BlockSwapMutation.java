package com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.tactical.TacticalSlots;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Troca dois blocos de lugar — <b>o operador de ordem</b>, e o que faltava para a v2 existir.
 *
 * <h2>Por que este operador é o centro da v2, e por que os dois que já existiam não bastavam</h2>
 *
 * A hipótese da v2 é que a <b>ordem</b> vire gene, em vez de ser derivada de uma alocação macro. Dos
 * dois operadores táticos que o repositório já tinha, nenhum a move: {@code MethodologyMutation}
 * troca a intensidade de um bloco e deixa o bloco onde está, e {@code DayBoundaryCrossover} recombina
 * dias inteiros de dois pais — ele mistura ordens existentes, mas não gera ordem nova. Sem um
 * operador que reordene, a população nunca visita uma permutação que a geração inicial não tenha
 * sorteado, e a busca degenera numa seleção sobre a amostra inicial.
 *
 * <h2>Troca, e não deslocamento</h2>
 *
 * A troca preserva o multiconjunto de blocos por construção: os mesmos blocos, em posições
 * diferentes. Um deslocamento — tirar de uma posição e inserir noutra — preservaria a ordem relativa
 * do resto e exploraria menos por sorteio, mas exigiria reindexar o mapa inteiro. A troca mantém o
 * reparo com um trabalho a menos: ele só precisa reordenar para respeitar pré-requisitos, nunca
 * recriar um bloco perdido.
 *
 * <p>O bloco leva sua própria duração para a posição nova, e é o reparador que recalcula os instantes
 * — ver {@code sinapse.timeline.TimelineRepairer}. É por isso que a troca pode ignorar se os dois
 * slots têm a mesma duração: depois dela o calendário é reconstruído de todo modo.
 *
 * <h2>Determinismo</h2>
 *
 * Os slots são lidos em ordem de calendário por {@link TacticalSlots}, então <b>quais</b> posições um
 * par de sorteios designa não depende de {@code TimeSlot.hashCode()}. Ver o Javadoc daquela classe
 * para o defeito que isso evita e por que "era reprodutível" não o desculpava.
 */
public class BlockSwapMutation implements TacticalMutationStrategy {

    @Override
    public TacticalStudyPlan mutate(TacticalStudyPlan plan, double mutationRate,
            EvolutionContext context) {

        Map<TimeSlot, TacticalStudyBlock> schedule = TacticalSlots.byCalendar(plan);
        List<TimeSlot> slots = new ArrayList<>(schedule.keySet());
        if (slots.size() < 2) {
            // Com menos de dois blocos não existe troca possível. Nenhum sorteio é consumido, para
            // que o número de saques seja função só do tamanho do plano.
            return plan;
        }

        // Uma tentativa de troca por bloco: a taxa vale por posição, como nos outros operadores,
        // e não por plano — assim a intensidade da mutação escala com o tamanho do cromossomo.
        for (int index = 0; index < slots.size(); index++) {
            if (RandomProvider.getInstance().nextDouble() >= mutationRate) {
                continue;
            }
            int other = RandomProvider.getInstance().nextInt(slots.size());
            if (other == index) {
                continue;
            }
            TimeSlot here = slots.get(index);
            TimeSlot there = slots.get(other);
            TacticalStudyBlock carried = schedule.get(here);
            schedule.put(here, schedule.get(there));
            schedule.put(there, carried);
        }

        return new TacticalStudyPlan(schedule);
    }
}
