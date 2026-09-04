package com.ia.project.dynamicstudyplanner.domain.exam;

import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamTest {

    /**
     * Ancora fixa em vez de {@code EXAM_DATE}: mantem o horizonte de 30 dias
     * sem tornar o resultado do teste dependente do dia em que ele roda.
     */
    private static final LocalDate EXAM_DATE = LocalDate.of(2026, 10, 1);

    @Test
    void shouldCalculateTotalGeneralKnowledgeQuestions() {
        // Arrange
        List<Subject> gkSubjects = List.of(
                new Subject("Math", 10, 3),
                new Subject("History", 5, 2)
        );
        Exam exam = new Exam("Test Exam", EXAM_DATE, 100.0, gkSubjects, List.of());

        // Act & Assert
        assertThat(exam.getGeneralKnowledgeTotalQuestions()).isEqualTo(15);
    }

    @Test
    void shouldFindAxisForSpecificSubject() {
        // Arrange
        Subject programming = new Subject("Programming", 20, 5);
        ThematicAxis itAxis = new ThematicAxis(1, "IT", 2.5, List.of(programming));
        Exam exam = new Exam("Test Exam", EXAM_DATE, 100.0, List.of(), List.of(itAxis));

        // Act
        Optional<ThematicAxis> result = exam.findAxisForSubject(programming);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("IT");
    }

    @Test
    void shouldCalculateBaseImportanceForGeneralKnowledge() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        List<Subject> gkSubjects = List.of(math, new Subject("History", 10, 2));
        Exam exam = new Exam("Test Exam", EXAM_DATE, 100.0, gkSubjects, List.of());

        // Act
        // Total GK questions = 20. Total Score = 100. Value per question = 5. Math questions = 10. Importance = 50.
        double importance = exam.calculateBaseImportance(math);

        // Assert
        assertThat(importance).isEqualTo(50.0);
    }

    @Test
    void shouldThrowExceptionWhenCalculatingImportanceWithZeroGKQuestions() {
        // Arrange
        Subject math = new Subject("Math", 0, 3);
        // GK subjects exist but total questions is 0
        List<Subject> gkSubjects = List.of(math);
        Exam exam = new Exam("Test Exam", EXAM_DATE, 100.0, gkSubjects, List.of());

        // Act & Assert
        assertThatThrownBy(() -> exam.calculateBaseImportance(math))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void shouldDefensivelyCopyCollections() {
        // Arrange
        List<Subject> mutableList = new ArrayList<>();
        mutableList.add(new Subject("Math", 10, 3));

        // When using List.copyOf() on a mutable ArrayList, a new immutable list is created.
        Exam exam = new Exam("Test Exam", EXAM_DATE, 100.0, mutableList, List.of());

        // Act
        // Modifying the original list should not affect the Exam's internal state.
        mutableList.add(new Subject("Physics", 5, 4));

        // Assert
        assertThat(exam.getGeneralKnowledgeSubjects()).hasSize(1);
    }
}
