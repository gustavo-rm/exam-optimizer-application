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
                java.util.List.of(new com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty(model)),
                java.util.List.of(new com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint())
        );
        EvolutionContext context = new EvolutionContext(importance, constraints, null, evaluator, null, null);

        // Act
        // knowledge = ln(1 + 5) = 1.7917. fitness = 10 * 1.7917 = 17.917
        double fitness = individual.calculateFitness(context);

        // Assert
        assertThat(fitness).isCloseTo(17.917, org.assertj.core.data.Offset.offset(0.01));
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
                java.util.List.of(new com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty(model)),
                java.util.List.of(new com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint())
        );
        EvolutionContext context = new EvolutionContext(importance, constraints, null, evaluator, null, null);

        // Act
        // knowledge = ln(1 + 1) = 0.693. Base = 6.93. Penalty (0.5) = 3.465
        double fitness = individual.calculateFitness(context);

        // Assert
        assertThat(fitness).isCloseTo(3.465, org.assertj.core.data.Offset.offset(0.01));
    }
}
