package com.ia.project.dynamicstudyplanner.ga.fitness.constraint;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import com.ia.project.dynamicstudyplanner.support.TopicPlans;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A severidade graduada desta restrição — o fecho da pendência <b>G14</b>.
 *
 * <h2>Por que este teste não existia, e por que a falta dele custou caro</h2>
 *
 * A restrição estava coberta a 77/77 instruções por testes que montavam a composição inteira para
 * outro fim. Cobertura incidental prova que a linha <i>executa</i>; não prova nada sobre o
 * <i>valor</i> que ela devolve. Enquanto a severidade era binária, {@link
 * #aSeveridadeEhAFracaoDeRevisoesPerdidas()} era o único teste que teria reprovado — e é o que
 * agora trava a correção.
 *
 * <p>O efeito medido do defeito está em {@code docs/revisao-ag/09-medicao-baseline-vs-v1.md} §9:
 * severidade 1,0 em 720 de 720 execuções, agregado preso em zero em 94,6% delas.
 */
@DisplayName("G14: severidade graduada de MandatoryReviewConstraint")
class MandatoryReviewConstraintTest {

    private static final int TOPICS = 8;

    private final MandatoryReviewConstraint constraint =
            new MandatoryReviewConstraint(new HybridRetentionEngine());

    /** Itens na ordem canônica do contexto, que é a ordem de chegada dos tópicos. */
    private static List<PlanningItem> itemsOf(EvolutionContext context) {
        return List.copyOf(context.importanceScores().keySet());
    }

    /**
     * Um cronograma em que os {@code reviewed} primeiros itens recebem um bloco de revisão e todos
     * recebem um bloco de estudo.
     *
     * <p>Os slots são sequenciais e disjuntos: o que está sob teste é quais itens têm revisão, não
     * como o calendário foi montado.
     */
    private static TacticalStudyPlan planWithReviews(EvolutionContext context, int reviewed) {
        List<PlanningItem> items = itemsOf(context);
        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();
        LocalDateTime cursor = LocalDateTime.parse("2026-09-01T09:00");

        for (int index = 0; index < items.size(); index++) {
            schedule.put(new TimeSlot(cursor, cursor.plusMinutes(60)),
                    new TacticalStudyBlock(items.get(index), StudyMethodology.ACTIVE_RECALL, 60));
            cursor = cursor.plusMinutes(60);

            if (index < reviewed) {
                schedule.put(new TimeSlot(cursor, cursor.plusMinutes(30)),
                        new TacticalStudyBlock(items.get(index),
                                StudyMethodology.SPACED_REPETITION_REVIEW, 30));
                cursor = cursor.plusMinutes(30);
            }
        }
        return new TacticalStudyPlan(schedule);
    }

    @Test
    @DisplayName("um plano macro não viola esta restrição, por não ter como expressá-la")
    void planoMacroNaoViolaEstaRestricao() {
        EvolutionContext context = TopicPlans.context(TOPICS);
        StudyPlan macro = TopicPlans.optimize(context, TOPICS * 2, 1, 10);

        // O cromossomo macro é uma contagem de sessões por item, sem calendário: ele não distingue
        // estudo de revisão. Severidade zero aqui é decisão registrada (CLAUDE.md §1b), e é o que
        // mantém a busca da v1 idêntica a antes desta correção.
        assertThat(constraint.violationSeverity(macro, context)).isZero();
        assertThat(constraint.isValid(macro, context)).isTrue();
    }

    @Test
    @DisplayName("sem nenhuma revisão agendada, a severidade é 1")
    void semNenhumaRevisaoASeveridadeEhUm() {
        EvolutionContext context = TopicPlans.context(TOPICS);

        assertThat(constraint.violationSeverity(planWithReviews(context, 0), context))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("com todas as revisões agendadas, a severidade é 0 e o plano é válido")
    void comTodasAsRevisoesASeveridadeEhZero() {
        EvolutionContext context = TopicPlans.context(TOPICS);
        TacticalStudyPlan complete = planWithReviews(context, TOPICS);

        assertThat(constraint.violationSeverity(complete, context)).isZero();
        assertThat(constraint.isValid(complete, context)).isTrue();
    }

    @Test
    @DisplayName("a severidade é a fração de revisões devidas que ficaram de fora")
    void aSeveridadeEhAFracaoDeRevisoesPerdidas() {
        EvolutionContext context = TopicPlans.context(TOPICS);

        // ESTE É O TESTE QUE REPROVARIA A VERSÃO BINÁRIA. Com a severidade binária, revisar 4 de 8
        // itens devolvia 1,0 — exatamente o que revisar nenhum devolvia — e a busca não tinha como
        // distinguir um plano quase completo de um plano que ignorou a restrição inteira.
        double half = constraint.violationSeverity(planWithReviews(context, TOPICS / 2), context);

        assertThat(half).isStrictlyBetween(0.0, 1.0);
        assertThat(half).isEqualTo(0.5);
    }

    @Test
    @DisplayName("agendar mais revisões nunca aumenta a severidade")
    void aSeveridadeEhMonotonica() {
        EvolutionContext context = TopicPlans.context(TOPICS);

        double previous = Double.MAX_VALUE;
        for (int reviewed = 0; reviewed <= TOPICS; reviewed++) {
            double severity = constraint.violationSeverity(planWithReviews(context, reviewed),
                    context);
            assertThat(severity)
                    .as("revisando %d de %d itens", reviewed, TOPICS)
                    .isLessThanOrEqualTo(previous);
            previous = severity;
        }
        assertThat(previous).isZero();
    }
}
