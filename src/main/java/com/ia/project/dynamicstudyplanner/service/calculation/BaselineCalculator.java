package examoptimizer.service.calculation;

import examoptimizer.domain.StudentProfile;
import examoptimizer.domain.exam.Exam;
import examoptimizer.domain.exam.Subject;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calculates the baseline (minimum) number of study days required for each subject.
 * <p>
 * This class translates the abstract concept of "difficulty" into a concrete number of days.
 * It uses a normalization technique to ensure the calculated days are always within a reasonable
 * and practical range, preventing impossibly large study plans.
 */
public final class BaselineCalculator {

    private static final int MAX_MINIMUM_DAYS = 15; // The most difficult subject will be assigned this many days.
    private static final int MIN_REQUIRED_DAYS = 1;  // Every subject will have at least this many days.

    /**
     * Calculates the minimum required study days for all subjects in an exam for a given student.
     *
     * @param exam The Exam object, containing objective data.
     * @param profile The Student's profile, containing subjective data.
     * @return A map associating each Subject with its calculated minimum study days.
     */
    public Map<Subject, Integer> calculateMinimumDays(Exam exam, StudentProfile profile) {
        // Step 1: Calculate a raw "perceived difficulty" score for every subject.
        Map<Subject, Double> perceivedDifficultyScores = exam.getAllSubjects().stream()
                .collect(Collectors.toMap(
                        subject -> subject,
                        subject -> calculatePerceivedDifficulty(subject, exam, profile)
                ));

        // Step 2: Find the maximum difficulty score to use for normalization.
        double maxDifficulty = perceivedDifficultyScores.values().stream()
                .max(Double::compare)
                .orElse(1.0);

        // Step 3: Normalize each score and scale it to the desired day range.
        return perceivedDifficultyScores.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> normalizeAndScale(entry.getValue(), maxDifficulty)
                ));
    }

    /**
     * Calculates a "Perceived Difficulty" score for a subject by combining objective exam weight
     * with the student's subjective knowledge gap.
     *
     * @return A numeric score representing the perceived difficulty for this specific student.
     */
    private double calculatePerceivedDifficulty(Subject subject, Exam exam, StudentProfile profile) {
        // The same logic from ImportanceCalculator's 'calculateBaseImportance' can be reused here.
        double objectiveWeight = new ImportanceCalculator().calculatePersonalizedImportance(exam, profile).get(subject) / profile.knowledgeGaps().getOrDefault(subject, 1.0);
        double knowledgeGapFactor = profile.knowledgeGaps().getOrDefault(subject, 1.0);

        // A subject is perceived as difficult if it is important for the exam AND the student is weak in it.
        return objectiveWeight * knowledgeGapFactor;
    }

    /**
     * Normalizes a raw score to a 0-1 range and then scales it to a predefined day range.
     *
     * @param rawScore The raw perceived difficulty score for a subject.
     * @param maxScore The maximum score among all subjects, used for normalization.
     * @return The final, scaled number of minimum days.
     */
    private int normalizeAndScale(double rawScore, double maxScore) {
        double normalizedScore = (maxScore > 0) ? (rawScore / maxScore) : 0;

        // Formula: MIN + (NORMALIZED_SCORE * (MAX - MIN))
        return (int) Math.round(
                MIN_REQUIRED_DAYS + (normalizedScore * (MAX_MINIMUM_DAYS - MIN_REQUIRED_DAYS))
        );
    }
}