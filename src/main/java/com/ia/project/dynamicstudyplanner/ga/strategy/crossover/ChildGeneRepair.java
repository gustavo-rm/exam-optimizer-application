package com.ia.project.dynamicstudyplanner.ga.strategy.crossover;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ajusta os genes de um filho para que a soma de dias volte a bater com o orçamento.
 *
 * <h2>Por que esta classe existe</h2>
 *
 * Recombinar dois planos válidos quase nunca produz um plano válido: pegar metade dos dias de um pai
 * e metade do outro dá uma soma que não é o orçamento de nenhum dos dois. Sem reparo, o algoritmo
 * genético evoluiria indivíduos que a API não pode entregar.
 *
 * <p>Até a etapa 04b este reparo estava <b>duplicado byte a byte</b> em
 * {@link RepairingCrossover} e {@link WeightedAverageCrossover} — 21 linhas idênticas, incluindo a
 * guarda contra laço infinito e o Javadoc, palavra por palavra (achado L2 de
 * {@code docs/qualidade/04-diagnostico-escrita.md}).
 *
 * <p>O risco não era o volume. Qual dos dois operadores roda em cada recombinação depende de um
 * sorteio em {@link HybridCrossover}: corrigir um defeito numa cópia e não na outra produziria
 * planos inválidos <b>de forma intermitente</b>, com o sintoma aparecendo em uma execução a cada
 * duas. Com uma implementação só, a correção vale para os dois caminhos por construção.
 */
final class ChildGeneRepair {

    /** Piso implícito quando o contexto não declara mínimo para uma disciplina. */
    private static final int DEFAULT_MINIMUM_DAYS = 1;

    private ChildGeneRepair() {
    }

    /**
     * Soma ou remove dias, um a um e em disciplinas sorteadas, até a soma bater com o orçamento.
     *
     * <p><b>Modifica o mapa recebido.</b> Ao remover, respeita o piso de dias mínimos de cada
     * disciplina; uma disciplina que já esteja no piso é retirada do sorteio, e é isso que impede o
     * laço de girar para sempre quando nenhuma pode ceder mais dias. Quando <b>nenhuma</b> pode
     * ceder, o laço termina com a soma ainda acima do alvo — comportamento preservado da versão
     * anterior e travado por {@code ChildGeneRepairTest}.
     *
     * @param childGenes           dias por disciplina do filho; <b>alterado no lugar</b>
     * @param targetDaySum         orçamento total de dias que a soma precisa atingir
     * @param minimumDaysPerSubject piso por disciplina; ausência significa {@value #DEFAULT_MINIMUM_DAYS}
     */
    static void repairToTargetSum(Map<Subject, Integer> childGenes, int targetDaySum,
                                  Map<Subject, Integer> minimumDaysPerSubject) {
        int currentDaySum = childGenes.values().stream().mapToInt(Integer::intValue).sum();
        int difference = targetDaySum - currentDaySum;
        List<Subject> candidates = new ArrayList<>(childGenes.keySet());

        while (difference != 0 && !candidates.isEmpty()) {
            Subject subject = candidates.get(RandomProvider.getInstance().nextInt(candidates.size()));
            int currentDays = childGenes.get(subject);

            if (difference > 0) {
                childGenes.put(subject, currentDays + 1);
                difference--;
                continue;
            }

            int minimumDays = minimumDaysPerSubject.getOrDefault(subject, DEFAULT_MINIMUM_DAYS);
            if (currentDays > minimumDays) {
                childGenes.put(subject, currentDays - 1);
                difference++;
            } else {
                // Ja esta no piso: sai do sorteio. E o que garante o termino do laco.
                candidates.remove(subject);
            }
        }
    }
}
