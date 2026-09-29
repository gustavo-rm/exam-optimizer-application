package com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.List;
import java.util.Map;
import com.ia.project.dynamicstudyplanner.ga.tactical.TacticalSlots;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

/**
 * Troca a metodologia de um bloco — de leitura passiva para recordação ativa, por exemplo.
 *
 * <p>É o operador que procura a <b>intensidade</b> certa para cada faixa do calendário, e o único
 * eixo do cromossomo tático que não é ordem. Ele não move blocos; ver
 * {@code BlockSwapMutation} para o eixo de ordem.
 *
 * <h2>Os slots são percorridos em ordem de calendário, e isso é correção, não estilo</h2>
 *
 * A versão anterior iterava {@code new HashMap<>(plan.getSchedule())} sorteando <b>um número por
 * entrada</b>. A contagem de sorteios era estável, mas <b>qual slot recebia qual sorteio</b> era
 * decidido por {@code TimeSlot.hashCode()} — de modo que acrescentar um campo ao registro, ou uma
 * atualização de JDK que mudasse a dispersão, alteraria silenciosamente todo plano produzido. Ver
 * {@link TacticalSlots} para o argumento completo e para por que "era reprodutível" não o salvava.
 *
 * <h2>A revisão espaçada não entra por sorteio</h2>
 *
 * Quando o sorteio cai em {@code SPACED_REPETITION_REVIEW} a mutação é <b>descartada</b> e o bloco
 * fica como estava: quem agenda revisão é a curva de retenção, por
 * {@code SpacedRepetitionRepairer}, e deixar a mutação criar revisões faria o operador competir com
 * o reparador. O sorteio é consumido de todo jeito, para que a sequência de números aleatórios não
 * dependa do resultado do próprio sorteio — o que tornaria o número de saques função do estado do
 * plano e quebraria a reprodutibilidade que {@code GaResultadoInalteradoTest} exige do AG macro.
 */
public class MethodologyMutation implements TacticalMutationStrategy {

    @Override
    public TacticalStudyPlan mutate(TacticalStudyPlan plan, double mutationRate,
            EvolutionContext context) {

        Map<TimeSlot, TacticalStudyBlock> newSchedule = TacticalSlots.byCalendar(plan);
        List<TimeSlot> slots = List.copyOf(newSchedule.keySet());
        StudyMethodology[] methodologies = StudyMethodology.values();

        for (TimeSlot slot : slots) {
            if (RandomProvider.getInstance().nextDouble() >= mutationRate) {
                continue;
            }
            StudyMethodology drawn =
                    methodologies[RandomProvider.getInstance().nextInt(methodologies.length)];
            if (drawn == StudyMethodology.SPACED_REPETITION_REVIEW) {
                continue;
            }
            TacticalStudyBlock block = newSchedule.get(slot);
            newSchedule.put(slot,
                    new TacticalStudyBlock(block.item(), drawn, block.durationMinutes()));
        }

        return new TacticalStudyPlan(newSchedule);
    }
}
