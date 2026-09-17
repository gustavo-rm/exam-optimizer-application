package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
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

    private static Exam exame(int disciplinas) {
        List<Subject> lista = new java.util.ArrayList<>();
        for (int i = 0; i < disciplinas; i++) {
            lista.add(new Subject("D" + i, 5 + i, 1 + (i % 5)));
        }
        return new Exam("Concurso", HOJE.plusDays(300), 100.0, lista, List.of());
    }

    private static StudentProfile perfil(Exam exame) {
        Map<Subject, Double> lacunas = new HashMap<>();
        for (Subject s : exame.getGeneralKnowledgeSubjects()) {
            lacunas.put(s, 3.0);
        }
        Map<DayOfWeek, Integer> disponibilidade = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            disponibilidade.put(d, 3);
        }
        return new StudentProfile("Aluno", lacunas, disponibilidade,
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE));
    }

    private static EvolutionContext contexto(Exam exame, Map<Subject, Integer> minimos) {
        Map<Subject, Double> importancias = new HashMap<>();
        for (Subject s : exame.getGeneralKnowledgeSubjects()) {
            importancias.put(s, 1.0);
        }
        return EvolutionContext.builder()
                .importanceScores(importancias)
                .minimumDaysPerSubject(minimos)
                .studentState(perfil(exame).getState())
                .fitnessEvaluator(new FitnessEvaluator(List.of(), List.of(), List.of()))
                .planStartDate(HOJE)
                .planningHorizonDays(300)
                .hoursPerStudyDay(3)
                .maxDailyCognitiveLoad(10)
                .build();
    }

    /** Um indivíduo com {@code diasPorDisciplina} dias em cada disciplina do edital. */
    private static Individual individuo(Exam exame, int diasPorDisciplina) {
        Map<Subject, Integer> genes = new HashMap<>();
        for (Subject s : exame.getGeneralKnowledgeSubjects()) {
            genes.put(s, diasPorDisciplina);
        }
        return new Individual(new StudyPlan(genes));
    }

    @Test
    @DisplayName("RepairingCrossover: a soma de dias do filho bate com o orcamento do pai 1")
    void repairingCrossoverRespeitaOOrcamento() {
        Exam exame = exame(6);
        RepairingCrossover operador = new RepairingCrossover();

        for (int i = 0; i < 200; i++) {
            Individual pai1 = individuo(exame, 10);
            Individual pai2 = individuo(exame, 4);
            Individual filho = operador.crossover(pai1, pai2, 1.0, contexto(exame, Map.of()));

            assertThat(somaDeDias(filho))
                    .as("tentativa %d: o filho tem que caber no orcamento do pai 1", i)
                    .isEqualTo(pai1.getPlan().getTotalDays());
        }
    }

    @Test
    @DisplayName("WeightedAverageCrossover: mesma invariante, pelo outro operador")
    void weightedAverageCrossoverRespeitaOOrcamento() {
        Exam exame = exame(6);
        WeightedAverageCrossover operador = new WeightedAverageCrossover();

        for (int i = 0; i < 200; i++) {
            Individual pai1 = individuo(exame, 10);
            Individual pai2 = individuo(exame, 4);
            Individual filho = operador.crossover(pai1, pai2, 1.0, contexto(exame, Map.of()));

            assertThat(somaDeDias(filho))
                    .as("tentativa %d", i)
                    .isEqualTo(pai1.getPlan().getTotalDays());
        }
    }

    @Test
    @DisplayName("o reparo nao derruba nenhuma disciplina abaixo do seu piso de dias minimos")
    void oReparoRespeitaOPisoDeCadaDisciplina() {
        Exam exame = exame(5);
        Map<Subject, Integer> minimos = new HashMap<>();
        for (Subject s : exame.getGeneralKnowledgeSubjects()) {
            minimos.put(s, 8);
        }

        HybridCrossover operador = new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover());

        for (int i = 0; i < 200; i++) {
            // Pais com folga confortavel acima do piso; o reparo precisa cortar sem violar o minimo.
            Individual filho = operador.crossover(individuo(exame, 20), individuo(exame, 12),
                    1.0, contexto(exame, minimos));

            assertThat(filho.getPlan().getDaysPerSubject())
                    .as("tentativa %d", i)
                    .allSatisfy((disciplina, dias) -> assertThat(dias)
                            .as("disciplina %s abaixo do piso", disciplina.name())
                            .isGreaterThanOrEqualTo(8));
        }
    }

    @Test
    @DisplayName("o reparo termina mesmo quando nenhuma disciplina pode ceder dias")
    void oReparoTerminaQuandoNinguemPodeCeder() {
        // Todas as disciplinas ja no piso e o alvo abaixo da soma: o laco nao tem como convergir.
        // A guarda de saida e retirar do sorteio quem esta no piso — sem ela, isto travaria.
        Exam exame = exame(4);
        Map<Subject, Integer> minimos = new HashMap<>();
        for (Subject s : exame.getGeneralKnowledgeSubjects()) {
            minimos.put(s, 10);
        }

        RepairingCrossover operador = new RepairingCrossover();
        Individual pai1 = individuo(exame, 10);
        Individual pai2 = individuo(exame, 10);

        // Sem timeout explicito: se a guarda sumir, o teste trava e o CI mata o build — que e o
        // sinal desejado. O que se afirma aqui e que ele TERMINA e devolve algo coerente.
        Individual filho = operador.crossover(pai1, pai2, 1.0, contexto(exame, minimos));

        assertThat(filho.getPlan().getDaysPerSubject().values())
                .as("ninguem foi empurrado abaixo do piso")
                .allSatisfy(dias -> assertThat(dias).isGreaterThanOrEqualTo(10));
    }

    private static int somaDeDias(Individual individuo) {
        return individuo.getPlan().getDaysPerSubject().values().stream().mapToInt(Integer::intValue).sum();
    }
}
