package com.ia.project.dynamicstudyplanner.ga.fitness.objective;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

/**
 * One objective of the aggregated fitness.
 * <p>
 * <b>Contract:</b> {@link #calculateReward} must return a value in {@code [0,1]}, and the weights of
 * all registered objectives must sum to 1 ({@code FitnessEvaluator} asserts this at construction).
 * Both rules exist so the aggregate stays in {@code [0,1]} and comparable across exams, students and
 * releases — an objective returning the exam's own units would dominate or vanish depending on the
 * payload (docs/revisao-ag/01-auditoria-fitness.md §2.1.4).
 * <p>
 * Every objective, its formula, its weight and the learning-science result behind it are documented
 * in {@code docs/revisao-ag/05-fitness-function.md}. Adding one here means adding it there.
 */
public interface FitnessObjective {
    double calculateReward(StudyPlan plan, EvolutionContext context);
    double getWeight();

    /**
     * Nome estável deste termo na decomposição da fitness.
     *
     * <p><b>Vira chave de API e de análise da tese</b>, então renomear um é quebra de contrato e
     * não refatoração. O padrão é o nome da classe, que serve a qualquer termo novo sem exigir
     * nada de quem o escreve; os três objetivos de produção o sobrescrevem com o nome do conceito,
     * porque {@code syllabusMastery} diz mais a quem lê um plano do que
     * {@code ScoreGainObjective}.
     *
     * @return o nome do termo
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
