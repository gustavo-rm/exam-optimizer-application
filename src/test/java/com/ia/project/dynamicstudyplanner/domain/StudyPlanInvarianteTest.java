package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o invariante do qual a memoização de {@code getTotalDays()} depende.
 *
 * <h2>Por que este teste existe</h2>
 *
 * A etapa 05b passou a calcular o total de dias uma vez, no construtor de {@link StudyPlan}
 * (achado F5). Isso só é correto enquanto o plano for de fato imutável — e a imutabilidade
 * depende de um <b>contrato</b>: quem constrói entrega a posse do mapa e não o altera depois. A
 * cópia defensiva que tornaria isso estrutural foi medida e rejeitada por custar mais do que
 * economiza (ver o Javadoc do construtor).
 *
 * <p>Este arquivo é o que substitui a cópia: em vez de pagar proteção em todo descendente, verifica
 * que o invariante <b>se mantém sobre os planos que a produção realmente gera</b> — depois de
 * cruzamento, depois de mutação, depois da cadeia híbrida completa.
 *
 * <p>Se algum dia um operador retiver o mapa e o alterar após construir o plano, o total ficaria
 * defasado em relação ao conteúdo, e é exatamente isso que estes testes detectam.
 */
@DisplayName("StudyPlan: o total memorizado nunca diverge do mapa")
class StudyPlanInvarianteTest {

    private static final LocalDate HOJE = LocalDate.now();

    @BeforeEach
    void semeia() {
        RandomProvider.setInstance(new Random(20260903L));
    }

    private static Exam exame(int disciplinas) {
        List<Subject> lista = new ArrayList<>();
        for (int i = 0; i < disciplinas; i++) {
            lista.add(new Subject("D" + i, 5 + (i * 3) % 25, 1 + (i % 5)));
        }
        return new Exam("E", HOJE.plusDays(400), 100.0, lista, List.of());
    }

    private static EvolutionContext contexto(Exam exame) {
        Map<Subject, Double> importancias = new HashMap<>();
        for (Subject s : exame.getGeneralKnowledgeSubjects()) {
            importancias.put(s, 1.0);
        }
        return EvolutionContext.builder()
                .importanceScores(importancias)
                .minimumDaysPerSubject(Map.of())
                .fitnessEvaluator(new FitnessEvaluator(List.of(), List.of(), List.of()))
                .planStartDate(HOJE)
                .planningHorizonDays(365)
                .hoursPerStudyDay(5)
                .maxDailyCognitiveLoad(10)
                .build();
    }

    private static Individual individuo(Exam exame, int diasPorDisciplina) {
        Map<Subject, Integer> genes = new HashMap<>();
        for (Subject s : exame.getGeneralKnowledgeSubjects()) {
            genes.put(s, diasPorDisciplina);
        }
        return new Individual(new StudyPlan(genes));
    }

    /** O invariante: o total memorizado tem que ser a soma do mapa exposto. */
    private static void verificaInvariante(StudyPlan plano, String contexto) {
        int somaDoMapa = plano.getDaysPerSubject().values().stream().mapToInt(Integer::intValue).sum();
        assertThat(plano.getTotalDays())
                .as("total memorizado divergiu da soma do mapa apos %s", contexto)
                .isEqualTo(somaDoMapa);
    }

    @Test
    @DisplayName("o invariante vale nos planos produzidos por cruzamento")
    void invarianteValeAposCruzamento() {
        Exam exame = exame(12);
        EvolutionContext ctx = contexto(exame);
        HybridCrossover operador =
                new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover());

        for (int i = 0; i < 300; i++) {
            Individual filho = operador.crossover(
                    individuo(exame, 20), individuo(exame, 12), 1.0, ctx);
            verificaInvariante(filho.getPlan(), "cruzamento " + i);
        }
    }

    @Test
    @DisplayName("o invariante vale nos planos produzidos por mutacao")
    void invarianteValeAposMutacao() {
        Exam exame = exame(12);
        EvolutionContext ctx = contexto(exame);
        CreepMutation mutacao = new CreepMutation();

        for (int i = 0; i < 300; i++) {
            Individual mutado = mutacao.mutate(individuo(exame, 20), 1.0, ctx);
            verificaInvariante(mutado.getPlan(), "mutacao " + i);
        }
    }

    @Test
    @DisplayName("o invariante vale na cadeia completa: cruzar e depois mutar")
    void invarianteValeNaCadeiaCompleta() {
        Exam exame = exame(24);
        EvolutionContext ctx = contexto(exame);
        HybridCrossover cruzamento =
                new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover());
        CreepMutation mutacao = new CreepMutation();

        for (int i = 0; i < 200; i++) {
            Individual filho = cruzamento.crossover(
                    individuo(exame, 15), individuo(exame, 10), 1.0, ctx);
            Individual mutado = mutacao.mutate(filho, 0.5, ctx);
            verificaInvariante(mutado.getPlan(), "cadeia completa " + i);
        }
    }

    @Test
    @DisplayName("plano vazio e plano nulo tambem respeitam o invariante")
    void invarianteValeNosCasosLimite() {
        verificaInvariante(new StudyPlan(Map.of()), "mapa vazio");
        verificaInvariante(new StudyPlan(null), "mapa nulo");
    }
}
