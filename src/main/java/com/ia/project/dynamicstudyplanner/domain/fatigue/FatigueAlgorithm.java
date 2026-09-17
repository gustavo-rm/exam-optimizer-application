package com.ia.project.dynamicstudyplanner.domain.fatigue;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;

import java.time.LocalTime;

/**
 * Contrato do modelo de fadiga e energia.
 *
 * <h2>Por que este contrato existe</h2>
 *
 * Introduzido na etapa 03b. Antes, {@code ga.fitness.penalty.FatigueAndSustainabilityPenalty}
 * importava a classe concreta {@code service.calculation.fatigue.FatigueAndEnergyModel} — dependência
 * de implementação atravessando fronteira de módulo, e uma das quatro arestas do ciclo
 * {@code ga} ↔ {@code service} ({@code docs/qualidade/03-diagnostico-estrutura.md}, achado E4).
 *
 * <p>Toda a assinatura usa tipos de domínio, então o contrato pertence aqui. A implementação
 * continua em {@code service}, que é onde deve estar. Decisão registrada em
 * {@code docs/adr/0001-abstracoes-de-calculo-no-dominio.md}.
 *
 * <p>O sufixo {@code ...Algorithm} segue o precedente de {@link
 * com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm} e é a convenção adotada
 * para contratos de cálculo de domínio.
 */
public interface FatigueAlgorithm {

    /**
     * Calcula o fator de risco de esgotamento para um plano tático.
     *
     * @return fator entre 0,0 e 1,0, em que 1,0 significa ausência de risco
     */
    double calculateBurnoutRisk(TacticalStudyPlan plan, StudentState state);

    /**
     * Nível de energia esperado num horário do dia, dado o cronotipo do estudante.
     *
     * @return valor entre 0,0 e 1,0
     */
    double getExpectedEnergyLevel(LocalTime time, Chronotype chronotype);
}
