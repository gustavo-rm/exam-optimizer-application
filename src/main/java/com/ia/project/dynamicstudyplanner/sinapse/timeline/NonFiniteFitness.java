package com.ia.project.dynamicstudyplanner.sinapse.timeline;

import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.Population;

/**
 * No caminho do {@code ga-timeline}, aptidão não finita vale o pior valor possível (EOA-12,
 * ADR-0011).
 *
 * <h2>O defeito que isto fecha</h2>
 *
 * O plano tático vazio pontua {@code NaN}: {@code DailyLoadBudgetObjective.overloadRatio} não tem o
 * que comparar quando o plano não aloca dia nenhum, e o {@code NaN} atravessa o limite e a soma.
 * {@code Population.getFittest} ordena com {@code Double.compare}, para o qual {@code NaN} é maior
 * que todo número, então o vazio vencia qualquer plano não vazio, e o elitismo o carregava até a
 * última geração. O motor recusava com {@code plan-would-be-empty} instâncias em que existe plano
 * (diagnóstico do EOA-10, §8e).
 *
 * <h2>Por que aqui, e não no termo nem na seleção</h2>
 *
 * Dar ao plano vazio um {@code F} finito dentro do termo mudaria a função objetivo que a tese
 * descreve, e {@code Population} e {@code TournamentSelection} estão na lista de não-mexer de
 * {@code CLAUDE.md} §4. A aptidão é atribuída por {@code Population.calculateFitness}, e
 * {@link TimelineSearch} é o único que a lê neste caminho; corrigir o valor em cache logo depois do
 * cálculo, antes de qualquer leitor, só muda o que não é número.
 *
 * <h2>Por que {@code -Infinity}</h2>
 *
 * <ul>
 *   <li>{@code 0.0} não serve: a seleção ordena pelo bruto quando ele é negativo num plano tático
 *       ({@code FitnessBreakdown.selectionScore()}), e planos não vazios pontuam abaixo de zero aqui
 *       (−0,6057 no reprodutor viável). O vazio continuaria vencendo.</li>
 *   <li>Não há no código um piso finito da aptidão de seleção; o sentinela de "pior" que já existe é
 *       {@code Double.NEGATIVE_INFINITY}, com que {@code GeneticAlgorithm} inicia
 *       {@code bestFitnessSoFar}.</li>
 *   <li>Os leitores deste caminho só comparam: {@code Double.compare} em {@code getFittest} põe
 *       {@code -Infinity} abaixo de todo número, e o {@code >} do torneio sempre o troca por um
 *       contendor finito. {@code SearchVitality} conta valores distintos num {@code Set}, onde
 *       {@code -Infinity} é um valor como era {@code NaN}. Nenhum leitor do caminho soma, tira média
 *       ou registra em log esta aptidão.</li>
 * </ul>
 *
 * <p>Todo valor finito sai intacto, bit a bit: uma execução sem aptidão não finita produz exatamente
 * o plano de antes, e o caminho do {@code ga} não passa por aqui.
 */
public final class NonFiniteFitness {

    /** O pior valor possível; ver a nota da classe. */
    public static final double WORST = Double.NEGATIVE_INFINITY;

    private NonFiniteFitness() {
    }

    /**
     * A regra, sobre um valor.
     *
     * @param fitness uma aptidão de seleção
     * @return o próprio valor quando finito; {@link #WORST} para {@code NaN}, {@code +Infinity} e
     *         {@code -Infinity}
     */
    public static double orWorst(double fitness) {
        return Double.isFinite(fitness) ? fitness : WORST;
    }

    /**
     * Aplica a regra à aptidão em cache de cada indivíduo.
     *
     * <p>Não reavalia nada e não consome sorteio: lê o valor que {@code calculateFitness} acabou de
     * gravar e só regrava o que não é finito.
     *
     * @param population uma população com a aptidão já calculada
     */
    public static void demote(Population population) {
        for (int index = 0; index < population.getSize(); index++) {
            Individual individual = population.getIndividual(index);
            if (!Double.isFinite(individual.getFitness())) {
                individual.setFitness(orWorst(individual.getFitness()));
            }
        }
    }
}
