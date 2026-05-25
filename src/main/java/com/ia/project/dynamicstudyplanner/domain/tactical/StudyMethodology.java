package com.ia.project.dynamicstudyplanner.domain.tactical;

/**
 * Represents the specific methodology to be used during a study block.
 * Each methodology carries a different cognitive and emotional load.
 */
public enum StudyMethodology {
    PASSIVE_READING(1.0, 0.5),      // Low load, low retention
    VIDEO_LECTURE(1.5, 0.6),
    ACTIVE_RECALL(3.0, 1.5),        // High load, high retention
    PRACTICE_EXAM(4.0, 2.0),
    SPACED_REPETITION_REVIEW(2.5, 1.8);

    private final double cognitiveLoadMultiplier;
    private final double expectedRetentionMultiplier;

    StudyMethodology(double cognitiveLoadMultiplier, double expectedRetentionMultiplier) {
        this.cognitiveLoadMultiplier = cognitiveLoadMultiplier;
        this.expectedRetentionMultiplier = expectedRetentionMultiplier;
    }

    public double getCognitiveLoadMultiplier() {
        return cognitiveLoadMultiplier;
    }

    public double getExpectedRetentionMultiplier() {
        return expectedRetentionMultiplier;
    }
}
