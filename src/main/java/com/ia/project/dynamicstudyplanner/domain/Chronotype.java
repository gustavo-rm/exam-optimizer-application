package com.ia.project.dynamicstudyplanner.domain;

/**
 * Represents the student's circadian rhythm preference, which dictates intraday energy curves.
 */
public enum Chronotype {
    MORNING_LARK,   // Peaks early, crashes in evening
    NIGHT_OWL,      // Sluggish morning, peaks late night
    INTERMEDIATE    // Standard bell curve, peaking mid-morning/early afternoon
}
