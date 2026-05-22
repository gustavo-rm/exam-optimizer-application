package com.ia.project.dynamicstudyplanner.service.calculation;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveLoadCalculatorTest {

    private final CognitiveLoadCalculator calculator = new CognitiveLoadCalculator();

    @Test
    void shouldCalculateLoadBasedOnAvailabilityAndPressure() {
        // Arrange
        // Total weekly hours = 14 (Avg 2 per day). Base capacity = 2 * 3.0 = 6.0
        StudentProfile profile = new StudentProfile("Test", Map.of(), Map.of(DayOfWeek.MONDAY, 7, DayOfWeek.TUESDAY, 7));

        // Avg cognitive load = 4.0. Pressure factor = 1.1 - ((4-1)/4)*0.2 = 1.1 - 0.15 = 0.95
        Exam exam = new Exam("Test", LocalDate.now(), 100.0, List.of(new Subject("Math", 10, 4)), List.of());

        // Fatigue factor (no gaps, default -1.0 -> assume 3.0) = 1.1 - ((3-1)/4)*0.3 = 1.1 - 0.15 = 0.95
        // Final load = 6.0 * 0.95 * 0.95 = 5.415 -> rounds to 5

        // Act
        int load = calculator.calculate(profile, exam);

        // Assert
        assertThat(load).isEqualTo(5);
    }
}
