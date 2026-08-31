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
        // Peso normalizado 1.0 (materia unica) x maestria com teto: tau(load 3) = 10 dias, entao
        // mastery(5) = 1 - exp(-5/10) = 0.39347. Ver docs/revisao-ag/05-fitness-function.md.
        double fitness = individual.calculateFitness(context);

        // Assert
        assertThat(fitness).isCloseTo(0.39347, org.assertj.core.data.Offset.offset(0.0001));
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
        // mastery(1) = 1 - exp(-1/10) = 0.09516 com peso 1.0; a violacao da constraint aplica 0.5.
        double fitness = individual.calculateFitness(context);

        // Assert
        assertThat(fitness).isCloseTo(0.04758, org.assertj.core.data.Offset.offset(0.0001));
    }
}
