package com.ia.project.dynamicstudyplanner.service.calculation;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.exam.ThematicAxis;
import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Calculates the importance of subjects by implementing a Weighted Scoring Model.
 * <p>
 * This calculator determines a subject's priority by combining its objective value in the
 * exam with the student's subjective need to study it. The final score is used by the
 * genetic algorithm's fitness function.
 */
public final class ImportanceCalculator {

    private static final Logger log = LoggerFactory.getLogger(ImportanceCalculator.class);

    /**
     * Calculates a personalized importance score for every subject in the exam.
     * This method orchestrates the two-step calculation process.
     *
     * @param exam The structured Exam object containing all rules and subjects.
     * @param profile The StudentProfile object containing personal data like knowledge gaps.
     * @return A map associating each Subject with its final calculated importance score.
     */
    public Map<Subject, Double> calculatePersonalizedImportance(Exam exam, StudentProfile profile) {
        Map<Subject, Double> finalImportanceScores = new HashMap<>();
        for (Subject subject : exam.getAllSubjects()) {
            double baseImportance = calculateBaseImportance(subject, exam);
            double finalImportance = applyPersonalizationFactor(baseImportance, subject, profile.knowledgeGaps());
            finalImportanceScores.put(subject, finalImportance);
        }
        return finalImportanceScores;
    }

    /**
     * Calculates the objective, base importance of a subject based purely on the exam's scoring rules.
     * This method isolates the logic for interpreting the exam's structure.
     *
     * @param subject The subject to be evaluated.
     * @param exam The context of the exam, providing scoring rules.
     * @return The objective importance score, representing the subject's potential contribution to the final grade.
     */
    private double calculateBaseImportance(Subject subject, Exam exam) {
        if (exam.generalKnowledgeSubjects().contains(subject)) {
            return calculateGeneralKnowledgeImportance(subject, exam);
        } else {
            return calculateSpecificKnowledgeImportance(subject, exam);
        }
    }

    /**
     * Calculates importance for a General Knowledge subject.
     * Assumes all questions in this section have equal value.
     */
    private double calculateGeneralKnowledgeImportance(Subject subject, Exam exam) {
        int totalGKQuestions = exam.getGeneralKnowledgeTotalQuestions();
        if (totalGKQuestions <= 0) {
            throw new DomainException("General Knowledge total questions must be positive to calculate importance.");
        }
        // Each question's value is an equal share of the section's total score.
        double valuePerQuestion = exam.generalKnowledgeTotalScore() / totalGKQuestions;
        return subject.questionCount() * valuePerQuestion;
    }

    /**
     * Calculates importance for a Specific Knowledge subject.
     * Assumes each question's value is determined by its thematic axis weight.
     */
    private double calculateSpecificKnowledgeImportance(Subject subject, Exam exam) {
        Optional<ThematicAxis> parentAxis = exam.findAxisForSubject(subject);
        if (parentAxis.isPresent()) {
            double axisWeight = parentAxis.get().weight();
            return subject.questionCount() * axisWeight;
        }
        log.warn("Subject '{}' was not found in any Specific Knowledge axis. Base importance set to 0.", subject.name());
        return 0.0;
    }

    /**
     * Adjusts a subject's base importance using a personalization factor from the student's profile.
     * This method applies the subjective layer to the calculation.
     */
    private double applyPersonalizationFactor(double baseImportance, Subject subject, Map<Subject, Double> knowledgeGaps) {
        // The Knowledge Gap factor amplifies the importance of subjects the student finds difficult.
        double knowledgeGapFactor = knowledgeGaps.getOrDefault(subject, 1.0);
        return baseImportance * knowledgeGapFactor;
    }
}