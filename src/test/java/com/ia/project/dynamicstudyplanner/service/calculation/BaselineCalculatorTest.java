package com.ia.project.dynamicstudyplanner.service.calculation;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaselineCalculatorTest {

    /** Ancora fixa: ver a nota em {@code CognitiveLoadCalculatorTest}. */
    private static final LocalDate EXAM_DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private ImportanceCalculator importanceCalculator;

    @InjectMocks
    private BaselineCalculator baselineCalculator;

    @Test
    void shouldCalculateAndScaleMinimumDays() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        Subject history = new Subject("History", 5, 2);
        Exam exam = new Exam("Test", EXAM_DATE, 100.0, List.of(math, history), List.of());

        StudentProfile profile = new StudentProfile("Test", Map.of(math, 2.0, history, 1.0), Map.of());

        // Mock importance
        when(importanceCalculator.calculatePersonalizedImportance(exam, profile))
                .thenReturn(Map.of(math, 50.0, history, 10.0));

        // Act
        Map<Subject, Integer> result = baselineCalculator.calculateMinimumDays(exam, profile);

        // Assert
        assertThat(result).hasSize(2);
        // Math has higher importance and higher gap, so it should have more days.
        assertThat(result.get(math)).isGreaterThan(result.get(history));
        // The max subject should scale to MAX_MINIMUM_DAYS (15)
        assertThat(result.get(math)).isEqualTo(15);
    }
}
