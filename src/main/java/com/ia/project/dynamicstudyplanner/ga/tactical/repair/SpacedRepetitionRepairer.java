package com.ia.project.dynamicstudyplanner.ga.tactical.repair;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Repara um cronograma que deixou de fora uma revisão obrigatória.
 *
 * <p>Cruzamento e mutação podem produzir um plano que viola a
 * {@code MandatoryReviewConstraint} — uma disciplina cuja curva de retenção exige revisão hoje
 * simplesmente não tem bloco de revisão agendado. Em vez de descartar o indivíduo, este reparador
 * <b>troca</b> o bloco de menor retorno pela revisão que faltava.
 *
 * <p>Trocar, e não acrescentar, é deliberado: o cronograma já respeita a disponibilidade do aluno, e
 * inserir mais um bloco criaria tempo que ele não tem. O preço é que a agenda mantém o tamanho e uma
 * atividade é sacrificada — daí a escolha recair sobre a de menor
 * {@linkplain #reviewValueOf(TacticalStudyBlock) valor de revisão}.
 */
@Component
public class SpacedRepetitionRepairer implements ChromosomeRepairer {

    private final RetentionAlgorithm retentionAlgorithm;

    public SpacedRepetitionRepairer(RetentionAlgorithm retentionAlgorithm) {
        this.retentionAlgorithm = retentionAlgorithm;
    }

    @Override
    public TacticalStudyPlan repair(TacticalStudyPlan plan, EvolutionContext context) {
        if (context.retentionProfile() == null) {
            return plan;
        }

        Map<TimeSlot, TacticalStudyBlock> schedule = new HashMap<>(plan.getSchedule());

        for (Subject subject : context.importanceScores().keySet()) {
            if (!needsReview(subject, context) || alreadyHasReview(schedule, subject)) {
                continue;
            }
            scheduleReview(schedule, subject);
        }

        return new TacticalStudyPlan(schedule);
    }

    /** A curva de retenção exige revisão desta disciplina na data de início do plano? */
    private boolean needsReview(Subject subject, EvolutionContext context) {
        return retentionAlgorithm.isReviewMandatory(
                subject, context.retentionProfile().getState(subject), context.planStartDate());
    }

    private boolean alreadyHasReview(Map<TimeSlot, TacticalStudyBlock> schedule, Subject subject) {
        return schedule.values().stream()
                .anyMatch(block -> block.subject().equals(subject)
                        && block.methodology() == StudyMethodology.SPACED_REPETITION_REVIEW);
    }

    /**
     * Sobrescreve o bloco de menor valor de revisão com a revisão obrigatória da disciplina.
     *
     * <p>Não faz nada quando a agenda está vazia: não há bloco a sacrificar, e a revisão obrigatória
     * fica sem ser agendada. É o comportamento anterior, travado por
     * {@code SpacedRepetitionRepairerTest.agendaVaziaContinuaVazia}.
     */
    private void scheduleReview(Map<TimeSlot, TacticalStudyBlock> schedule, Subject subject) {
        TimeSlot weakestSlot = null;
        double lowestValue = Double.MAX_VALUE;

        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : schedule.entrySet()) {
            double value = reviewValueOf(entry.getValue());
            if (value < lowestValue) {
                lowestValue = value;
                weakestSlot = entry.getKey();
            }
        }

        if (weakestSlot != null) {
            schedule.put(weakestSlot, new TacticalStudyBlock(
                    subject, StudyMethodology.SPACED_REPETITION_REVIEW, weakestSlot.getDurationMinutes()));
        }
    }

    /**
     * Quanto vale manter este bloco: carga cognitiva da disciplina vezes o quanto a metodologia
     * costuma fixar o conteúdo.
     *
     * <p>É uma heurística grosseira — o bloco mais barato de sacrificar é o de assunto leve estudado
     * por um método pouco retentivo. Não pondera importância da disciplina nem proximidade da prova.
     */
    private double reviewValueOf(TacticalStudyBlock block) {
        return block.subject().cognitiveLoad() * block.methodology().getExpectedRetentionMultiplier();
    }
}
