package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.CognitiveLoadObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty;
import com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor;
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterisation tests for {@code docs/revisao-ag/06-verificacao-pos-rodada.md}.
 * <p>
 * These tests <b>document what the system does today</b>. They are not a specification of what it
 * should do, and none of them is a fix. Their job is to make the claims in the review documents
 * falsifiable by execution rather than by reading, after a code-wide search found no prerequisite,
 * dependency-graph or topological-ordering logic anywhere in the repository.
 * <p>
 * Two claims are under test:
 * <ol>
 *   <li><b>"Prerequisite sequencing is not implemented."</b> If it were, two plans that differ only
 *       in whether a dependent subject is studied before its prerequisite would score differently.
 *       {@link NoPrerequisiteLogicIsReachable} shows they do not.</li>
 *   <li><b>"A topological check would be vacuous, because the chromosome has no order"</b>
 *       ({@code 06-decisao-ausubel.md} §2.4). {@link OrderIsExpressibleInTheTacticalEncoding} shows
 *       this holds for {@code StudyPlan} but <em>not</em> for {@code TacticalStudyPlan}, a
 *       time-indexed subclass that already exists in the repository and that the same
 *       {@code FitnessEvaluator} already accepts.</li>
 * </ol>
 * If a future change makes any of these fail, prerequisite handling has been added or the encoding
 * has changed — and the review documents need updating either way.
 */
@DisplayName("Diagnostico: sequenciamento por pre-requisitos")
class PrerequisiteSequencingDiagnosticTest {

    /**
     * A plausible prerequisite pair: {@code FOUNDATION} would have to be covered before
     * {@code ADVANCED} under Ausubel. Nothing in the domain model can express that relation — the
     * declaration lives only in this comment, which is itself the finding.
     */
    private static final Subject FOUNDATION = new Subject("Teoria Geral", 10, 3);
    private static final Subject ADVANCED = new Subject("Aplicacoes Avancadas", 10, 3);

    private static final int HORIZON_DAYS = 180;
    private static final int HOURS_PER_STUDY_DAY = 4;
    private static final int MAX_DAILY_LOAD = 20;
    private static final LocalDateTime DAY_ONE = LocalDateTime.of(2026, 1, 5, 8, 0);

    @Nested
    @DisplayName("Nenhuma logica de pre-requisitos e alcancavel pela fitness")
    class NoPrerequisiteLogicIsReachable {

        @Test
        @DisplayName("o cromossomo macro nao distingue 'A antes de B' de 'B antes de A'")
        void macroChromosomeCannotEvenRepresentTheViolation() {
            // The same allocation described in the two possible orders. If the encoding carried
            // order, these would be different objects.
            StudyPlan prerequisiteFirst = new StudyPlan(Map.of(FOUNDATION, 10, ADVANCED, 6));
            StudyPlan dependentFirst = new StudyPlan(Map.of(ADVANCED, 6, FOUNDATION, 10));

            assertThat(prerequisiteFirst.getDaysPerSubject())
                    .as("Map<Subject,Integer> nao tem ordem: os dois 'planos' sao o mesmo valor. "
                            + "Nao existe violacao de precedencia a detectar porque nao existe "
                            + "precedencia a expressar.")
                    .isEqualTo(dependentFirst.getDaysPerSubject());
        }

        @Test
        @DisplayName("nenhum componente da fitness penaliza estudar o dependente antes do pre-requisito")
        void tacticalPrecedenceViolationIsNotPenalised() {
            // Here order IS expressible: TimeSlots carry real timestamps. Plan A studies the
            // prerequisite on day 1 and the dependent on day 2; plan B does the reverse.
            TacticalStudyPlan respectsPrecedence = tacticalPlan(FOUNDATION, ADVANCED);
            TacticalStudyPlan violatesPrecedence = tacticalPlan(ADVANCED, FOUNDATION);

            FitnessEvaluator evaluator = productionPipeline();
            EvolutionContext context = contextFor(evaluator);

            double respecting = evaluator.evaluate(respectsPrecedence, context);
            double violating = evaluator.evaluate(violatesPrecedence, context);

            assertThat(violating)
                    .as("Os dois planos alocam o mesmo esforco e diferem apenas na ORDEM, que este "
                            + "encoding expressa. A fitness devolve o mesmo valor, portanto nenhum "
                            + "objetivo, restricao ou penalidade le precedencia.")
                    .isEqualTo(respecting);
        }
    }

