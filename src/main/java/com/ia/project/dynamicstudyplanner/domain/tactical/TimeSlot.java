package com.ia.project.dynamicstudyplanner.domain.tactical;

import java.time.LocalDateTime;

/**
 * Represents a fixed window of availability for a student.
 * In the tactical GA, this forms the rigid grid (the loci) of the chromosome.
 *
 * @param startTime When the slot begins.
 * @param endTime When the slot ends.
 */
public record TimeSlot(
        LocalDateTime startTime,
        LocalDateTime endTime
) {
    public long getDurationMinutes() {
        return java.time.Duration.between(startTime, endTime).toMinutes();
    }
}
