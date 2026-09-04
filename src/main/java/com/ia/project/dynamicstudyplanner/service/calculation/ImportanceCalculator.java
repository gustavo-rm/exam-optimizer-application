package com.ia.project.dynamicstudyplanner.service.calculation;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Calculates the importance of subjects by implementing a Weighted Scoring Model.
 * <p>
 * This calculator determines a subject's priority by combining its objective value in the
 * exam with the student's subjective need to study it.
 * <p>
 * The score produced here is <b>raw</b>, in the exam's own scoring units. Every fitness term reads
 * the normalised form instead ({@code EvolutionContext.normalizedImportance}), because raw values
 * from two subjects in one sum can differ by a factor of 250,000 under the API's own validation
 * limits. See {@code docs/revisao-ag/05-fitness-function.md} §3.1.
 */
@Service
public class ImportanceCalculator {

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
            double baseImportance = exam.calculateBaseImportance(subject);
            double finalImportance = profile.applyKnowledgeGapFactor(subject, baseImportance);
            finalImportanceScores.put(subject, finalImportance);
        }
        return finalImportanceScores;
    }
}
