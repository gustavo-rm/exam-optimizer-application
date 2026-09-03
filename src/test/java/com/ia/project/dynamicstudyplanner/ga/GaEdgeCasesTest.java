package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleResult;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleStatus;
import com.ia.project.dynamicstudyplanner.ga.config.DefaultGeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.factory.StudyPlanFactory;
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
import com.ia.project.dynamicstudyplanner.service.StudyScheduleGenerator;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor;
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.AllocationChains;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.InterleavedCriticalStrategy;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.ReviewFocusedStrategy;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Edge cases and robustness guarantees of the optimizer, from
 * {@code docs/revisao-ag/04-robustez.md}.
 * <p>
 * Each test pins behaviour that was either newly fixed or explicitly left as-is. Where the current
 * behaviour is known to be unsatisfactory but changing it would alter the API contract, the test
 * documents what the system does today and links to the pending decision, so the report and the
 * code cannot drift apart.
 */
@DisplayName("Casos de borda e robustez do AG")
class GaEdgeCasesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 2);

    @AfterEach
    void restoreProductionRandomness() {
        // Tests below pin the seed; leaving a fixed seed installed would leak into other tests,
        // since RandomProvider holds global static state.
        RandomProvider.setInstance(new SecureRandom());
    }

    // ------------------------------------------------------------------
    // Infeasible inputs must fail loudly and say why
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Entradas inviaveis")
    class InfeasibleInputs {

        @Test
        @DisplayName("orcamento menor que o piso de dias minimos falha com mensagem acionavel")
        void budgetBelowMinimumDaysFloorFails() {
            Exam exam = examWithSubjects(10);
            StudentProfile profile = profile(exam, 4);

            // Passou a ser DomainException na etapa 03d: a requisicao e bem formada e compreendida,
            // e o edital que exige mais dias do que o aluno tem. O cliente recebe 422, nao 400.
            // Ver ADR-0005 e docs/qualidade/03d-correcao-contrato-de-erro.md.
            assertThatThrownBy(() -> optimizer().optimize(exam, profile, 5, 20, 10))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Total minimum study days required")
                    .hasMessageContaining("exceeds total available days");
        }

        @Test
        @DisplayName("exame sem disciplinas falha com mensagem de dominio, nao com erro interno do JDK")
        void examWithNoSubjectsFailsClearly() {
            // Before the fix this surfaced java.util.Random's "bound must be positive", which says
            // nothing about the actual problem. See 04-robustez.md, correcao C2.
            StudyPlanFactory factory = new StudyPlanFactory();

            // Idem: regra de negocio sobre o edital, nao argumento malformado (etapa 03d).
            assertThatThrownBy(() -> factory.createRandomPlan(List.of(), 10, Map.of()))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("no subjects")
                    .hasMessageNotContaining("bound must be positive");
        }

        @Test
        @DisplayName("orcamento negativo e rejeitado")
        void negativeBudgetIsRejected() {
            Subject math = new Subject("Math", 10, 3);
            StudyPlanFactory factory = new StudyPlanFactory();

            assertThatThrownBy(() -> factory.createRandomPlan(List.of(math), -5, Map.of(math, 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        @DisplayName("disponibilidade semanal negativa e rejeitada na construcao do perfil")
        void negativeWeeklyAvailabilityIsRejected() {
            // Before the fix a negative availability propagated silently into a negative reduction
            // factor and an empty schedule labelled only as a time deficit. See correcao C3.
            Map<DayOfWeek, Integer> availability = new EnumMap<>(DayOfWeek.class);
            availability.put(DayOfWeek.MONDAY, 4);
            availability.put(DayOfWeek.TUESDAY, -3);

            assertThatThrownBy(() -> new StudentProfile("A", Map.of(), availability, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be negative")
                    .hasMessageContaining("TUESDAY");
        }
    }

    // ------------------------------------------------------------------
    // Degenerate but legal inputs must still produce a valid plan
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Entradas degeneradas porem legais")
    class DegenerateInputs {

        @Test
        @DisplayName("uma unica disciplina produz um plano valido")
        void singleSubjectProducesValidPlan() {
            Exam exam = examWithSubjects(1);
            StudentProfile profile = profile(exam, 4);

            OptimizationResult result = optimizer().optimize(exam, profile, 100, 30, 20);

            assertThat(result.plan().getTotalDays()).isEqualTo(100);
            assertThat(result.plan().getDaysPerSubject()).hasSize(1);
            assertThat(result.fitness()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("mutacao com uma unica disciplina devolve o individuo intacto, sem laco infinito")
        void mutationWithSingleSubjectIsANoOp() {
            // AbstractMutationStrategy.mutate short-circuits below two subjects. Without that guard
            // randomSubjectExcluding would spin forever looking for a different subject; the guard
            // added in this stage now also throws instead of hanging if it is ever reached.
            Subject only = new Subject("Only", 10, 3);
            Individual individual = new Individual(new StudyPlan(Map.of(only, 40)));
            EvolutionContext context = EvolutionContext.builder()
                    .importanceScores(Map.of(only, 5.0))
                    .minimumDaysPerSubject(Map.of(only, 1))
                    .planningHorizonDays(180)
                    .hoursPerStudyDay(4)
                    .maxDailyCognitiveLoad(20)
                    .build();

            Individual mutated = new CreepMutation().mutate(individual, 1.0, context);

            assertThat(mutated).isSameAs(individual);
        }

        @Test
        @DisplayName("disponibilidade semanal zero: hoje devolve cronograma vazio com aviso generico")
        void zeroAvailabilityCurrentlyYieldsAnEmptySchedule() {
            // Documenta o comportamento ATUAL, que a etapa 04 considera insatisfatorio mas nao
            // corrige: transformar isto em erro muda o contrato da API (200 -> 4xx) e por isso esta
            // listado como pendencia P1 em 04-robustez.md, aguardando decisao humana.
            Exam exam = examWithSubjects(5);
            StudentProfile profile = profile(exam, 0);

            OptimizationResult result = optimizer().optimize(exam, profile, 100, 20, 10);
            ScheduleResult schedule = scheduleFor(result.plan(), profile, exam);

            assertThat(result.plan().getTotalDays()).isEqualTo(100);
            assertThat(schedule.schedule()).isEmpty();
            assertThat(schedule.availableHours()).isZero();
            assertThat(schedule.status()).isEqualTo(ScheduleStatus.WARNING_TIME_DEFICIT);
        }

        @Test
        @DisplayName("data da prova no passado: hoje devolve cronograma vazio com aviso generico")
        void examDateInThePastCurrentlyYieldsAnEmptySchedule() {
            // Mesma situacao da anterior: pendencia P2 em 04-robustez.md.
            List<Subject> subjects = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                subjects.add(new Subject("S" + i, 10, 3));
            }
            Exam pastExam = new Exam("Passado", TODAY.minusDays(30), 100.0, subjects, List.of());
            StudentProfile profile = profile(pastExam, 4);

            OptimizationResult result = optimizer().optimize(pastExam, profile, 100, 20, 10);
            ScheduleResult schedule = scheduleFor(result.plan(), profile, pastExam);

            assertThat(schedule.schedule()).isEmpty();
            assertThat(schedule.availableHours()).isZero();
            assertThat(schedule.status()).isEqualTo(ScheduleStatus.WARNING_TIME_DEFICIT);
        }
    }

    // ------------------------------------------------------------------
    // Reproducibility
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Determinismo")
    class Determinism {

        @Test
        @DisplayName("mesma seed produz plano identico")
        void sameSeedProducesIdenticalPlan() {
            // This is the guarantee the whole robustness stage was about. Before the fixes the GA
            // drew from Math.random() and ThreadLocalRandom, and every operator captured
            // RandomProvider.getInstance() in a field at construction time, so a seed set afterwards
            // reached almost nothing. Reproducible in 2 of 8 benchmark instances; now 8 of 8.
            Exam exam = examWithSubjects(12);
            StudentProfile profile = profile(exam, 4);
            StudyOptimizerService optimizer = optimizer();

            RandomProvider.setInstance(new Random(20260830L));
            StudyPlan first = optimizer.optimize(exam, profile, 240, 60, 30).plan();

            RandomProvider.setInstance(new Random(20260830L));
            StudyPlan second = optimizer.optimize(exam, profile, 240, 60, 30).plan();

            assertThat(signature(second))
                    .as("A mesma seed deve reproduzir exatamente a mesma alocacao.")
                    .isEqualTo(signature(first));
        }

        @Test
        @DisplayName("seeds diferentes produzem planos diferentes (o AG continua estocastico)")
        void differentSeedsProduceDifferentPlans() {
            // Complement to the test above: the fix must not have collapsed the search into a
            // constant. A GA whose output no longer depends on the seed would not be searching.
            //
            // The exam here has varied question counts on purpose. With uniformly weighted subjects
            // the optimum is a single even split that every seed converges to, so the plans would
            // legitimately coincide and the test would say nothing about stochasticity.
            Exam exam = examWithVariedSubjects(12);
            StudentProfile profile = profile(exam, 4);
            StudyOptimizerService optimizer = optimizer();

            RandomProvider.setInstance(new Random(1L));
            StudyPlan first = optimizer.optimize(exam, profile, 240, 60, 30).plan();

            RandomProvider.setInstance(new Random(999L));
            StudyPlan second = optimizer.optimize(exam, profile, 240, 60, 30).plan();

            assertThat(signature(second)).isNotEqualTo(signature(first));
        }
    }

    // ------------------------------------------------------------------
    // Scalability
    // ------------------------------------------------------------------

    /**
     * <b>CONHECIDO-INSTAVEL — asserção de tempo de parede.</b> Registrado como T8 em
     * {@code docs/qualidade/01-diagnostico-testes.md} e tratado em
     * {@code docs/qualidade/01b-correcao-testes.md}.
     *
     * <p><b>Causa suspeita:</b> a asserção final compara tempo de parede (<i>wall-clock</i>, o tempo
     * real decorrido, que inclui pausas do coletor de lixo e concorrência com outros processos)
     * contra um limite fixo. Numa máquina de integração contínua compartilhada, uma pausa longa ou
     * um vizinho barulhento pode estourar o limite sem que nada no código tenha mudado. Não foi
     * observada nenhuma falha em 15 execuções (10 no diagnóstico, 5 na verificação final), e a folga
     * medida é grande — cerca de 150 ms contra um teto de 10 s, ou 66x —, então o teste continua
     * ativo em vez de ser removido.
     *
     * <p><b>Por que não foi corrigido agora:</b> substituir tempo por uma medida insensível à
     * máquina exigiria contar operações (avaliações de fitness, por exemplo), o que significa
     * instrumentar o motor do algoritmo genético — mudança em código de produção, fora do escopo de
     * uma etapa de testes. Fica como pendência P2.
     *
     * <p>A etiqueta {@code lento-e-sensivel-a-maquina} permite excluir este teste de uma execução
     * com {@code -Dgroups='!lento-e-sensivel-a-maquina'} caso ele passe a falhar de forma
     * intermitente, sem precisar editar o código.
     */
    @Test
    @Tag("lento-e-sensivel-a-maquina")
    @DisplayName("escala para 200 disciplinas dentro de um orcamento de tempo razoavel")
    void scalesToManySubjects() {
        Exam exam = examWithSubjects(200);
        StudentProfile profile = profile(exam, 6);

        // The minimum-days floor grows linearly with the subject count (see pendencia P5), so the
        // budget has to be derived from it rather than fixed, or the instance is infeasible.
        int floor = new BaselineCalculator(new ImportanceCalculator())
                .calculateMinimumDays(exam, profile).values().stream()
                .mapToInt(Integer::intValue).sum();
        int budget = (int) Math.ceil(floor * 1.25);

        long start = System.nanoTime();
        OptimizationResult result = optimizer().optimize(exam, profile, budget, 100, 50);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertThat(result.plan().getTotalDays()).isEqualTo(budget);
        assertThat(result.plan().getDaysPerSubject()).hasSize(200);
        // Generous bound: measured at roughly 150 ms on the review machine. The assertion guards
        // against an accidental change in complexity, not against machine-to-machine variation.
        assertThat(elapsedMillis)
                .as("200 disciplinas levaram %d ms; esperado bem abaixo de 10 s", elapsedMillis)
                .isLessThan(10_000L);
    }

    @Test
    @DisplayName("o piso de dias minimos cresce com o numero de disciplinas e pode inviabilizar o problema")
    void minimumDaysFloorGrowsWithSubjectCount() {
        // Pins the mechanism behind pendencia P5: with equally weighted subjects every one of them
        // is scaled to MAX_MINIMUM_DAYS, so the floor is 15 * n. The API caps totalStudyDays at
        // 365, which makes any such exam with 25 or more subjects impossible to satisfy.
        BaselineCalculator calculator = new BaselineCalculator(new ImportanceCalculator());

        Exam exam = examWithSubjects(25);
        int floor = calculator.calculateMinimumDays(exam, profile(exam, 4)).values().stream()
                .mapToInt(Integer::intValue).sum();

        assertThat(floor)
                .as("Piso de %d dias para 25 disciplinas de peso igual, contra o teto de 365 "
                        + "dias que GaConfigDto aceita.", floor)
                .isGreaterThan(365);
    }

    @Test
    @DisplayName("o plano gerado sempre respeita orcamento e piso de dias minimos")
    void producedPlansAreAlwaysFeasible() {
        Exam exam = examWithSubjects(15);
        StudentProfile profile = profile(exam, 5);
        Map<Subject, Integer> minimums = new BaselineCalculator(new ImportanceCalculator())
                .calculateMinimumDays(exam, profile);
        int budget = (int) Math.ceil(minimums.values().stream()
                .mapToInt(Integer::intValue).sum() * 1.4);

        for (long seed : new long[]{1L, 2L, 3L, 4L, 5L}) {
            RandomProvider.setInstance(new Random(seed));
            StudyPlan plan = optimizer().optimize(exam, profile, budget, 50, 30).plan();

            assertThat(plan.getTotalDays()).as("seed %d: orcamento", seed).isEqualTo(budget);
            assertThat(plan.meetsMinimumConstraints(minimums))
                    .as("seed %d: piso de dias minimos", seed).isTrue();
        }
    }

    /**
     * Reescrito na etapa 01b. A versão anterior era
     * {@code assertThatCode(...).doesNotThrowAnyException()} e não afirmava nada sobre o plano
     * produzido: um algoritmo que devolvesse um plano vazio, estourasse o orçamento ou reportasse
     * fitness fora de {@code [0,1]} passaria. A configuração mínima (1 geração, população 2) é o
     * caminho em que os laços do AG executam menos vezes, e é justamente onde um erro de contorno —
     * população que nunca evolui, elite não inicializada — apareceria primeiro.
     */
    @Test
    @DisplayName("configuracao minima do AG produz um resultado valido, nao apenas ausencia de excecao")
    void tinyGaConfigurationProducesAValidResult() {
        Exam exam = examWithSubjects(3);
        StudentProfile profile = profile(exam, 3);
        int budget = 60;

        OptimizationResult result = optimizer().optimize(exam, profile, budget, 1, 2);

        assertThat(result).as("o otimizador deve devolver um resultado").isNotNull();
        assertThat(result.plan()).as("o resultado deve conter um plano").isNotNull();
        assertThat(result.plan().getDaysPerSubject())
                .as("toda disciplina do edital precisa aparecer no plano, mesmo com 1 geracao")
                .hasSize(3)
                .allSatisfy((subject, days) -> assertThat(days)
                        .as("dias alocados para %s", subject.name())
                        .isNotNegative());
        assertThat(result.plan().getTotalDays())
                .as("o orcamento deve ser respeitado exatamente, mesmo na configuracao minima")
                .isEqualTo(budget);
        assertThat(result.fitness())
                .as("a fitness agregada e normalizada em [0,1] (05-fitness-function.md)")
                .isBetween(0.0, 1.0);
        assertThat(result.generationsRun())
                .as("uma geracao foi pedida, uma geracao deve ser reportada")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static StudyOptimizerService optimizer() {
        return new StudyOptimizerService(
                contextAssembler(),
                new DefaultGeneticAlgorithmFactory(
                        new TournamentSelection(),
                        new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover()),
                        new CreepMutation()),
                new DefaultPopulationGenerator(),
                new OptimizationMetrics(new SimpleMeterRegistry()));
    }

    /**
     * As calculadoras de dominio deixaram de ser dependencias diretas do servico na etapa 03e
     * (achado E8) e passaram a viver no montador do contexto. Este metodo existe para que a
     * mudanca fique num lugar so, se elas mudarem de novo.
     */
    private static EvolutionContextAssembler contextAssembler() {
        ImportanceCalculator importanceCalculator = new ImportanceCalculator();
        return new EvolutionContextAssembler(
                new BaselineCalculator(importanceCalculator),
                importanceCalculator,
                new CognitiveLoadCalculator(),
                fitnessEvaluator());
    }

    private static FitnessEvaluator fitnessEvaluator() {
        return new FitnessEvaluator(
                List.of(new ScoreGainObjective(), new RetentionObjective(), new CognitiveLoadObjective()),
                List.of(new DropoutRiskPenalty(new DropoutRiskPredictor()),
                        new FatigueAndSustainabilityPenalty(new FatigueAndEnergyModel())),
                List.of(new MinimumDaysConstraint(),
                        new MandatoryReviewConstraint(new HybridRetentionEngine())));
    }

    private static ScheduleResult scheduleFor(StudyPlan plan, StudentProfile profile, Exam exam) {
        int maxLoad = new CognitiveLoadCalculator().calculate(profile, exam);
        // Usa a definicao unica de AllocationChains: se producao trocar a ordem dos decoradores,
        // este teste passa a exercitar a cadeia nova sem que ninguem precise lembrar de atualiza-lo.
        return new StudyScheduleGenerator().generate(plan, profile, exam, TODAY,
                AllocationChains.production(maxLoad));
    }

    /** Uniformly weighted subjects: every subject has the same importance, so the optimum is unique. */
    private static Exam examWithSubjects(int count) {
        List<Subject> subjects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            subjects.add(new Subject("S" + i, 10, 3));
        }
        return new Exam("Exame", TODAY.plusDays(200), 100.0, subjects, List.of());
    }

    /**
     * Subjects with spread-out question counts, hence spread-out importances. Needed wherever the
     * test depends on the optimum not being a single trivially reachable even split.
     */
    private static Exam examWithVariedSubjects(int count) {
        List<Subject> subjects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            subjects.add(new Subject("V" + i, 2 + (i * 7) % 29, 1 + (i % 5)));
        }
        return new Exam("Exame variado", TODAY.plusDays(200), 100.0, subjects, List.of());
    }

    private static StudentProfile profile(Exam exam, int hoursPerDay) {
        Map<Subject, Double> gaps = new HashMap<>();
        for (Subject subject : exam.getAllSubjects()) {
            gaps.put(subject, 3.0);
        }
        Map<DayOfWeek, Integer> availability = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            availability.put(day, hoursPerDay);
        }
        return new StudentProfile("Aluno", gaps, availability,
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE));
    }

    /** Order-independent identity of an allocation, so plan equality does not depend on map order. */
    private static String signature(StudyPlan plan) {
        return plan.getDaysPerSubject().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Subject::name)))
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .reduce((a, b) -> a + "|" + b)
                .orElse("<vazio>");
    }
}
