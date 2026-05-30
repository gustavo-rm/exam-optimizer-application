package com.ia.project.dynamicstudyplanner.domain.engagement;

/**
 * Tracks the historical engagement and consistency of the student.
 * Used by the DropoutRiskPredictor to assess behavioral trends.
 */
public record EngagementProfile(
        double recentCompletionRate,     // E.g., 0.85 (85% of scheduled blocks completed over last 7 days)
        int consecutiveFailedDays,       // Days where completion was near zero
        double historicalConsistency,    // Long-term completion rate (e.g., 30-day average)
        boolean hasExpressedFrustration  // Derived from NLP/Chatbot onboarding sentiment
) {
    public EngagementProfile {
        if (recentCompletionRate < 0.0 || recentCompletionRate > 1.0) {
            throw new IllegalArgumentException("Completion rate must be between 0.0 and 1.0");
        }
        if (historicalConsistency < 0.0 || historicalConsistency > 1.0) {
            throw new IllegalArgumentException("Historical consistency must be between 0.0 and 1.0");
        }
        if (consecutiveFailedDays < 0) {
            throw new IllegalArgumentException("Failed days cannot be negative");
        }
    }

    /**
     * Helper constructor for a perfectly engaged student (default baseline).
     */
    public static EngagementProfile baseline() {
        return new EngagementProfile(1.0, 0, 1.0, false);
    }
}
