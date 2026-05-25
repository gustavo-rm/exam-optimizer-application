package com.ia.project.dynamicstudyplanner.domain;

/**
 * Encapsulates the psychological, physical, and emotional state of a student.
 * Values are on a scale from 1.0 (very low) to 5.0 (very high).
 */
public record StudentState(
        double stressLevel,
        double fatigueLevel,
        double motivationLevel
) {}
