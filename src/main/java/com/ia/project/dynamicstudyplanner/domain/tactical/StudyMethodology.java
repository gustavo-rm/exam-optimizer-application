package com.ia.project.dynamicstudyplanner.domain.tactical;

/**
 * Represents the specific methodology to be used during a study block.
 * Each methodology carries a different cognitive and emotional load.
 */
public enum StudyMethodology {
    PASSIVE_READING(1.0, 0.5, 1.0, 1.5),      // Low load, low retention, low emotional load, low energy required
    VIDEO_LECTURE(1.5, 0.6, 1.2, 2.0),
    ACTIVE_RECALL(3.0, 1.5, 2.5,
            4.0),        // High load, high retention, moderate emotional load, high energy required
    PRACTICE_EXAM(4.0, 2.0, 4.5, 5.0),        // Very high load, high emotional load
    SPACED_REPETITION_REVIEW(2.5, 1.8, 2.0, 3.0);

    private final double cognitiveLoadMultiplier;
    private final double expectedRetentionMultiplier;
    private final double emotionalLoadMultiplier;
    private final double requiredEnergyLevel;

    StudyMethodology(double cognitiveLoadMultiplier, double expectedRetentionMultiplier,
            double emotionalLoadMultiplier, double requiredEnergyLevel) {
        this.cognitiveLoadMultiplier = cognitiveLoadMultiplier;
        this.expectedRetentionMultiplier = expectedRetentionMultiplier;
        this.emotionalLoadMultiplier = emotionalLoadMultiplier;
        this.requiredEnergyLevel = requiredEnergyLevel;
    }

    public double getCognitiveLoadMultiplier() {
        return cognitiveLoadMultiplier;
    }

    public double getExpectedRetentionMultiplier() {
        return expectedRetentionMultiplier;
    }

    public double getEmotionalLoadMultiplier() {
        return emotionalLoadMultiplier;
    }

    public double getRequiredEnergyLevel() {
        return requiredEnergyLevel;
    }
}
