package com.ia.project.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.config.DefaultGeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.CognitiveLoadObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty;
import com.ia.project.dynamicstudyplanner.ga.generator.DefaultPopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.TournamentSelection;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor;
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A semente declarada torna a execução reproduzível, e não vaza para a thread do pool.
 *
 * <h2>As duas propriedades, e por que a segunda é a que morde</h2>
 *
 * A primeira é a que se pede de uma semente: mesma entrada e mesma semente dão o mesmo plano,
 * mesmo em threads diferentes. A segunda é a que só aparece em produção: o executor
 * {@code optimizerTaskExecutor} <b>reaproveita as threads do pool</b>, e {@link RandomProvider}
 * guarda a fonte <b>por thread</b>. Sem a restauração no {@code finally}, a semente instalada por
 * uma requisição continuaria instalada quando a próxima caísse na mesma thread — e uma execução que
 * não pediu semente alguma passaria a repetir o plano de outra, de forma intermitente, dependendo
 * de qual thread a atendeu. É a falha mais cara de diagnosticar que esta mudança poderia introduzir.
 *
 * <p>O teste força exatamente esse cenário: um pool de <b>uma única thread</b>, uma execução
 * semeada e, em seguida, na mesma thread, duas execuções sem semente que precisam divergir entre si.
 */
@DisplayName("Semente de execucao: reproduz e nao vaza para a thread do pool")
class SementeDeExecucaoTest {

    private static final long SEMENTE = 20260921L;
    private static final LocalDate HOJE = LocalDate.now();

