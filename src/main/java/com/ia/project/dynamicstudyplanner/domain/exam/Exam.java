package com.ia.project.dynamicstudyplanner.domain.exam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the entire exam structure as defined in the official announcement ('edital').
 * This class encapsulates all parts of the exam, including general and specific knowledge sections,
 * thematic axes, and all subjects.
 *
 * @param name The official name of the exam.
 * @param examDate The final date for the exam (the deadline).
 * @param generalKnowledgeSubjects The list of subjects for the General Knowledge part.
 * @param specificKnowledgeAxes The list of thematic axes for the Specific Knowledge part.
 */
public record Exam(
        String name,
        LocalDate examDate,
        double generalKnowledgeTotalScore, // <-- The new attribute
        List<Subject> generalKnowledgeSubjects,
        List<ThematicAxis> specificKnowledgeAxes
) {
    /**
     * Helper method to get the total number of questions in the General Knowledge exam.
     * @return The total question count for the General Knowledge part.
     */
    public int getGeneralKnowledgeTotalQuestions() {
        return generalKnowledgeSubjects.stream()
                .mapToInt(Subject::questionCount)
                .sum();
    }

    /**
     * Helper method to generate the map of weights for the Specific Knowledge axes.
     * This is useful for the ImportanceCalculator.
     * @return A map where the key is the axis ID and the value is its weight.
     */
    public Map<Integer, Double> getSpecificKnowledgeWeights() {
        return specificKnowledgeAxes.stream()
                .collect(Collectors.toMap(ThematicAxis::id, ThematicAxis::weight));
    }

    /**
     * Provides a single, unified list of all subjects in the entire exam.
     * @return A list containing all subjects from both general and specific parts.
     */
    public List<Subject> getAllSubjects() {
        // Combines subjects from the general part and all subjects from all specific axes
        return Stream.concat(
                generalKnowledgeSubjects.stream(),
                specificKnowledgeAxes.stream().flatMap(axis -> axis.subjects().stream())
        ).toList();
    }

    /**
     * Finds the ThematicAxis that a given subject belongs to.
     *
     * @param subject The subject to find.
     * @return An Optional containing the parent ThematicAxis if found, otherwise an empty Optional.
     */
    public Optional<ThematicAxis> findAxisForSubject(Subject subject) {
        return specificKnowledgeAxes.stream()
                .filter(axis -> axis.subjects().contains(subject))
                .findFirst();
    }
}
