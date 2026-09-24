package com.ia.project.dynamicstudyplanner.ga.factory;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.PlanningItemIndex;
import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;

import java.util.List;
import java.util.Map;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

/**
 * Factory for creating instances of {@link StudyPlan}.
 * <p>
 * This class has a single, critical responsibility: to generate valid, random initial plans
 * that serve as the chromosomes for the starting population of the genetic algorithm. A diverse
 * initial population is essential for the success of the evolutionary process, and the simple
 * randomization used here is the most effective way to achieve that goal.
 */
public final class StudyPlanFactory {

    /**
     * Creates a random {@code StudyPlan} ensuring that all minimum day constraints are met.
     * <p>
     * The process is intentionally straightforward to promote maximum initial diversity:
     * <ol>
     * <li><b>Viability Check:</b> It first ensures the plan is mathematically possible.</li>
     * <li><b>Constraint Fulfillment:</b> It allocates the minimum required days for each subject.</li>
     * <li><b>Random Allocation:</b> It distributes all remaining days purely at random among all subjects.</li>
     * </ol>
     * This unbiased random distribution is crucial for preventing any initial assumptions from
     * limiting the search space that the genetic algorithm can explore.
     *
     * @param index A ordem canônica dos genes, compartilhada por toda a população; {@code null}
     *              deriva uma da lista de disciplinas.
     * @param subjects The list of all subjects to be included in the plan.
     * @param totalDays The total number of days to be allocated in the plan.
     * @param minimumDaysPerItem A map containing the calculated minimum days for each subject.
     * @return A new, randomly generated {@code StudyPlan}.
     * @throws DomainException          se o edital não tiver disciplinas, ou se o piso de dias
     *                                  mínimos exceder o orçamento disponível — situações em que a
     *                                  requisição é compreensível mas as regras de negócio a tornam
     *                                  insatisfazível
     * @throws IllegalArgumentException se {@code totalDays} for negativo, o que é erro de quem
     *                                  chama e não situação de negócio
     */
    public StudyPlan createRandomPlan(
            PlanningItemIndex index,
            List<PlanningItem> items,
            int totalDays,
            Map<PlanningItem, Integer> minimumDaysPerItem
    ) {
        // Guarded explicitly: without this the random distribution below reaches
        // Random.nextInt(0) and surfaces the JDK's "bound must be positive", which tells the
        // caller nothing about what is actually wrong with their exam.
        if (items == null || items.isEmpty()) {
            // Regra de negocio, nao argumento malformado: DomainException para que o cliente receba
            // 422 e nao 400. Ver ADR-0005.
            throw new DomainException(
                    "Cannot build a study plan: there are no planning items."
            );
        }

        if (totalDays < 0) {
            // Continua IllegalArgumentException de proposito: um numero de dias negativo nao e uma
            // situacao em que o aluno possa estar, e sim um defeito de quem chama. A validacao da
            // chamada interna: o orcamento e derivado, nunca vem do corpo da requisicao.
            throw new IllegalArgumentException(
                    "Total available days cannot be negative (received " + totalDays + ")."
            );
        }

        int totalMinimumDays = minimumDaysPerItem.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (totalMinimumDays > totalDays) {
            // O caso central do achado E3. A requisicao e bem formada e compreendida: o edital
            // simplesmente exige mais dias do que o aluno declarou ter. E o que a RFC 9110 chama de
            // 422 — entendido, mas semanticamente impossivel de processar —, e nao 400.
            throw new DomainException(
                    "Total minimum study days required (" + totalMinimumDays +
                            ") exceeds total available days (" + totalDays + ")."
            );
        }

        // Todos os individuos da populacao compartilham esta ordem de genes (pendencia P18): e o
        // que permite recombinar dois planos posicao a posicao, sem consultar item nenhum.
        PlanningItemIndex ordem = index != null ? index : PlanningItemIndex.of(items);
        int[] dias = ordem.projectInts(minimumDaysPerItem, 1);

        int remainingDays = totalDays - totalMinimumDays;
        for (int i = 0; i < remainingDays; i++) {
            dias[RandomProvider.getInstance().nextInt(ordem.size())]++;
        }

        return new StudyPlan(ordem, dias);
    }
}
