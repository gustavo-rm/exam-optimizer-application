package com.ia.project.dynamicstudyplanner.ga.strategy.crossover;

import com.ia.project.dynamicstudyplanner.util.RandomProvider;

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

    private ChildGeneRepair() {
    }

    /**
     * Soma ou remove dias, um a um e em posições sorteadas, até a soma bater com o orçamento.
     *
     * <p><b>Modifica o vetor recebido.</b> Ao remover, respeita o piso de dias mínimos de cada
     * disciplina; uma posição que já esteja no piso é retirada do sorteio, e é isso que impede o
     * laço de girar para sempre quando nenhuma pode ceder mais dias. Quando <b>nenhuma</b> pode
     * ceder, o laço termina com a soma ainda acima do alvo — comportamento preservado da versão
     * anterior e travado por {@code ChildGeneRepairTest}.
     *
     * <h2>Por que os candidatos são um vetor e não uma lista (pendência P18)</h2>
     *
     * O sorteio é por <b>posição</b>: {@code candidatos[i]} guarda a posição do gene que ainda pode
     * ceder dias. Retirar um candidato desloca o resto para a esquerda, exatamente como
     * {@code ArrayList.remove} fazia — o algoritmo é o mesmo, e a ordem relativa dos candidatos
     * também. O que sumiu foi o custo por volta: a versão com {@code Map<Subject, Integer>} pagava
     * um cálculo de hash na leitura e outro na escrita de cada dia ajustado.
     *
     * @param genes    dias por posição do filho; <b>alterado no lugar</b>
     * @param minimums piso por posição, alinhado ao mesmo índice de {@code genes}
     * @param targetDaySum orçamento total de dias que a soma precisa atingir
     */
    static void repairToTargetSum(int[] genes, int[] minimums, int targetDaySum) {
        int currentDaySum = 0;
        for (int dias : genes) {
            currentDaySum += dias;
        }
        int difference = targetDaySum - currentDaySum;

        int[] candidates = new int[genes.length];
        for (int i = 0; i < genes.length; i++) {
            candidates[i] = i;
        }
        int candidateCount = genes.length;

        while (difference != 0 && candidateCount > 0) {
            int escolhido = RandomProvider.getInstance().nextInt(candidateCount);
            int posicao = candidates[escolhido];

            if (difference > 0) {
                genes[posicao]++;
                difference--;
                continue;
            }

            if (genes[posicao] > minimums[posicao]) {
                genes[posicao]--;
                difference++;
            } else {
                // Ja esta no piso: sai do sorteio. E o que garante o termino do laco.
                System.arraycopy(candidates, escolhido + 1, candidates, escolhido,
                        candidateCount - escolhido - 1);
                candidateCount--;
            }
        }
    }
}
