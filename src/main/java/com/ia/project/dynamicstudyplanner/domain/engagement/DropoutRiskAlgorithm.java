package com.ia.project.dynamicstudyplanner.domain.engagement;

import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;

/**
 * Contrato do preditor de risco de evasão.
 *
 * <h2>Por que este contrato existe</h2>
 *
 * Introduzido na etapa 03b, pelo mesmo motivo de {@link
 * com.ia.project.dynamicstudyplanner.domain.fatigue.FatigueAlgorithm}: a penalidade em
 * {@code ga.fitness.penalty.DropoutRiskPenalty} importava a classe concreta
 * {@code service.calculation.engagement.DropoutRiskPredictor}, o que era uma das quatro arestas do
 * ciclo {@code ga} ↔ {@code service} ({@code docs/qualidade/03-diagnostico-estrutura.md}, E4).
 *
 * <p>Decisão registrada em {@code docs/adr/0001-abstracoes-de-calculo-no-dominio.md}.
 */
public interface DropoutRiskAlgorithm {

    /**
     * Calcula a pontuação de risco de evasão para um plano tático.
     *
     * @return pontuação entre 0,0 (sem risco) e 1,0 (risco máximo)
     */
    double calculateRiskScore(TacticalStudyPlan plan, EngagementProfile engagement, StudentState state);
}
