package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudyPlanTest {

    @Test
    void shouldCalculateTotalDays() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        Subject history = new Subject("History", 10, 3);
        StudyPlan plan = new StudyPlan(Map.of(math, 5, history, 3));

        // Act & Assert
        assertThat(plan.getTotalDays()).isEqualTo(8);
    }

    @Test
    void shouldVerifyMinimumConstraintsAreMet() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        Subject history = new Subject("History", 10, 3);
        StudyPlan plan = new StudyPlan(Map.of(math, 5, history, 3));

        // Constraints: require less than or equal to what is allocated
        Map<Subject, Integer> constraints = Map.of(math, 4, history, 3);

        // Act & Assert
        assertThat(plan.meetsMinimumConstraints(constraints)).isTrue();
    }

    @Test
    void shouldDetectConstraintViolation() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        Subject history = new Subject("History", 10, 3);
        StudyPlan plan = new StudyPlan(Map.of(math, 2, history, 3)); // Only 2 days allocated to Math

        // Constraints: requires 4 days for Math
        Map<Subject, Integer> constraints = Map.of(math, 4, history, 3);

        // Act & Assert
        assertThat(plan.meetsMinimumConstraints(constraints)).isFalse();
    }
}
