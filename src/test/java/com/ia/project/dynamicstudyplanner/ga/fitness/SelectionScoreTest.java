package com.ia.project.dynamicstudyplanner.ga.fitness;

import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.support.TopicPlans;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O escore de seleção, e as duas metades da porta que o governa — pendência <b>G15</b>.
 *
 * <h2>O que este teste protege</h2>
 *
 * {@code clamp(raw, 0, 1)} serve a dois propósitos que se separaram:
 *
 * <ol>
 *   <li><b>manter a fitness publicada em {@code [0,1]}</b>, que é requisito de relato e continua
 *       valendo para todo plano;</li>
 *   <li><b>impedir aptidão negativa onde ela é usada como peso de mistura</b>, em
 *       {@code WeightedAverageCrossover}. {@code IndividualTest.severeViolationClampsAtZero} registra
 *       essa razão, e ela continua valendo no caminho macro.</li>
 * </ol>
 *
 * O preço de aplicar o limite à <b>seleção</b> é que planos igualmente inviáveis empatam em zero e o
 * torneio escolhe ao acaso. Em plano tático isso é fatal — é lá que a restrição de revisão leva o
 * bruto a negativo em 84,4% das execuções medidas — e é lá que nenhum operador lê aptidão como peso.
 *
 * <p>Por isso a porta tem duas metades, e as duas precisam de teste: {@link #macroInviavelEmpataEmZero()}
 * prova que a garantia do operador macro sobreviveu, e {@link #taticosInviaveisFicamOrdenados()} prova
 * que o gradiente foi restaurado. Um teste só de uma das metades deixaria a outra livre para regredir.
 */
@DisplayName("G15: escore de selecao contra fitness publicada")
class SelectionScoreTest {

    private static final int TOPICS = 6;

    /** Plano tático em que os {@code reviewed} primeiros itens recebem revisão. */
    private static TacticalStudyPlan tactical(EvolutionContext context, int reviewed) {
        List<PlanningItem> items = List.copyOf(context.importanceScores().keySet());
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
    @DisplayName("plano macro inviavel continua empatando em zero, protegendo o crossover ponderado")
    void macroInviavelEmpataEmZero() {
        EvolutionContext context = TopicPlans.context(TOPICS);
        FitnessEvaluator evaluator = TopicPlans.evaluator();

        // Piso alto contra alocação mínima: severidade máxima no piso de dias.
        Map<PlanningItem, Integer> um = new LinkedHashMap<>();
        context.importanceScores().keySet().forEach(item -> um.put(item, 1));
        StudyPlan macro = new StudyPlan(um);

        FitnessBreakdown breakdown = evaluator.explain(macro, context);

        // Seja o bruto positivo ou negativo, um plano MACRO nunca entrega negativo à seleção:
        // WeightedAverageCrossover usa a aptidão como peso e extrapolaria. Ver o Javadoc da classe.
        assertThat(evaluator.evaluate(macro, context))
                .as("macro nunca vai negativo, qualquer que seja o bruto (%.6f)", breakdown.rawScore())
                .isGreaterThanOrEqualTo(0.0);
        assertThat(breakdown.selectionScore()).isEqualTo(breakdown.aggregate());
    }

    @Test
    @DisplayName("dois planos taticos inviaveis ficam ordenados em vez de empatar em zero")
    void taticosInviaveisFicamOrdenados() {
        EvolutionContext context = TopicPlans.context(TOPICS);
        FitnessEvaluator evaluator = TopicPlans.evaluator();

        TacticalStudyPlan pior = tactical(context, 0);
        TacticalStudyPlan melhor = tactical(context, TOPICS);

        FitnessBreakdown decompPior = evaluator.explain(pior, context);
        FitnessBreakdown decompMelhor = evaluator.explain(melhor, context);

        // A PRECONDIÇÃO do teste: os dois são inviáveis, logo o agregado PUBLICADO empata em zero.
        // Sem esta asserção o teste poderia passar sobre planos viáveis e não provar nada sobre G15.
        assertThat(decompPior.rawScore()).isNegative();
        assertThat(decompPior.aggregate()).isZero();

        // E A CORREÇÃO: a seleção os distingue.
        assertThat(evaluator.evaluate(melhor, context))
                .as("revisar %d itens tem de ordenar acima de revisar nenhum", TOPICS)
                .isGreaterThan(evaluator.evaluate(pior, context));
        assertThat(decompPior.selectionScore()).isEqualTo(decompPior.rawScore());
    }

    @Test
    @DisplayName("onde o limite nao mordia, selecao e publicacao continuam identicas")
    void ondeOLimiteNaoMordeNadaMuda() {
        EvolutionContext context = TopicPlans.context(TOPICS);
        FitnessEvaluator evaluator = TopicPlans.evaluator();
        StudyPlan macro = TopicPlans.optimize(context, TOPICS * 3, 3, 10);

        FitnessBreakdown breakdown = evaluator.explain(macro, context);

        assertThat(breakdown.rawScore()).isPositive();
        assertThat(breakdown.selectionScore()).isEqualTo(breakdown.aggregate());
        assertThat(evaluator.evaluate(macro, context)).isEqualTo(breakdown.aggregate());
    }
}
