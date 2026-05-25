package com.ia.project.dynamicstudyplanner.domain.retention;

import java.time.LocalDate;

/**
 * Tracks the cognitive memory state of a specific subject for a student.
 * Inspired by SM-2 and Half-Life Regression memory states.
 */
public class SubjectRetentionState {

    // Default SM-2 starting Easiness Factor
    private static final double DEFAULT_EASINESS_FACTOR = 2.5;

    private int repetitionCount;
    private double easinessFactor;
    private double currentIntervalDays; // Equivalent to Stability (S)
    private LocalDate lastReviewDate;

    public SubjectRetentionState(LocalDate lastReviewDate) {
        this.repetitionCount = 0;
        this.easinessFactor = DEFAULT_EASINESS_FACTOR;
        this.currentIntervalDays = 1.0; // Start with a 1-day interval
        this.lastReviewDate = lastReviewDate;
    }

    public SubjectRetentionState(int repetitionCount, double easinessFactor, double currentIntervalDays, LocalDate lastReviewDate) {
        this.repetitionCount = repetitionCount;
        this.easinessFactor = easinessFactor;
        this.currentIntervalDays = currentIntervalDays;
        this.lastReviewDate = lastReviewDate;
    }

    public int getRepetitionCount() { return repetitionCount; }
    public double getEasinessFactor() { return easinessFactor; }
    public double getCurrentIntervalDays() { return currentIntervalDays; }
    public LocalDate getLastReviewDate() { return lastReviewDate; }

    public void updateState(int newRepetitionCount, double newEasinessFactor, double newIntervalDays, LocalDate reviewDate) {
        this.repetitionCount = newRepetitionCount;
        this.easinessFactor = newEasinessFactor;
        this.currentIntervalDays = newIntervalDays;
        this.lastReviewDate = reviewDate;
    }
}
