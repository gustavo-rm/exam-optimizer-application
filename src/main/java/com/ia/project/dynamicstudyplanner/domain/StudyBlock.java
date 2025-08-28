package examoptimizer.domain;

import examoptimizer.domain.exam.Subject;

/**
 * Represents a single, scheduled block of study for a specific subject on a given day.
 *
 * @param subject The subject to be studied.
 * @param hours   The number of hours allocated to this study block.
 */
public record StudyBlock(Subject subject, int hours) {}