    private static StudyOptimizerService servico() {
        ImportanceCalculator importancia = new ImportanceCalculator();
        FitnessEvaluator avaliador = new FitnessEvaluator(
                List.of(new ScoreGainObjective(), new RetentionObjective(), new CognitiveLoadObjective()),
                List.of(new DropoutRiskPenalty(new DropoutRiskPredictor()),
                        new FatigueAndSustainabilityPenalty(new FatigueAndEnergyModel())),
                List.of(new MinimumDaysConstraint(),
                        new MandatoryReviewConstraint(new HybridRetentionEngine())));
        return new StudyOptimizerService(
                new EvolutionContextAssembler(new BaselineCalculator(importancia), importancia,
                        new CognitiveLoadCalculator(), avaliador),
                new DefaultGeneticAlgorithmFactory(new TournamentSelection(),
                        new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover()),
                        new CreepMutation()),
                new DefaultPopulationGenerator(),
                new OptimizationMetrics(new SimpleMeterRegistry()));
    }

    private static Exam exame() {
        List<Subject> lista = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            lista.add(new Subject("D" + i, 5 + (i * 3) % 25, 1 + (i % 5)));
        }
        return new Exam("Concurso", HOJE.plusDays(400), 100.0, lista, List.of());
    }

    private static StudentProfile perfil(Exam exame) {
        Map<Subject, Double> lacunas = new HashMap<>();
        for (Subject s : exame.getGeneralKnowledgeSubjects()) {
            lacunas.put(s, 3.0);
        }
        Map<DayOfWeek, Integer> disponibilidade = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            disponibilidade.put(d, 5);
        }
        return new StudentProfile("Aluno", lacunas, disponibilidade,
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE));
    }

    /** Assinatura do plano, independente da ordem em que o mapa itera. */
    private static String assinatura(OptimizationResult resultado) {
        return resultado.plan().getDaysPerSubject().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().name()))
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .reduce((a, b) -> a + "|" + b)
                .orElse("<vazio>");
    }

    @Test
    @DisplayName("mesma entrada e mesma semente, em threads diferentes do mesmo pool, dao o mesmo plano")
    void mesmaSementeEmThreadsDiferentesDaOMesmoPlano() throws Exception {
        StudyOptimizerService servico = servico();
        Exam exame = exame();
        StudentProfile perfil = perfil(exame);
        Callable<String> execucao = () ->
                assinatura(servico.optimize(exame, perfil, 365, 40, 30, SEMENTE));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<String> planos = new ArrayList<>();
        try {
            for (Future<String> resultado :
                    pool.invokeAll(List.of(execucao, execucao, execucao, execucao))) {
                planos.add(resultado.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(planos)
                .as("quatro threads, uma semente: um plano so")
                .hasSize(4)
                .containsOnly(planos.get(0));
    }

    /**
     * A semente de uma execução não pode sobreviver na thread do pool.
     *
     * <h2>Por que a asserção é esta, e não "o plano seguinte repete o semeado"</h2>
     *
     * A formulação ingênua não funciona, e foi descartada depois de uma sabotagem deliberada: sem a
     * restauração, a execução seguinte herda o {@code Random} semeado <b>já avançado</b> pelos
     * sorteios da primeira, então ela diverge do plano semeado de qualquer forma. Um teste montado
     * assim passa com e sem o {@code finally}, e não prova nada.
     *
     * <p>A propriedade que discrimina é <b>a ausência de interferência</b>: uma execução sem
     * semente, partindo de uma fonte conhecida, tem de dar o mesmo plano quer uma execução semeada
     * tenha ocorrido antes na mesma thread, quer não. Com a restauração, a fonte instalada nunca é
     * tocada pela execução semeada — que usa a sua própria — e chega intacta à execução seguinte.
     * Sem a restauração, chega no lugar dela a fonte semeada e avançada, e os planos diferem.
     *
     * <p>Um pool de <b>uma única thread</b> garante que tudo cai na mesma thread, que é a condição
     * que o {@code finally} existe para cobrir.
     */
    @Test
    @DisplayName("uma execucao sem semente nao e afetada por uma execucao semeada anterior")
    void aSementeNaoVazaParaAExecucaoSeguinteNaMesmaThread() throws Exception {
        StudyOptimizerService servico = servico();
        Exam exame = exame();
        StudentProfile perfil = perfil(exame);
        long referencia = 987654321L;

        ExecutorService umaThread = Executors.newFixedThreadPool(1);
        try {
            String semInterferencia = umaThread.submit(() -> {
                RandomProvider.setInstance(new Random(referencia));
                return assinatura(servico.optimize(exame, perfil, 365, 40, 30, null));
            }).get();

            String depoisDeUmaSemeada = umaThread.submit(() -> {
                RandomProvider.setInstance(new Random(referencia));
                servico.optimize(exame, perfil, 365, 40, 30, SEMENTE);
                return assinatura(servico.optimize(exame, perfil, 365, 40, 30, null));
            }).get();

            assertThat(depoisDeUmaSemeada)
                    .as("a execucao semeada nao pode ter deixado a sua fonte instalada na thread: "
                            + "partindo da mesma fonte, a execucao livre tem de dar o mesmo plano "
                            + "com ou sem uma semeada antes dela")
                    .isEqualTo(semInterferencia);
        } finally {
            umaThread.shutdownNow();
        }
    }

    /**
     * A fonte instalada volta a ser <b>a mesma instância</b> depois de uma execução semeada.
     *
     * <p>A verificação por identidade é a forma direta da mesma invariante: o teste acima mede a
     * consequência observável, este mede a causa.
     */
    @Test
    @DisplayName("apos uma execucao semeada, a fonte anterior volta — a mesma instancia")
    void aFonteAnteriorEhRestauradaAposUmaExecucaoSemeada() {
        StudyOptimizerService servico = servico();
        Exam exame = exame();
        Random instalado = new Random(123L);
        RandomProvider.setInstance(instalado);

        servico.optimize(exame, perfil(exame), 365, 20, 20, SEMENTE);

        assertThat(RandomProvider.getInstance())
                .as("sem isto, a proxima requisicao que cair nesta thread do pool herda a semente")
                .isSameAs(instalado);
    }

    @Test
    @DisplayName("sem semente, o provedor da thread nao e tocado — nem lido, nem trocado")
    void semSementeOProvedorNaoEhTocado() {
        StudyOptimizerService servico = servico();
        Exam exame = exame();
        Random instalado = new Random(SEMENTE);
        RandomProvider.setInstance(instalado);

        servico.optimize(exame, perfil(exame), 365, 20, 20);

        assertThat(RandomProvider.getInstance())
                .as("a sobrecarga sem semente deixa exatamente a mesma instancia no lugar, que e o "
                        + "que mantem GaResultadoInalteradoTest e os benchmarks funcionando")
                .isSameAs(instalado);
    }
}
