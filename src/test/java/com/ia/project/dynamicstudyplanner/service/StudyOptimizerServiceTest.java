package com.ia.project.dynamicstudyplanner.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.config.DefaultGeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.CognitiveLoadObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.generator.DefaultPopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.TournamentSelection;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A orquestração que restou em {@code StudyOptimizerService} depois do achado E8.
 *
 * <h2>O que vale testar aqui</h2>
 *
 * Cálculo, montagem do contexto e contagem saíram para {@link EvolutionContextAssembler} e
 * {@link OptimizationMetrics}, que têm testes próprios. O que sobrou nesta classe é a <b>sequência</b>
 * — e dois pontos dela não estavam cobertos por nada:
 *
 * <ul>
 *   <li><b>O bloco de rastreio dentro do laço de gerações.</b> Ele só executa com o log em
 *       {@code TRACE}, o que nenhum teste ligava. Formata {@code getWorst()} e
 *       {@code getAverageFitness()}, chamadas que <i>não</i> aparecem em nenhum outro caminho: se
 *       uma delas falhasse numa população degenerada, o defeito só apareceria em produção, e
 *       justamente quando alguém tivesse ligado o rastreio para investigar outro problema.</li>
 *   <li><b>A ligação entre a duração medida e a duração relatada.</b> O tempo que volta na resposta
 *       da API tem que vir da mesma medição que alimenta o monitoramento — não de um segundo
 *       relógio.</li>
 * </ul>
 */
@DisplayName("StudyOptimizerService: a orquestracao")
class StudyOptimizerServiceTest {

    private static final LocalDate HOJE = LocalDate.now();

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final StudyOptimizerService servico = servico(registro);

    private Logger logger;
    private ListAppender<ILoggingEvent> coletor;

    @BeforeEach
    void anexarColetor() {
        logger = (Logger) LoggerFactory.getLogger(StudyOptimizerService.class);
        coletor = new ListAppender<>();
        coletor.start();
        logger.addAppender(coletor);
        logger.setLevel(Level.TRACE);
    }

    @AfterEach
    void removerColetor() {
        logger.detachAppender(coletor);
        coletor.stop();
        logger.setLevel(null);
    }

    @Test
    @DisplayName("o laco de geracoes registra o progresso quando o rastreio esta ligado")
    void oLacoRegistraOProgressoEmRastreio() {
        // Seis geracoes: o bloco de rastreio dispara a cada cinco, entao i=0 e i=5 passam por ele.
        servico.optimize(exame(), perfil(), 120, 6, 8);

        List<String> rastreios = coletor.list.stream()
                .filter(evento -> evento.getLevel() == Level.TRACE)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(rastreios)
                .as("com TRACE ligado e seis geracoes, o progresso sai duas vezes (i=0 e i=5)")
                .hasSize(2);
        assertThat(rastreios.get(0))
                .as("a linha precisa trazer as tres leituras da populacao, nao so a melhor")
                .contains("Best Fitness:")
                .contains("Avg Fitness:")
                .contains("Worst Fitness:");

        assertThat(coletor.list)
                .as("o encerramento da evolucao e registrado uma vez, em INFO")
                .filteredOn(evento -> evento.getLevel() == Level.INFO)
                .singleElement()
                .extracting(ILoggingEvent::getFormattedMessage).asString()
                .contains("Evolution complete after 6 generations");
    }

    @Test
    @DisplayName("a duracao relatada vem da medicao, e a rodada e contabilizada")
    void aDuracaoRelatadaVemDaMedicao() {
        OptimizationResult resultado = servico.optimize(exame(), perfil(), 120, 3, 8);

        assertThat(resultado.generationsRun())
                .as("o numero de geracoes relatado e o pedido")
                .isEqualTo(3);
        assertThat(resultado.executionTimeMillis())
                .as("a duracao vem da medicao real, nao de um valor fixo")
                .isNotNegative();

        double registrada = registro.get("dynamicstudyplanner.optimization.duration")
                .timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);

        assertThat(registro.get("dynamicstudyplanner.optimization.runs").counter().count())
                .as("a orquestracao precisa passar pela medicao, e nao contornar")
                .isEqualTo(1.0);
        assertThat(registrada)
                .as("o monitoramento e a resposta da API leem a MESMA medicao")
                .isGreaterThanOrEqualTo(resultado.executionTimeMillis());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static StudyOptimizerService servico(SimpleMeterRegistry registro) {
        ImportanceCalculator importanceCalculator = new ImportanceCalculator();
        return new StudyOptimizerService(
                new EvolutionContextAssembler(
                        new BaselineCalculator(importanceCalculator),
                        importanceCalculator,
                        new CognitiveLoadCalculator(),
                        new FitnessEvaluator(
                                List.of(new ScoreGainObjective(), new RetentionObjective(),
                                        new CognitiveLoadObjective()),
                                List.of(), List.of())),
                new DefaultGeneticAlgorithmFactory(
                        new TournamentSelection(),
                        new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover()),
                        new CreepMutation()),
                new DefaultPopulationGenerator(),
                new OptimizationMetrics(registro));
    }

    private static Exam exame() {
        return new Exam("Concurso", HOJE.plusDays(200), 100.0,
                List.of(new Subject("Portugues", 20, 3), new Subject("Matematica", 15, 4)),
                List.of());
    }

    private static StudentProfile perfil() {
        Map<Subject, Double> lacunas = new HashMap<>();
        for (Subject disciplina : exame().getGeneralKnowledgeSubjects()) {
            lacunas.put(disciplina, 3.0);
        }
        Map<DayOfWeek, Integer> disponibilidade = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dia : DayOfWeek.values()) {
            disponibilidade.put(dia, 3);
        }
        return new StudentProfile("Aluno", lacunas, disponibilidade,
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE));
    }
}
