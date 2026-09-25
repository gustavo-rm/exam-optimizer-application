package com.ia.project.dynamicstudyplanner.domain.retention;

import java.time.LocalDate;

/**
 * Estado de memória do estudante para um único {@code PlanningItem}.
 *
 * <p>Inspirado nos estados de memória do SM-2 e da Half-Life Regression.
 *
 * <p>Chamava-se {@code SubjectRetentionState} enquanto a unidade de planejamento era a disciplina
 * de concurso. A unidade é o item desde EOA-4, e o nome antigo era a última peça do vocabulário de
 * concurso ainda de pé fora do contrato da plataforma — onde {@code subjectId} é conceito dela e
 * permanece.
 */
public class ItemRetentionState {

    // Default SM-2 starting Easiness Factor
    private static final double DEFAULT_EASINESS_FACTOR = 2.5;

    private int repetitionCount;
    private double easinessFactor;
    private double currentIntervalDays; // Equivalent to Stability (S)
    private LocalDate lastReviewDate;

    public ItemRetentionState(LocalDate lastReviewDate) {
        this.repetitionCount = 0;
        this.easinessFactor = DEFAULT_EASINESS_FACTOR;
        this.currentIntervalDays = 1.0; // Start with a 1-day interval
        this.lastReviewDate = lastReviewDate;
    }

    public ItemRetentionState(int repetitionCount, double easinessFactor, double currentIntervalDays,
            LocalDate lastReviewDate) {
        this.repetitionCount = repetitionCount;
        this.easinessFactor = easinessFactor;
        this.currentIntervalDays = currentIntervalDays;
        this.lastReviewDate = lastReviewDate;
    }

    public int getRepetitionCount() {
        return repetitionCount;
    }
    public double getEasinessFactor() {
        return easinessFactor;
    }
    public double getCurrentIntervalDays() {
        return currentIntervalDays;
    }
    public LocalDate getLastReviewDate() {
        return lastReviewDate;
    }

    public void updateState(int newRepetitionCount, double newEasinessFactor, double newIntervalDays,
            LocalDate reviewDate) {
        this.repetitionCount = newRepetitionCount;
        this.easinessFactor = newEasinessFactor;
        this.currentIntervalDays = newIntervalDays;
        this.lastReviewDate = reviewDate;
    }
}