    @Nested
    @DisplayName("A ordem e expressavel no encoding tatico, que a fitness ja aceita")
    class OrderIsExpressibleInTheTacticalEncoding {

        @Test
        @DisplayName("TacticalStudyPlan recupera qual disciplina foi estudada primeiro")
        void tacticalEncodingCarriesOrder() {
            TacticalStudyPlan plan = tacticalPlan(FOUNDATION, ADVANCED);

            Subject earliest = plan.getSchedule().entrySet().stream()
                    .min((a, b) -> a.getKey().startTime().compareTo(b.getKey().startTime()))
                    .map(e -> e.getValue().subject())
                    .orElseThrow();

            assertThat(earliest)
                    .as("Um validador topologico sobre ESTE encoding nao seria vacuo: a pergunta "
                            + "'A vem antes de B?' tem resposta. A vacuidade afirmada em "
                            + "06-decisao-ausubel.md §2.4 e propriedade do cromossomo macro, nao "
                            + "do repositorio.")
                    .isEqualTo(FOUNDATION);
        }

        @Test
        @DisplayName("o FitnessEvaluator de producao aceita o encoding tatico sem alteracao")
        void productionEvaluatorAlreadyAcceptsTheTacticalEncoding() {
            FitnessEvaluator evaluator = productionPipeline();

            double fitness = evaluator.evaluate(tacticalPlan(FOUNDATION, ADVANCED), contextFor(evaluator));

            assertThat(fitness)
                    .as("TacticalStudyPlan estende StudyPlan, entao a assinatura atual ja o aceita. "
                            + "Um termo de precedencia nao exigiria trocar a interface da fitness.")
                    .isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("Guardrails da etapa 05")
    class Guardrails {

        @Test
        @DisplayName("o assert de soma dos pesos rejeita um pipeline desbalanceado")
        void weightSumAssertionRejectsAnUnbalancedPipeline() {
            List<FitnessObjective> missingOneObjective =
                    List.of(new ScoreGainObjective(), new RetentionObjective()); // 0.50 + 0.30

            assertThatThrownBy(() -> new FitnessEvaluator(missingOneObjective, List.of(), List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("devem somar 1.0");
        }

        /**
         * Reescrito na etapa 01b. A versão anterior era {@code assertThat(...).isNotNull()}, que em
         * Java não pode falhar sem que uma exceção tenha sido lançada antes: o teste funcionava por
         * efeito colateral e a asserção não dizia o que ele realmente verificava. Agora declara as
         * duas coisas: que a composição de produção passa pelo assert de soma dos pesos, e qual é a
         * composição que passa — de modo que acrescentar ou remover um objetivo sem rebalancear os
         * pesos falhe aqui, com mensagem legível.
         */
        @Test
        @DisplayName("a composicao de producao passa pelo assert de soma dos pesos")
        void productionPipelineSatisfiesTheWeightSumAssertion() {
            assertThatCode(PrerequisiteSequencingDiagnosticTest::productionPipeline)
                    .as("os tres objetivos de producao somam 1.0 e o construtor deve aceita-los")
                    .doesNotThrowAnyException();

            assertThat(List.of(new ScoreGainObjective(), new RetentionObjective(), new CognitiveLoadObjective()))
                    .as("a composicao verificada acima e exatamente esta")
                    .extracting(FitnessObjective::getWeight)
                    .containsExactly(0.50, 0.30, 0.20);
        }
    }

    // ------------------------------------------------------------------

    /** Two one-hour blocks on consecutive days, in the given order. */
    private static TacticalStudyPlan tacticalPlan(Subject firstDay, Subject secondDay) {
        return new TacticalStudyPlan(Map.of(
                new TimeSlot(DAY_ONE, DAY_ONE.plusHours(1)),
                new TacticalStudyBlock(firstDay, StudyMethodology.ACTIVE_RECALL, 60),
                new TimeSlot(DAY_ONE.plusDays(1), DAY_ONE.plusDays(1).plusHours(1)),
                new TacticalStudyBlock(secondDay, StudyMethodology.ACTIVE_RECALL, 60)));
    }

    private static EvolutionContext contextFor(FitnessEvaluator evaluator) {
        return EvolutionContext.of(
                Map.of(FOUNDATION, 10.0, ADVANCED, 10.0),
                Map.of(FOUNDATION, 1, ADVANCED, 1),
                null, evaluator, null, null, null,
                HORIZON_DAYS, HOURS_PER_STUDY_DAY, MAX_DAILY_LOAD);
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
