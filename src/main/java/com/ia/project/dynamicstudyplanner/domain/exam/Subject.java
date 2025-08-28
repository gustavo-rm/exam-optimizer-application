package examoptimizer.domain.exam;

/**
 * Represents a single, self-contained subject.
 * Its context (which exam part or axis it belongs to) is now managed
 * by the parent Exam and ThematicAxis classes.
 *
 * @param name The name of the subject.
 * @param questionCount The number of questions for THIS subject in the exam.
 */
public record Subject(
        String name,
        int questionCount
) {}
