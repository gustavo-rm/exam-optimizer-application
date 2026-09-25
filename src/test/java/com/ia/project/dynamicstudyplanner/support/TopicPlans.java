package com.ia.project.dynamicstudyplanner.support;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.Population;
import com.ia.project.dynamicstudyplanner.ga.config.DefaultGeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.generator.DefaultPopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.TournamentSelection;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import com.ia.project.dynamicstudyplanner.sinapse.AvailabilityWindows;
import com.ia.project.dynamicstudyplanner.sinapse.DailyLoadBudgetObjective;
import com.ia.project.dynamicstudyplanner.sinapse.SinapseEvolutionContexts;
import com.ia.project.dynamicstudyplanner.sinapse.TopicPlanningItems;
import com.ia.project.dynamicstudyplanner.sinapse.importance.GoalPriorityImportance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Instâncias de tamanho arbitrário no domínio de tópicos, para os testes do núcleo do AG.
 *
 * <h2>Por que esta classe existe</h2>
 *
 * Até EOA-4b, os testes do AG montavam suas instâncias com {@code Exam} e {@code Subject} e
 * derivavam o contexto de {@code EvolutionContextAssembler}. Os três saíram com o caminho de
 * concurso, e o que restou é o caminho que a plataforma de fato chama: um {@link PlanRequest} vira
 * contexto por {@link SinapseEvolutionContexts}, que é o único montador de produção.
 *
 * <p>As instâncias daqui <b>não</b> são as do documento de referência — aquelas estão em
 * {@code plan/PlanRequests} e existem para provar forma de contrato. Estas existem para variar
 * <b>tamanho</b>: um teste de borda precisa de 1 e de 200 tópicos, e o documento tem 4.
 *
 * <h2>O que varia entre os tópicos, e por quê</h2>
 *
 * Faixa de esforço e duração estimada variam com o índice, e os tópicos se dividem em duas
 * disciplinas com prioridades de meta diferentes. Sem essa variação, todo tópico pesaria igual na
 * fitness e qualquer teste sobre "a busca escolheu algo" viraria um teste sobre como um empate foi
 * desfeito.
 */
public final class TopicPlans {

    /** Faixas do conjunto fechado do contrato, em ordem crescente de esforço. */
    private static final String[] TIERS = {"SHORT", "STANDARD", "LONG", "EXTENDED"};

    private static final UUID SUBJECT_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SUBJECT_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final LocalDate START = LocalDate.parse("2026-09-01");

    /** Hora em que cada janela diária começa. Só precisa ser a mesma em todas as instâncias. */
    private static final LocalTime WINDOW_START = LocalTime.of(9, 0);

    private TopicPlans() {
    }

    /**
     * A composição de fitness do caminho SINAPSE, montada à mão.
     *
     * <p>É a mesma lista que {@code SinapseFitnessConfig} declara — os mesmos três objetivos e as
     * mesmas duas restrições, sem penalidade. {@code SinapseFitnessTermsTest} trava a equivalência;
     * aqui ela é repetida porque um teste de núcleo não sobe o contexto do Spring.
     *
     * @return o avaliador do caminho de produção
     */
    public static FitnessEvaluator evaluator() {
        return new FitnessEvaluator(
                List.of(new ScoreGainObjective(), new RetentionObjective(),
                        new DailyLoadBudgetObjective()),
                List.of(),
                List.of(new MinimumDaysConstraint(),
                        new MandatoryReviewConstraint(new HybridRetentionEngine())));
    }

    /**
     * Um pedido com {@code topics} tópicos, horizonte de {@code horizonDays} dias e uma janela
     * diária de {@code hoursPerDay} horas.
     *
     * @param topics      quantos tópicos planejar
     * @param horizonDays dias do horizonte, contando o primeiro e o último
     * @param hoursPerDay horas disponíveis em cada dia do horizonte
     * @return o pedido, na forma que a plataforma envia
     */
    public static PlanRequest request(int topics, int horizonDays, int hoursPerDay) {
        return new PlanRequest(PlanRequest.VERSION,
                new PlanRequest.Horizon(START, START.plusDays(horizonDays - 1L)),
                availability(horizonDays, hoursPerDay),
                List.of(new PlanRequest.Goal(SUBJECT_A, START.plusDays(horizonDays - 1L), 5),
                        new PlanRequest.Goal(SUBJECT_B, null, 2)),
                topics(topics),
                List.of(), List.of(), Map.of(), 0L);
    }

