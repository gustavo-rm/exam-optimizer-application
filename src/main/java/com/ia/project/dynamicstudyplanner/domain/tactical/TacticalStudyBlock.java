package com.ia.project.dynamicstudyplanner.domain.tactical;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

/**
 * Represents the gene in the tactical GA.
 * It defines *what* is studied and *how* it is studied within a specific TimeSlot.
 *
 * @param subject The subject to be studied.
 * @param methodology The tactical method (e.g., active recall, reading).
 * @param durationMinutes The actual duration scheduled within the slot (must be <= TimeSlot duration).
 */
public record TacticalStudyBlock(
        Subject subject,
        StudyMethodology methodology,
        long durationMinutes
) {
    public double calculateRequiredEnergy() {
        // The block's energy requirement scales slightly with its duration, but is primarily driven by methodology.
        double baseEnergy = methodology.getRequiredEnergyLevel();
        double durationFactor = Math.log10(durationMinutes + 10) / 2.0; // Dampened scaling
        return baseEnergy * durationFactor;
    }

    public double calculateEmotionalLoad() {
        return methodology.getEmotionalLoadMultiplier() * subject.cognitiveLoad();
    }
}
