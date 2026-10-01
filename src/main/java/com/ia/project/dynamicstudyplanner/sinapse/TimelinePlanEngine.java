package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;
import com.ia.project.dynamicstudyplanner.ga.tactical.TacticalSlots;
import com.ia.project.dynamicstudyplanner.ga.tactical.repair.SpacedRepetitionRepairer;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.crossover.DayBoundaryCrossover;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation.BlockSwapMutation;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation.MethodologyMutation;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlanEngine;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.plan.PlanRequestGuard;
import com.ia.project.dynamicstudyplanner.plan.PrecedencePolicy;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteOrderRepairer;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteReport;
import com.ia.project.dynamicstudyplanner.plan.SoftPrerequisiteEdges;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategy;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineChromosomes;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineRepairer;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineSearch;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * {@code POST /plans} pelo cromossomo de linha do tempo: a <b>terceira</b> condição do experimento.
 *
 * <h2>O que distingue esta condição das outras duas</h2>
 *
 * <table>
 *   <caption>As três condições, e o que cada uma decide sobre a ordem</caption>
 *   <tr><th>condição</th><th>alocação</th><th>ordem</th><th>preferências SOFT</th></tr>
 *   <tr><td>{@code greedy-baseline}</td><td>heurística</td><td>pressão de meta, depois posição
 *       curricular</td><td>nem repara nem busca — mede</td></tr>
 *   <tr><td>{@code ga} (v1)</td><td><b>buscada</b></td><td>derivada da alocação, depois
 *       reparada</td><td>reparo pós-processado</td></tr>
 *   <tr><td>{@code ga-timeline} (v2)</td><td>determinística</td><td><b>buscada</b></td>
 *       <td>precificadas <b>dentro</b> da fitness, sem reparo</td></tr>
 * </table>
 *
 * <p>A hipótese da v2 é a da última coluna: se a ordem é gene, o termo de preferências passa a
 * <b>guiar a busca</b> em vez de ser corrigido depois. Na v1 aquele termo devolve 0 no cromossomo
 * macro por construção — ele não tem calendário para violar uma preferência — e por isso a v1 é o
 * <b>grupo de controle</b> da v2, e não um rascunho dela ({@code CLAUDE.md} §1b).
 *
 * <p><b>A v2 não busca a alocação, e isso é limitação declarada.</b> Ver
 * {@code TimelineChromosomes}: a contagem de sessões por item é determinística, para que uma
 * diferença medida entre v1 e v2 seja atribuível à ordem. A consequência é que numa instância onde o
 * ganho vem de alocar melhor, a v2 não tem como competir — e o relatório precisa poder concluir isso
 * sem que seja tratado como falha.
 *
 * <h2>O orçamento de busca é o mesmo da v1</h2>
 *
 * Mesmas gerações, mesma população ({@code GeneticSearchBudget}), mesmas taxas
 * ({@code TimelineSearch}), mesmo orçamento de sessões ({@link SessionBudget}, extraído de
 * {@code GeneticPlanEngine} exatamente para não haver duas fórmulas). Sem isso, a comparação de custo
 * mediria a configuração.
 *
 * <h2>Determinismo</h2>
 *
 * A semente do pedido é instalada na thread e <b>restaurada num {@code finally}</b>, o padrão de
 * EOA-3. Nenhum mapa da rota é ordenado por hash: {@link TacticalSlots} ordena os slots pelo
 * calendário e todo desempate é por texto de identificador.
 *
 * <h2>{@code elapsedMillis} é zero, como nos outros dois</h2>
 *
 * Medi-lo faria duas execuções do mesmo pedido semeado diferirem. {@code generations} é a contagem
 * real.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class TimelinePlanEngine implements PlanEngine {

    /** O id da terceira condição do experimento. */
    public static final String ID = "ga-timeline";

    /** Ver o comentário da classe: uma duração medida quebraria a reprodutibilidade. */
    private static final long ELAPSED_MILLIS = 0L;

    private final FitnessComposition composition;
    private final RetentionAlgorithm retention;
    private final RequestConditions conditions;
    private final SelectionStrategy selection;
    private final SpacedRepetitionRepairer retentionRepairer;
    private final GeneticSearchBudget budget;
    private final String coreVersion;

    /**
     * @param composition       a fitness deste caminho, por nome de bean
     * @param retention         a recorrência que o termo de retenção lê
     * @param conditions        qual célula do fatorial esta requisição nomeia: proveniência,
     *                          importância e política de precedência
     * @param selection         seleção, a mesma do caminho macro
     * @param retentionRepairer devolve revisões obrigatórias perdidas pelos operadores
     * @param budget            quanta busca gastar; o mesmo da v1
     * @param coreVersion       o build, relatado como {@code metadata.coreVersion}
     */
    public TimelinePlanEngine(
            @Qualifier("sinapseFitnessComposition") FitnessComposition composition,
            RetentionAlgorithm retention,
            RequestConditions conditions,
            SelectionStrategy selection,
            SpacedRepetitionRepairer retentionRepairer,
            GeneticSearchBudget budget,
            @Value("${baseline.core.version}") String coreVersion) {

        this.composition = composition;
        this.retention = retention;
        this.conditions = conditions;
        this.selection = selection;
        this.retentionRepairer = retentionRepairer;
        this.budget = budget;
        this.coreVersion = coreVersion;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PlanResponse plan(PlanRequest request) {
        PlanRequestGuard.check(request);
        EffortTierBands.checkEvery(request.topics());

        Random previous = RandomProvider.getInstance();
        RandomProvider.setInstance(new Random(request.randomSeed()));
        try {
            return run(request);
        } finally {
            RandomProvider.setInstance(previous);
        }
    }

    private PlanResponse run(PlanRequest request) {
        EdgeProvenanceFilter filter = conditions.provenance(request);
        // WEIGHTED e o padrao DESTE motor: e o comportamento que o relatorio 10 mediu como
        // tratamento e que o 11 varreu. Ver PrecedencePolicies para por que o padrao e por motor.
        PrecedencePolicy policy = conditions.precedence(request, PrecedencePolicy.WEIGHTED);
        HardPrerequisiteGraph graph =
                HardPrerequisiteGraph.of(request.topics(), request.prerequisites(), filter);
        SoftPrerequisiteEdges soft =
                SoftPrerequisiteEdges.of(request.topics(), request.prerequisites(), filter);

        ImportanceStrategy importance = conditions.importance(request);
        List<PlanningItem> items = TopicPlanningItems.of(request.topics());
        EvolutionContext context = SinapseEvolutionContexts.of(request, items,
                AvailabilityWindows.of(request.availability()), composition.evaluator(), retention,
                importance, SinapseEvolutionContexts.softPrerequisites(soft, request.topics()));

        Map<PlanningItem, UUID> topicIdsByItem = TopicPlanningItems.topicIdsByItem(request.topics());
        Searched searched = search(request, context, graph, topicIdsByItem);
        TacticalStudyPlan fittest = searched.fittest();
        if (fittest.getSchedule().isEmpty()) {
            throw new PlanRejectedException("plan-would-be-empty",
                    "No session fits: the availability windows inside the horizon cannot hold even "
                            + "the first topic of the study order. An empty plan is refused rather "
                            + "than returned, because the platform rejects one anyway.",
                    List.of(request.topics().get(0).id().toString()));
        }

        return respond(request, context, graph, soft, new Reported(filter, policy, importance),
                topicIdsByItem, searched);
    }

    /**
     * O que a busca devolve, e o que o reparo lexicográfico precisa dela.
     *
     * <p>Sob {@code LEXICOGRAPHIC} o plano vencedor é <b>reordenado e recolocado</b>, e isso exige o
     * reparador e os blocos canônicos — que a busca já construiu. Devolvê-los evita reconstruí-los,
     * e reconstruí-los seria reconstruir a alocação, que precisa ser a mesma das duas políticas para
     * que a comparação entre elas meça só a política.
     *
     * @param fittest   o cromossomo mais apto
     * @param vitality  os sinais de que a busca buscou
     * @param repairer  o reparador de linha do tempo desta requisição
     * @param blocks    os blocos canônicos por item
     */
    private record Searched(TacticalStudyPlan fittest, SearchVitality vitality,
            TimelineRepairer repairer, Map<PlanningItem, List<TacticalStudyBlock>> blocks) {
    }

    /**
     * As três escolhas que a resposta ecoa, agrupadas para o construtor de {@code respond} caber no
     * limite de parâmetros.
     *
     * @param filter     a condição de ablação
     * @param policy     a política de precedência
     * @param importance a estratégia de importância
     */
    private record Reported(EdgeProvenanceFilter filter, PrecedencePolicy policy,
            ImportanceStrategy importance) {
    }

    /** Gera a população inicial, já reparada, e evolui. */
    private Searched search(PlanRequest request, EvolutionContext context,
            HardPrerequisiteGraph graph, Map<PlanningItem, UUID> topicIdsByItem) {

        Map<PlanningItem, Integer> sessions =
                TimelineChromosomes.sessionsPerItem(context, SessionBudget.of(request, context));

        Map<UUID, PlanRequest.Topic> topicsById = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        request.topics().forEach(topic -> topicsById.put(topic.id(), topic));
        Map<PlanningItem, List<TacticalStudyBlock>> blocksBy = new LinkedHashMap<>();
        for (PlanningItem item : context.importanceScores().keySet()) {
            blocksBy.put(item, TimelineChromosomes.blocksOf(item,
                    topicsById.get(topicIdsByItem.get(item)), sessions.getOrDefault(item, 1)));
        }
        TimelineRepairer repairer =
                new TimelineRepairer(request, graph, topicIdsByItem, blocksBy);

        List<PlanningItem> canonical = List.copyOf(context.importanceScores().keySet());
        List<TacticalStudyPlan> seeds = new ArrayList<>(budget.populationSize());
        // O primeiro semeador é a ordem canônica, não uma permutação: sem ele a população inicial
        // poderia não conter nenhuma ordem próxima da curricular, e a v2 começaria atrás da v1 por
        // sorteio em vez de por mérito.
        seeds.add(repairer.repair(TimelineChromosomes.inOrder(canonical, blocksBy), context));
        for (int index = 1; index < budget.populationSize(); index++) {
            seeds.add(repairer.repair(
                    TimelineChromosomes.inOrder(TimelineChromosomes.shuffled(canonical), blocksBy),
                    context));
        }

        TimelineSearch.Outcome outcome = new TimelineSearch(selection, new DayBoundaryCrossover(),
                new BlockSwapMutation(), new MethodologyMutation(), retentionRepairer, repairer)
                .run(seeds, context, budget.generations());
        return new Searched(outcome.fittest(), outcome.vitality(), repairer, blocksBy);
    }

    /** Monta a resposta a partir do cromossomo vencedor, que já é o plano tático. */
    private PlanResponse respond(PlanRequest request, EvolutionContext context,
            HardPrerequisiteGraph graph, SoftPrerequisiteEdges soft, Reported reported,
            Map<PlanningItem, UUID> topicIdsByItem, Searched searched) {

        TacticalStudyPlan plan = searched.fittest();
        List<PlanRequest.Topic> order = fullOrder(request, topicIdsByItem, plan);

        // SOB LEXICOGRAPHIC, o reparador QUE JA EXISTE roda sobre a saida da linha do tempo.
        //
        // E a celula que desconfunde representacao de politica. A v2 publicada precifica a ordem
        // dentro da busca; esta celula mantem a MESMA representacao e a MESMA alocacao e troca so a
        // politica por reparo lexicografico. Se ela igualar a v1 em inversoes e preservar a vantagem
        // de cobertura, a representacao explica o ganho; se perder cobertura ate o nivel da v1, a
        // vantagem era consequencia da politica e nao da representacao.
        //
        // Nenhum segundo reparador foi escrito: PrerequisiteOrderRepairer reordena a lista de topicos
        // e TimelineRepairer recoloca o calendario naquela ordem. A ordem reparada ja respeita as
        // arestas rigidas, entao a ordenacao topologica do segundo e inerte sobre ela.
        PrerequisiteOrderRepairer.Result repair = null;
        if (reported.policy() == PrecedencePolicy.LEXICOGRAPHIC) {
            repair = PrerequisiteOrderRepairer.repair(order, graph, soft);
            order = repair.order();
            plan = searched.repairer().repair(
                    TimelineChromosomes.inOrder(itemsOf(order, topicIdsByItem), searched.blocks()),
                    context);
        }

        int scheduled = scheduledCount(plan);
        long minutes = plan.getSchedule().values().stream()
                .mapToLong(TacticalStudyBlock::durationMinutes)
                .sum();
        SessionPlacement.Result placed = new SessionPlacement.Result(plan, scheduled,
                request.topics().size(), minutes);

        // measured sob WEIGHTED, repaired sob LEXICOGRAPHIC. A chave -before-repair aparece so na
        // segunda, que e a declaracao por omissao que o resto do repositorio tambem faz.
        PrerequisiteReport prerequisites = repair == null
                ? PrerequisiteReport.measured(reported.filter(), graph, soft, order, scheduled)
                : PrerequisiteReport.repaired(reported.filter(), graph, soft, repair, order,
                        scheduled);

        FitnessBreakdown breakdown = composition.evaluator().explain(plan, context);
        PlanResponse response = new PlanResponse(PlanRequest.VERSION,
                TacticalSessions.of(plan, topicIdsByItem),
                SinapseFitness.of(composition, breakdown, placed, plan, context,
                        reported.importance(), prerequisites, searched.vitality()),
                new PlanResponse.ExecutionMetadata(coreVersion, request.randomSeed(),
                        budget.generations(), ELAPSED_MILLIS));

        PlanOutputInvariants.check(request, response, graph);
        return response;
    }

    /**
     * A ordem completa: os agendados na ordem do calendário, depois o que não couve.
     *
     * <p>Passar só os agendados faria {@code PrerequisiteReport} concluir que nada ficou de fora, e a
     * resposta afirmaria um plano completo onde há um plano parcial — o defeito que "nomear em vez de
     * contar" existe para impedir.
     */
    private static List<PlanRequest.Topic> fullOrder(PlanRequest request,
            Map<PlanningItem, UUID> topicIdsByItem, TacticalStudyPlan plan) {

        List<PlanRequest.Topic> scheduled = studyOrder(request, topicIdsByItem, plan);
        List<PlanRequest.Topic> order = new ArrayList<>(scheduled);
        request.topics().stream().filter(topic -> !scheduled.contains(topic)).forEach(order::add);
        return order;
    }

    /** Os itens de planejamento de uma ordem de tópicos, para reconstruir o cromossomo. */
    private static List<PlanningItem> itemsOf(List<PlanRequest.Topic> order,
            Map<PlanningItem, UUID> topicIdsByItem) {

        Map<UUID, PlanningItem> byId = new LinkedHashMap<>();
        topicIdsByItem.forEach((item, id) -> byId.put(id, item));
        return order.stream().map(topic -> byId.get(topic.id())).filter(item -> item != null).toList();
    }

    /** Tópicos com ao menos um bloco no plano. */
    private static int scheduledCount(TacticalStudyPlan plan) {
        return (int) plan.getSchedule().values().stream()
                .map(TacticalStudyBlock::item)
                .distinct()
                .count();
    }

    /**
     * Os tópicos agendados, na ordem em que o calendário os apresenta.
     *
     * <p>Só os agendados: o reparador trunca no primeiro bloco que não couber, então os que ficaram de
     * fora não estão no cromossomo. É deles que {@code PrerequisiteReport} monta a lista de não
     * agendados, comparando com os tópicos do pedido.
     */
    private static List<PlanRequest.Topic> studyOrder(PlanRequest request,
            Map<PlanningItem, UUID> topicIdsByItem, TacticalStudyPlan plan) {

        Map<UUID, PlanRequest.Topic> topicsById = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        request.topics().forEach(topic -> topicsById.put(topic.id(), topic));

        Set<UUID> seen = new LinkedHashSet<>();
        TacticalSlots.ordered(plan).forEach(slot ->
                seen.add(topicIdsByItem.get(plan.getSchedule().get(slot).item())));

        List<PlanRequest.Topic> order = new ArrayList<>(seen.size());
        seen.forEach(id -> order.add(topicsById.get(id)));
        return order;
    }
}
