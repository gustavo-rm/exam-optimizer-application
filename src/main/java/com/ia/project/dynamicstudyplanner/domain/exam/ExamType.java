package com.ia.project.dynamicstudyplanner.domain.exam;

/**
 * Defines the type of exam a subject belongs to.
 */
public enum ExamType {
    /**
     * For subjects in the General Knowledge exam (P1).
     */
    GENERAL_KNOWLEDGE,
    /**
     * For subjects in the Specific Knowledge exam (P2), which are grouped by thematic axes.
     */
    SPECIFIC_KNOWLEDGE
}
