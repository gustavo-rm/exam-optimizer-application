package com.ia.project.dynamicstudyplanner.service.calculation;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.exam.ThematicAxis;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImportanceCalculatorTest {

    /** Ancora fixa: ver a nota em {@code CognitiveLoadCalculatorTest}. */
    private static final LocalDate EXAM_DATE = LocalDate.of(2026, 9, 1);

    private final ImportanceCalculator calculator = new ImportanceCalculator();

    @Test
    void shouldCalculatePersonalizedImportanceForMixedSubjects() {
        // Arrange
        Subject math = new Subject("Math", 10, 3); // GK
        Subject programming = new Subject("Programming", 20, 5); // Specific

        ThematicAxis itAxis = new ThematicAxis(1, "IT", 2.5, List.of(programming));
        Exam exam = new Exam("Test Exam", EXAM_DATE, 100.0, List.of(math), List.of(itAxis));

        // Math: Base = 10 * (100.0 / 10) = 100.0. Profile gap = 1.0. Final = 100.0
        // Programming: Base = 20 * 2.5 = 50.0. Profile gap = 2.0. Final = 100.0
        StudentProfile profile = new StudentProfile("Test", Map.of(programming, 2.0), Map.of());

        // Act
        Map<Subject, Double> result = calculator.calculatePersonalizedImportance(exam, profile);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(math)).isEqualTo(100.0);
        assertThat(result.get(programming)).isEqualTo(100.0);
    }
}
