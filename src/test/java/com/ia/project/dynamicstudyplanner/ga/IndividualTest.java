package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IndividualTest {

    @Test
    void shouldCalculateFitnessWithoutPenalties() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        StudyPlan plan = new StudyPlan(Map.of(math, 5));
        Individual individual = new Individual(plan);

        Map<Subject, Double> importance = Map.of(math, 10.0);
        Map<Subject, Integer> constraints = Map.of(math, 2);
        com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel model = new com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel();
        com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator evaluator = new com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator(
                java.util.List.of(new com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective()),
                java.util.List.of(
                        new com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty(model),
                        new com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty(new com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor())
                ),
                java.util.List.of(new com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint())
        );
        EvolutionContext context = EvolutionContext.of(importance, constraints, null, evaluator, null, null, null, 180, 4, 20);

        // Act
        // A importancia e normalizada para o simplex antes de ponderar (05-fitness-function.md):
        // com uma unica materia, o peso normalizado e 1.0, entao fitness = ln(1 + 5) = 1.7917.
        double fitness = individual.calculateFitness(context);

        // Assert
        assertThat(fitness).isCloseTo(1.7917, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void shouldApplyPenaltyWhenConstraintsViolated() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        StudyPlan plan = new StudyPlan(Map.of(math, 1)); // Fails constraint
        Individual individual = new Individual(plan);

        Map<Subject, Double> importance = Map.of(math, 10.0);
        Map<Subject, Integer> constraints = Map.of(math, 5); // Requires 5
        com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel model = new com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel();
        com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator evaluator = new com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator(
                java.util.List.of(new com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective()),
                java.util.List.of(
                        new com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty(model),
                        new com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty(new com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor())
                ),
                java.util.List.of(new com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint())
        );
        EvolutionContext context = EvolutionContext.of(importance, constraints, null, evaluator, null, null, null, 180, 4, 20);

        // Act
        // ln(1 + 1) = 0.6931 com peso normalizado 1.0; a violacao da constraint aplica 0.5.
        double fitness = individual.calculateFitness(context);

        // Assert
        assertThat(fitness).isCloseTo(0.3466, org.assertj.core.data.Offset.offset(0.001));
    }
}
