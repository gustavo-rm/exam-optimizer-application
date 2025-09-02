package com.ia.project.dynamicstudyplanner.domain.exam;

/**
 * Represents a single, self-contained subject.
 *
 * @param name The name of the subject.
 * @param questionCount The number of questions for this subject in the exam.
 * @param cognitiveLoad An objective score (e.g., 1-5) representing the intrinsic
 * mental effort required by the subject's content.
 */
public record Subject(
        String name,
        int questionCount,
        int cognitiveLoad
) {}
