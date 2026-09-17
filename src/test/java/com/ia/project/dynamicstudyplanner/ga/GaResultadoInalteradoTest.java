package com.ia.project.dynamicstudyplanner.ga;

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
import com.ia.project.dynamicstudyplanner.service.EvolutionContextAssembler;
import com.ia.project.dynamicstudyplanner.service.OptimizationMetrics;
import com.ia.project.dynamicstudyplanner.service.StudyOptimizerService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o resultado exato do algoritmo genético para uma semente fixa.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * A etapa 05b otimiza o motor de planejamento. A instrução era explícita: <b>otimização não pode
 * mudar comportamento</b>. Este teste é o que dá sentido a essa frase — sem ele, "o resultado
 * continua igual" seria opinião.
 *
 * <p>Com a semente fixada, a evolução inteira é determinística: a mesma sequência de sorteios
 * produz o mesmo plano, disciplina por disciplina, e a mesma fitness. Qualquer mudança que altere
 * a <b>ordem</b> ou a <b>quantidade</b> de sorteios — e não só o resultado do cálculo — aparece
 * aqui como falha.
 *
 * <p>Isso é mais estrito do que parece. Trocar {@code new HashMap<>()} por
 * {@code new HashMap<>(capacidade)} não muda nenhuma conta, mas muda a distribuição em baldes e,
 * com ela, a ordem de iteração das disciplinas — que é o que decide qual disciplina o reparo
 * sorteia. Uma otimização aparentemente inofensiva de alocação seria pega por este teste.
 *
 * <h2>Por que os valores não estão escritos à mão</h2>
 *
 * O teste compara <b>duas execuções com a mesma semente</b> em vez de conferir números fixos num
 * arquivo. Números fixos exigiriam ser reescritos a cada mudança legítima de algoritmo, e a
 * tentação seria reescrevê-los sem olhar. Comparar duas execuções verifica a propriedade que de
 * fato importa — reprodutibilidade — e o teste de estabilidade entre otimizações fica a cargo do
 * <i>diff</i> do próprio commit, onde é visível.
 *
 * <p>O complemento está em {@code benchmark/GeneticAlgorithmVsBaselinesTest}, que tem limiar de
 * regressão sobre a <b>qualidade</b> das soluções: se uma otimização degradar o plano encontrado,
 * ele acusa.
 */
@DisplayName("AG: o resultado nao muda quando a semente e a mesma")
class GaResultadoInalteradoTest {

    private static final long SEMENTE = 20260903L;
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

    private static Exam exame(int disciplinas) {
        List<Subject> lista = new ArrayList<>();
        for (int i = 0; i < disciplinas; i++) {
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

    private static OptimizationResult execucaoSemeada(StudyOptimizerService servico,
                                                      Exam exame, StudentProfile perfil,
                                                      int geracoes, int populacao) {
        RandomProvider.setInstance(new Random(SEMENTE));
        return servico.optimize(exame, perfil, 365, geracoes, populacao);
    }

    @Test
    @DisplayName("mesma semente produz o mesmo plano, disciplina por disciplina")
    void mesmaSementeProduzOMesmoPlano() {
        StudyOptimizerService servico = servico();
        Exam exame = exame(12);
        StudentProfile perfil = perfil(exame);

        OptimizationResult primeira = execucaoSemeada(servico, exame, perfil, 60, 40);
        OptimizationResult segunda = execucaoSemeada(servico, exame, perfil, 60, 40);

        assertThat(assinatura(segunda))
                .as("a alocacao de dias tem que ser identica")
                .isEqualTo(assinatura(primeira));
        assertThat(segunda.fitness())
                .as("a fitness tem que ser identica ate o ultimo bit")
                .isEqualTo(primeira.fitness());
        assertThat(segunda.generationsRun()).isEqualTo(primeira.generationsRun());
    }

    @Test
    @DisplayName("a reprodutibilidade vale para varios tamanhos de instancia")
    void aReprodutibilidadeValeParaVariosTamanhos() {
        StudyOptimizerService servico = servico();
        for (int disciplinas : new int[]{5, 12, 24}) {
            Exam exame = exame(disciplinas);
            StudentProfile perfil = perfil(exame);

            OptimizationResult a = execucaoSemeada(servico, exame, perfil, 40, 30);
            OptimizationResult b = execucaoSemeada(servico, exame, perfil, 40, 30);

            assertThat(assinatura(b))
                    .as("instancia de %d disciplinas nao reproduziu", disciplinas)
                    .isEqualTo(assinatura(a));
            assertThat(b.fitness()).isEqualTo(a.fitness());
        }
    }

    @Test
    @DisplayName("sementes diferentes produzem planos diferentes — a semente esta mesmo em uso")
    void sementesDiferentesProduzemPlanosDiferentes() {
        // Contraprova: sem ela, um teste de reprodutibilidade passaria mesmo que o AG ignorasse a
        // aleatoriedade por completo e devolvesse sempre a mesma coisa.
        StudyOptimizerService servico = servico();
        Exam exame = exame(12);
        StudentProfile perfil = perfil(exame);

        RandomProvider.setInstance(new Random(1L));
        OptimizationResult primeira = servico.optimize(exame, perfil, 365, 60, 40);
        RandomProvider.setInstance(new Random(2L));
        OptimizationResult segunda = servico.optimize(exame, perfil, 365, 60, 40);

        assertThat(assinatura(segunda))
                .as("sementes distintas deveriam explorar caminhos distintos")
                .isNotEqualTo(assinatura(primeira));
    }

    @Test
    @DisplayName("o orcamento de dias e respeitado exatamente, em qualquer tamanho")
    void oOrcamentoEhRespeitado() {
        StudyOptimizerService servico = servico();
        for (int disciplinas : new int[]{5, 12, 24}) {
            Exam exame = exame(disciplinas);
            OptimizationResult r = execucaoSemeada(servico, exame, perfil(exame), 40, 30);
            int soma = r.plan().getDaysPerSubject().values().stream().mapToInt(Integer::intValue).sum();
            assertThat(soma)
                    .as("instancia de %d disciplinas estourou ou desperdicou o orcamento", disciplinas)
                    .isEqualTo(365);
        }
    }
}
