package com.ia.project.dynamicstudyplanner.domain.tactical;

import java.time.LocalDateTime;

/**
 * Represents a larger block of time where the student is generally available.
 * A window contains multiple specific TimeSlots and carries an expected energy level.
 *
 * @param startTime The start of the availability.
 * @param endTime The end of the availability.
 * @param expectedEnergyLevel The predicted energy of the student during this window (1.0 low to 5.0 high).
 */
public record AvailabilityWindow(
        LocalDateTime startTime,
        LocalDateTime endTime,
        double expectedEnergyLevel
) {
    public long getDurationMinutes() {
        return java.time.Duration.between(startTime, endTime).toMinutes();
    }
}
