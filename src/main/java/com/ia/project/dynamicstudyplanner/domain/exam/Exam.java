package com.ia.project.dynamicstudyplanner.domain.exam;

import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the entire exam structure as defined in the official announcement ('edital').
 * This class is the Aggregate Root for the Exam bounded context. It encapsulates the rules
 * for calculating objective importance scores for its subjects.
 */
public class Exam {

    private static final Logger log = LoggerFactory.getLogger(Exam.class);

    private final String name;
    private final LocalDate examDate;
    private final double generalKnowledgeTotalScore;
    private final List<Subject> generalKnowledgeSubjects;
    private final List<ThematicAxis> specificKnowledgeAxes;

    public Exam(String name, LocalDate examDate, double generalKnowledgeTotalScore,
                List<Subject> generalKnowledgeSubjects, List<ThematicAxis> specificKnowledgeAxes) {
        this.name = name;
        this.examDate = examDate;
        this.generalKnowledgeTotalScore = generalKnowledgeTotalScore;
        // Defensive copying to prevent external modification
        this.generalKnowledgeSubjects = generalKnowledgeSubjects == null ? List.of() : Collections.unmodifiableList(generalKnowledgeSubjects);
        this.specificKnowledgeAxes = specificKnowledgeAxes == null ? List.of() : Collections.unmodifiableList(specificKnowledgeAxes);
    }

    public String getName() {
        return name;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public double getGeneralKnowledgeTotalScore() {
        return generalKnowledgeTotalScore;
    }

    public List<Subject> getGeneralKnowledgeSubjects() {
        return generalKnowledgeSubjects;
    }

    public List<ThematicAxis> getSpecificKnowledgeAxes() {
        return specificKnowledgeAxes;
    }
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

    /**
     * Calculates the objective base importance of a subject based purely on the exam's scoring rules.
     * This logic belongs in the Exam entity because it dictates its own structure and grading.
     *
     * @param subject The subject to calculate importance for.
     * @return The calculated objective weight.
     */
    public double calculateBaseImportance(Subject subject) {
        if (generalKnowledgeSubjects.contains(subject)) {
            int totalGKQuestions = getGeneralKnowledgeTotalQuestions();
            if (totalGKQuestions <= 0) {
                throw new DomainException("General Knowledge total questions must be positive to calculate importance.");
            }
            double valuePerQuestion = generalKnowledgeTotalScore / totalGKQuestions;
            return subject.questionCount() * valuePerQuestion;
        } else {
            Optional<ThematicAxis> parentAxis = findAxisForSubject(subject);
            if (parentAxis.isPresent()) {
                double axisWeight = parentAxis.get().weight();
                return subject.questionCount() * axisWeight;
            }
            log.warn("Subject '{}' was not found in any Specific Knowledge axis. Base importance set to 0.", subject.name());
            return 0.0;
        }
    }
}
