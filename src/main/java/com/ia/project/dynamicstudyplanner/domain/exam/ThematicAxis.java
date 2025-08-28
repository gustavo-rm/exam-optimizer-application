package examoptimizer.domain.exam;

import java.util.List;

/**
 * Represents a "Thematic Axis" within the Specific Knowledge part of the exam.
 * It groups related subjects and has its own specific weight.
 *
 * @param id A unique identifier for the axis (e.g., 1, 2, 3).
 * @param name The descriptive name of the axis (e.g., "Information Technology").
 * @param weight The weight of this entire axis in the final score calculation.
 * @param subjects The list of subjects that belong to this axis.
 */
public record ThematicAxis(
        int id,
        String name,
        double weight,
        List<Subject> subjects
) {
    /**
     * Calculates the total number of questions within this axis.
     * @return The sum of question counts from all subjects in this axis.
     */
    public int getTotalQuestions() {
        return subjects.stream()
                .mapToInt(Subject::questionCount)
                .sum();
    }
}
