package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.CognitiveLoadObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty;
import com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor;
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the aggregated fitness formula documented in {@code docs/revisao-ag/05-fitness-function.md}.
 * <p>
 * The expected values are worked out by hand below rather than copied from a run, so the test fails
 * if a term's formula or weight changes — which is the point: those changes must be deliberate and
 * must be reflected in the reference document.
 */
class IndividualTest {

    /** Cognitive load 3 puts this subject at the model's average difficulty, so tau = 10 days. */
    private static final Subject MATH = new Subject("Math", 10, 3);

    private static final int HORIZON_DAYS = 180;
    private static final int HOURS_PER_STUDY_DAY = 4;
    private static final int MAX_DAILY_LOAD = 20;

    @Test
    @DisplayName("soma ponderada dos tres objetivos, sem violacao de restricao")
    void aggregatesTheThreeObjectives() {
        // O1 mastery(5 dias, tau=10) = 1 - e^-0.5 = 0.393469, peso 0.50 -> 0.196735
        // O3 cobertura = 5 / (180/10) = 0.277778,            peso 0.30 -> 0.083333
        // O4 carga diaria = 4h x 3 = 12 <= orcamento 20 -> 1.0, peso 0.20 -> 0.200000
        //                                                            total = 0.480068
        double fitness = fitnessOf(5, 2);

        assertThat(fitness).isCloseTo(0.480068, Offset.offset(0.000001));
    }

    @Test
    @DisplayName("violacao parcial do piso e penalizada em proporcao a severidade")
    void partialConstraintViolationIsPenalisedProportionally() {
        // Um dia alocado contra um piso de dois: falta 1 de 2, severidade 0.5,
        // penalidade 0.50 x 0.5 = 0.25 subtraida da soma dos objetivos (0.264248).
        double fitness = fitnessOf(1, 2);

        assertThat(fitness).isCloseTo(0.014248, Offset.offset(0.000001));
    }

    @Test
    @DisplayName("violacao severa zera a fitness, sem ficar negativa")
    void severeViolationClampsAtZero() {
        // Falta 4 de um piso de 5: severidade 0.8, penalidade 0.40, acima da soma dos objetivos.
        // O clamp impede fitness negativa, que quebraria a media ponderada do crossover.
        double fitness = fitnessOf(1, 5);

        assertThat(fitness).isZero();
    }

    @Test
    @DisplayName("mais dias na mesma disciplina rendem menos, refletindo o teto de maestria")
    void masteryCurveHasDiminishingReturns() {
        double atFive = fitnessOf(5, 2);
        double atTwelve = fitnessOf(12, 2);
        double atNineteen = fitnessOf(19, 2);

        double firstStep = atTwelve - atFive;
        double secondStep = atNineteen - atTwelve;

        assertThat(secondStep)
                .as("O ganho dos 7 dias seguintes (%.4f) deve ser menor que o dos 7 primeiros (%.4f): "
                        + "e a saturacao que impede a alocacao de concentrar tudo numa disciplina.",
                        secondStep, firstStep)
                .isLessThan(firstStep);
    }

    private static double fitnessOf(int allocatedDays, int minimumDays) {
        FitnessEvaluator evaluator = productionPipeline();
        EvolutionContext context = EvolutionContext.of(
                Map.of(MATH, 10.0), Map.of(MATH, minimumDays), null, evaluator,
                null, null, null, HORIZON_DAYS, HOURS_PER_STUDY_DAY, MAX_DAILY_LOAD);

        return new Individual(new StudyPlan(Map.of(MATH, allocatedDays))).calculateFitness(context);
    }

    /** The same component set Spring wires in production. */
    private static FitnessEvaluator productionPipeline() {
        return new FitnessEvaluator(
                List.of(new ScoreGainObjective(), new RetentionObjective(), new CognitiveLoadObjective()),
                List.of(new DropoutRiskPenalty(new DropoutRiskPredictor()),
                        new FatigueAndSustainabilityPenalty(new FatigueAndEnergyModel())),
                List.of(new MinimumDaysConstraint(),
                        new MandatoryReviewConstraint(new HybridRetentionEngine())));
    }
}
