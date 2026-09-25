package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A invariante que os dois operadores de cruzamento compartilham: o filho respeita o orçamento.
 *
 * <h2>Por que este teste existe</h2>
 *
 * O reparo de genes estava duplicado byte a byte em {@code RepairingCrossover} e
 * {@code WeightedAverageCrossover} (achado L2). A etapa 04b unificou os dois em
 * {@code ChildGeneRepair}, e este arquivo é o que impede a regressão silenciosa: ele exercita
 * <b>os dois operadores</b> e cobra deles a mesma invariante.
 *
 * <p>Se alguém voltar a duplicar o reparo e corrigir só um lado, um dos dois blocos abaixo falha.
 *
 * <h2>Por que pelos operadores e não pela função direta</h2>
 *
 * {@code ChildGeneRepair} é de visibilidade de pacote, de propósito: ela é detalhe interno da
 * família de cruzamento, não API. Testá-la pela porta pública dos operadores verifica o que importa
 * de fato — que o filho entregue à população seja válido — em vez de fixar a assinatura de um
 * auxiliar.
 */
@DisplayName("Cruzamento: o filho sempre respeita o orcamento de dias")
class ChildGeneRepairTest {

    private static final LocalDate HOJE = LocalDate.now();

    @BeforeEach
    void semeia() {
        RandomProvider.setInstance(new Random(20260903L));
    }

    private static List<PlanningItem> itens(int quantos) {
        List<PlanningItem> lista = new java.util.ArrayList<>();
        for (int i = 0; i < quantos; i++) {
            lista.add(new PlanningItem("D" + i, "D" + i, 1 + (i % 5)));
        }
        return lista;
    }

    private static EvolutionContext contexto(List<PlanningItem> itens, Map<PlanningItem, Integer> minimos) {
        Map<PlanningItem, Double> importancias = new HashMap<>();
        for (PlanningItem item : itens) {
            importancias.put(item, 1.0);
        }
        return EvolutionContext.builder()
                .importanceScores(importancias)
                .minimumDaysPerItem(minimos)
                .fitnessEvaluator(new FitnessEvaluator(List.of(), List.of(), List.of()))
                .planStartDate(HOJE)
                .planningHorizonDays(300)
                .hoursPerStudyDay(3)
                .maxDailyCognitiveLoad(10)
                .build();
    }

    /** Um indivíduo com {@code diasPorItem} dias em cada item do plano. */
    private static Individual individuo(List<PlanningItem> itens, int diasPorItem) {
        Map<PlanningItem, Integer> genes = new HashMap<>();
        for (PlanningItem item : itens) {
            genes.put(item, diasPorItem);
        }
        return new Individual(new StudyPlan(genes));
    }

    @Test
    @DisplayName("RepairingCrossover: a soma de dias do filho bate com o orcamento do pai 1")
    void repairingCrossoverRespeitaOOrcamento() {
        List<PlanningItem> itens = itens(6);
        RepairingCrossover operador = new RepairingCrossover();

        for (int i = 0; i < 200; i++) {
            Individual pai1 = individuo(itens, 10);
            Individual pai2 = individuo(itens, 4);
            Individual filho = operador.crossover(pai1, pai2, 1.0, contexto(itens, Map.of()));

            assertThat(somaDeDias(filho))
                    .as("tentativa %d: o filho tem que caber no orcamento do pai 1", i)
                    .isEqualTo(pai1.getPlan().getTotalDays());
        }
    }

    @Test
    @DisplayName("WeightedAverageCrossover: mesma invariante, pelo outro operador")
    void weightedAverageCrossoverRespeitaOOrcamento() {
        List<PlanningItem> itens = itens(6);
        WeightedAverageCrossover operador = new WeightedAverageCrossover();

        for (int i = 0; i < 200; i++) {
            Individual pai1 = individuo(itens, 10);
            Individual pai2 = individuo(itens, 4);
            Individual filho = operador.crossover(pai1, pai2, 1.0, contexto(itens, Map.of()));

            assertThat(somaDeDias(filho))
                    .as("tentativa %d", i)
                    .isEqualTo(pai1.getPlan().getTotalDays());
        }
    }

    @Test
    @DisplayName("o reparo nao derruba nenhuma disciplina abaixo do seu piso de dias minimos")
    void oReparoRespeitaOPisoDeCadaDisciplina() {
        List<PlanningItem> itens = itens(5);
        Map<PlanningItem, Integer> minimos = new HashMap<>();
        for (PlanningItem s : itens) {
            minimos.put(s, 8);
        }

        HybridCrossover operador = new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover());

        for (int i = 0; i < 200; i++) {
            // Pais com folga confortavel acima do piso; o reparo precisa cortar sem violar o minimo.
            Individual filho = operador.crossover(individuo(itens, 20), individuo(itens, 12),
                    1.0, contexto(itens, minimos));

            assertThat(filho.getPlan().getDaysPerItem())
                    .as("tentativa %d", i)
                    .allSatisfy((item, dias) -> assertThat(dias)
                            .as("item %s abaixo do piso", item.name())
                            .isGreaterThanOrEqualTo(8));
        }
    }

    @Test
    @DisplayName("o reparo termina mesmo quando nenhuma disciplina pode ceder dias")
    void oReparoTerminaQuandoNinguemPodeCeder() {
        // Todas as disciplinas ja no piso e o alvo abaixo da soma: o laco nao tem como convergir.
        // A guarda de saida e retirar do sorteio quem esta no piso — sem ela, isto travaria.
        List<PlanningItem> itens = itens(4);
        Map<PlanningItem, Integer> minimos = new HashMap<>();
        for (PlanningItem s : itens) {
            minimos.put(s, 10);
        }

        RepairingCrossover operador = new RepairingCrossover();
        Individual pai1 = individuo(itens, 10);
        Individual pai2 = individuo(itens, 10);

        // Sem timeout explicito: se a guarda sumir, o teste trava e o CI mata o build — que e o
        // sinal desejado. O que se afirma aqui e que ele TERMINA e devolve algo coerente.
        Individual filho = operador.crossover(pai1, pai2, 1.0, contexto(itens, minimos));

        assertThat(filho.getPlan().getDaysPerItem().values())
                .as("ninguem foi empurrado abaixo do piso")
                .allSatisfy(dias -> assertThat(dias).isGreaterThanOrEqualTo(10));
    }

    private static int somaDeDias(Individual individuo) {
        return individuo.getPlan().getDaysPerItem().values().stream().mapToInt(Integer::intValue).sum();
    }
}