    /** O mesmo, com um horizonte folgado: 365 dias de 5 horas. */
    public static PlanRequest request(int topics) {
        return request(topics, 365, 5);
    }

    /**
     * O contexto que o AG recebe, montado pelo montador de produção.
     *
     * @param request o pedido
     * @return o contexto, sem nenhum campo derivado de dado que a plataforma não envia
     */
    public static EvolutionContext context(PlanRequest request) {
        List<PlanningItem> items = TopicPlanningItems.of(request.topics());
        return SinapseEvolutionContexts.of(request, items,
                AvailabilityWindows.of(request.availability()), evaluator(),
                new HybridRetentionEngine(), new GoalPriorityImportance());
    }

    /** Atalho para {@code context(request(topics))}. */
    public static EvolutionContext context(int topics) {
        return context(request(topics));
    }

    /**
     * Roda a evolução e devolve o cromossomo mais apto.
     *
     * <p>É a mesma sequência de {@code GeneticPlanEngine.evolve}: gerar a população, evoluir
     * {@code generations} vezes, ler o mais apto. Os testes de núcleo chamam isto em vez do motor
     * porque precisam variar o orçamento e o tamanho da instância, que o motor deriva do pedido.
     *
     * <p><b>Não instala semente.</b> Quem quiser reprodutibilidade chama
     * {@code RandomProvider.setInstance} antes, e é isso que torna a ausência de semente visível no
     * teste que depende dela.
     *
     * @param context     o contexto
     * @param budget      sessões a distribuir
     * @param generations gerações a evoluir
     * @param population  tamanho da população
     * @return o plano mais apto da última geração
     */
    public static StudyPlan optimize(EvolutionContext context, int budget, int generations,
            int population) {
        GeneticAlgorithm algorithm = new DefaultGeneticAlgorithmFactory(new TournamentSelection(),
                new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover()),
                new CreepMutation()).create();
        Population current = new DefaultPopulationGenerator().generate(budget, population, context);
        for (int generation = 0; generation < generations; generation++) {
            current = algorithm.evolvePopulation(current, context);
        }
        return current.getFittest().getPlan();
    }

    /**
     * Assinatura do plano, independente da ordem em que o mapa itera.
     *
     * @param plan o cromossomo
     * @return {@code id=dias} por item, em ordem de identificador
     */
    public static String signature(StudyPlan plan) {
        return plan.getDaysPerItem().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .reduce((a, b) -> a + "|" + b)
                .orElse("<vazio>");
    }

    private static List<PlanRequest.Topic> topics(int count) {
        List<PlanRequest.Topic> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new PlanRequest.Topic(topicId(i), i % 2 == 0 ? SUBJECT_A : SUBJECT_B,
                    (i / 2) + 1, TIERS[i % TIERS.length], 30 + (i * 30) % 150));
        }
        return list;
    }

    private static List<PlanRequest.AvailabilitySlot> availability(int horizonDays, int hoursPerDay) {
        List<PlanRequest.AvailabilitySlot> slots = new ArrayList<>(horizonDays);
        for (int day = 0; day < horizonDays; day++) {
            Instant from = START.plusDays(day).atTime(WINDOW_START).toInstant(ZoneOffset.UTC);
            slots.add(new PlanRequest.AvailabilitySlot(from, from.plusSeconds(3600L * hoursPerDay)));
        }
        return slots;
    }

    /** Identificadores estáveis e distintos, derivados do índice. */
    private static UUID topicId(int index) {
        return new UUID(0xa000000000004000L, 0x8000000000000000L | index);
    }
}
