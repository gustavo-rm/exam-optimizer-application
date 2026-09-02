package com.ia.project.dynamicstudyplanner.domain.retention;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.time.LocalDate;

/**
 * Contrato dos algoritmos de retenção de memória.
 *
 * <h2>Por que esta interface vive em {@code domain}</h2>
 *
 * Ela foi movida de {@code service.calculation.retention} na etapa 03b. Estava no pacote de quem a
 * <b>implementa</b> ({@code HybridRetentionEngine}), e não no de quem a <b>consome</b> — o que
 * obrigava {@code ga.fitness.constraint} a compilar contra {@code service} só para enxergar o
 * contrato, e fechava um ciclo de dependência entre os dois módulos
 * ({@code docs/qualidade/03-diagnostico-estrutura.md}, achados E4 e E5).
 *
 * <p>Todos os tipos da assinatura — {@link Subject}, {@link SubjectRetentionState},
 * {@link LocalDate} — já são de domínio, então o contrato pertence naturalmente aqui. A decisão está
 * registrada em {@code docs/adr/0001-abstracoes-de-calculo-no-dominio.md}.
 * <p>
 * The macro fitness does not call this interface: its retention term is a mean-field approximation
 * derived from the same forgetting curve, because the macro chromosome has no calendar. See
 * {@code docs/revisao-ag/05-fitness-function.md} §3.3 for the derivation and its limits.
 */
public interface RetentionAlgorithm {

    /**
     * Calculates the probability of recalling the subject's material on a specific date.
     * Uses the Ebbinghaus Forgetting Curve formula.
     *
     * @return Probability between 0.0 and 1.0.
     */
    double calculateRetentionProbability(SubjectRetentionState state, LocalDate targetDate);

    /**
     * Determines if a spaced repetition review is mandatory on or before the target date.
     */
    boolean isReviewMandatory(Subject subject, SubjectRetentionState state, LocalDate targetDate);

    /**
     * Calculates the new memory state after a study/review session.
     *
     * @param currentState The memory state prior to the review.
     * @param reviewDate The date the review occurred.
     * @param performanceGrade Subjective grade of performance (0 to 5) similar to SM-2.
     * @return The updated memory state.
     */
    SubjectRetentionState processReview(SubjectRetentionState currentState, LocalDate reviewDate, int performanceGrade);
}
