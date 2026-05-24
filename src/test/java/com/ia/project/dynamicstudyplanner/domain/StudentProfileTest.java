package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfileTest {

    @Test
    void shouldCalculateTotalWeeklyHours() {
        // Arrange
        Map<DayOfWeek, Integer> availability = Map.of(
                DayOfWeek.MONDAY, 4,
                DayOfWeek.SATURDAY, 8
        );
        StudentProfile profile = new StudentProfile("Test", Map.of(), availability);

        // Act & Assert
        assertThat(profile.getTotalWeeklyHours()).isEqualTo(12);
    }

    @Test
    void shouldReturnDefaultKnowledgeGapFactorWhenSubjectNotFound() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        StudentProfile profile = new StudentProfile("Test", Map.of(), Map.of());

        // Act & Assert
        assertThat(profile.getKnowledgeGapFactor(math)).isEqualTo(1.0);
    }

    @Test
    void shouldCalculateAverageKnowledgeGap() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        Subject history = new Subject("History", 10, 3);
        Map<Subject, Double> gaps = Map.of(math, 2.0, history, 4.0);
        StudentProfile profile = new StudentProfile("Test", gaps, Map.of());

        // Act & Assert
        assertThat(profile.getAverageKnowledgeGap()).isEqualTo(3.0);
    }

    @Test
    void shouldReturnNegativeOneForAverageGapWhenEmpty() {
        // Arrange
        StudentProfile profile = new StudentProfile("Test", Map.of(), Map.of());

        // Act & Assert
        assertThat(profile.getAverageKnowledgeGap()).isEqualTo(-1.0);
    }

    @Test
    void shouldApplyKnowledgeGapFactor() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        StudentProfile profile = new StudentProfile("Test", Map.of(math, 2.5), Map.of());

        // Act (Base importance * gap factor)
        double result = profile.applyKnowledgeGapFactor(math, 10.0);

        // Assert
        assertThat(result).isEqualTo(25.0);
    }
}
