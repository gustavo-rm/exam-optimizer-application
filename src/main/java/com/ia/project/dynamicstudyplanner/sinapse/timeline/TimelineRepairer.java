package com.ia.project.dynamicstudyplanner.sinapse.timeline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.tactical.TacticalSlots;
import com.ia.project.dynamicstudyplanner.ga.tactical.repair.ChromosomeRepairer;
import com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.sinapse.AvailabilityWindows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Devolve validade a um cromossomo tático: reordena para respeitar pré-requisitos e reempacota o
 * calendário para frente.
 *
 * <h2>Por que o reparo, e não operadores que já produzam planos válidos</h2>
 *
 * É o paradigma que {@link ChromosomeRepairer} declara, e a razão é aritmética. Um operador que
 * preservasse simultaneamente o multiconjunto de blocos, as janelas de disponibilidade, a
 * não-sobreposição e a ordem topológica teria de conhecer as quatro coisas — e cada operador novo
 * teria de reconhecê-las de novo. Reparar depois concentra as quatro num lugar, e deixa os operadores
 * livres para explorar: {@code BlockSwapMutation} troca dois blocos sem pensar em pré-requisito, e
 * {@code DayBoundaryCrossover} corta dias sem pensar em janela.
 *
 * <h2>O que ele garante, e como</h2>
 *
 * <ol>
 *   <li><b>Ordem topológica dos {@code HARD}.</b> A ordem observada dos itens — pela primeira
 *       aparição de cada um no calendário — alimenta um Kahn como <i>preferência</i>. O resultado é
 *       uma ordem que respeita todas as arestas rígidas e fica o mais perto possível da que o
 *       cromossomo pediu. O operador pode pedir qualquer permutação; o reparo nunca entrega uma
 *       inválida.</li>
 *   <li><b>Dentro das janelas, sem sobreposição, só para frente.</b> Os instantes são reatribuídos
 *       por {@link AvailabilityAllocator}, <b>o mesmo</b> que os outros dois motores usam. São
 *       propriedades do protocolo, não deste motor, e compartilhar a implementação é o que mantém as
 *       três condições comparáveis nos eixos que não estão sob teste.</li>
 *   <li><b>Truncamento, não salto.</b> A colocação para no primeiro bloco que não couber. O conjunto
 *       agendado segue sendo um <b>prefixo</b> de uma ordem topológica e, portanto, fechado sob
 *       pré-requisitos — nenhum aluno recebe um tópico cujo pré-requisito ficou de fora. É a mesma
 *       regra de {@code SessionPlacement}, e {@code PlanOutputInvariants} recusa o plano dos dois
 *       motores se ela quebrar.</li>
 * </ol>
 *
 * <h2>Todos os blocos de um item ficam juntos</h2>
 *
 * A reordenação é <b>por item</b>, não por bloco: quando o item A precede B, todos os blocos de A
 * precedem todos os blocos de B. É o que torna a garantia rígida verificável na leitura forte — nenhuma
 * sessão de um dependente começa antes de <i>toda</i> sessão do pré-requisito ter terminado — que é a
 * leitura que o harness de medição cobra em {@code Invariants.hardInversions}.
 *
 * <p>O preço é que este reparo não pode intercalar dois itens, e intercalar é útil para retenção. A v2
 * portanto <b>não</b> explora intercalação, e isso é limitação declarada, não descuido: explorá-la
 * exigiria enfraquecer a leitura forte para uma leitura por sessão, que é outra decisão.
 *
 * <h2>Ele também normaliza o multiconjunto de blocos, e sem isso a v2 não é o que diz ser</h2>
 *
 * {@code DayBoundaryCrossover} copia os dias até o corte de um pai e os seguintes do outro. Um item
 * que aparece nos dois lados sai <b>duplicado</b>; um que não aparece em nenhum sai <b>perdido</b>.
 * Duas consequências, e as duas foram observadas:
 *
 * <ul>
 *   <li><b>A alocação deixava de ser fixa.</b> A v2 se define por buscar a ordem com a contagem de
 *       sessões dada ({@code TimelineChromosomes}); com o multiconjunto à deriva ela passaria a
 *       buscar as duas coisas, e a comparação com a v1 mediria duas mudanças.</li>
 *   <li><b>Um dependente sobrevivia sem o pré-requisito.</b> Isto não é sutil: foi
 *       {@code PlanOutputInvariants} que o pegou, com um plano em que o tópico {@code ...0d} estava
 *       agendado e o {@code ...08} de que ele depende não estava no plano.</li>
 * </ul>
 *
 * <p>Então o reparo reconstrói a contagem <b>canônica</b> por item e conserva do cromossomo apenas o
 * que é gene: a <b>ordem</b> dos itens e a <b>metodologia</b> dos blocos. Com todos os itens sempre
 * presentes, a ordenação topológica é total e não existe resíduo — o caso em que um pré-requisito
 * ausente teria de excluir o dependente deixa de ser alcançável, em vez de ser tratado.
 *
 * <h2>Determinismo</h2>
 *
 * Nenhum sorteio. Todo desempate é por texto de identificador ou por ordem de calendário, e todo mapa
 * é ordenado por inserção ou por chave — nunca por hash. Duas chamadas com o mesmo plano devolvem o
 * mesmo plano.
 */
public final class TimelineRepairer implements ChromosomeRepairer {

    private final Map<PlanningItem, List<TacticalStudyBlock>> canonical;

    /** Alocador preparado uma vez; cada reparo tira dele um cursor novo. Ver G17. */
    private final AvailabilityAllocator prepared;

    /** Posição canônica de cada item, para indexar os vetores abaixo. */
    private final Map<PlanningItem, Integer> positions;

    /** Por posição: as posições dos itens que dependem dela. */
    private final int[][] dependents;

    /** Por posição: quantos pré-requisitos ela tem. Copiado por reparo e decrementado. */
    private final int[] baseIndegree;

    /**
     * @param request        o pedido, para as janelas e o horizonte
     * @param graph          as arestas rígidas aplicáveis nesta condição de proveniência
     * @param topicIdsByItem item de planejamento para identificador de tópico, para ler o grafo
     * @param canonical      os blocos que todo indivíduo tem de ter, por item; a alocação fixa
     */
    public TimelineRepairer(PlanRequest request, HardPrerequisiteGraph graph,
            Map<PlanningItem, UUID> topicIdsByItem,
            Map<PlanningItem, List<TacticalStudyBlock>> canonical) {

        this.canonical = Collections.unmodifiableMap(new LinkedHashMap<>(canonical));
        this.prepared = AvailabilityAllocator.over(request);

        // O grafo chega por identificador de tópico; o reparo trabalha por item. A tradução é função
        // pura do pedido, então acontece aqui uma vez em vez de por descendente. Era um
        // TreeMap<UUID> sob HardPrerequisiteGraph.BY_TEXT reconstruído a cada reparo — e BY_TEXT
        // compara UUID::toString, que aloca uma String de 36 caracteres por comparação. Ver G17.
        Map<PlanningItem, Integer> byPosition = new LinkedHashMap<>();
        canonical.keySet().forEach(item -> byPosition.put(item, byPosition.size()));
        this.positions = Collections.unmodifiableMap(byPosition);

        Map<UUID, PlanningItem> itemsById = new LinkedHashMap<>();
        topicIdsByItem.forEach((item, id) -> itemsById.put(id, item));

        int size = byPosition.size();
        List<List<Integer>> dependentsOf = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            dependentsOf.add(new ArrayList<>());
        }
        this.baseIndegree = new int[size];

        byPosition.forEach((item, at) -> {
            for (UUID prerequisiteId : graph.prerequisitesOf(topicIdsByItem.get(item))) {
                PlanningItem prerequisite = itemsById.get(prerequisiteId);
                // Aresta para fora do conjunto canônico não conta, espelhando a guarda
                // `item != null` que a versão anterior aplicava a cada reparo.
                Integer from = prerequisite == null ? null : byPosition.get(prerequisite);
                if (from != null) {
                    baseIndegree[at]++;
                    dependentsOf.get(from).add(at);
                }
            }
        });

        this.dependents = new int[size][];
        for (int index = 0; index < size; index++) {
            this.dependents[index] =
                    dependentsOf.get(index).stream().mapToInt(Integer::intValue).toArray();
        }
    }

    @Override
    public TacticalStudyPlan repair(TacticalStudyPlan plan, EvolutionContext context) {
        Map<PlanningItem, List<TacticalStudyBlock>> observed =
                groupByItem(blocksInCalendarOrder(plan));
        List<PlanningItem> requested = requestedOrder(observed.keySet());
        return place(topologicalOrder(requested), normalised(observed));
    }

    /**
     * A ordem que o cromossomo pediu, completada com o que ele perdeu.
     *
     * <p>Os itens observados vêm primeiro, na ordem do calendário — esse é o gene. Os que o
     * cruzamento perdeu entram depois, na ordem canônica, porque a contagem canônica os traz de volta
     * e eles precisam de uma posição. Pôr os perdidos no fim é a escolha que menos mexe no gene.
     */
    private List<PlanningItem> requestedOrder(Set<PlanningItem> observed) {
        List<PlanningItem> order = new ArrayList<>(observed);
        canonical.keySet().stream().filter(item -> !observed.contains(item)).forEach(order::add);
        return order;
    }

    /**
     * A contagem canônica de blocos por item, com as metodologias que o cromossomo escolheu.
     *
     * <p>O bloco {@code i} de um item fica com a metodologia do {@code i}-ésimo bloco observado
     * daquele item, quando existe, e com a canônica quando não — o que preserva o gene de
     * metodologia sem deixar a contagem derivar.
     */
    private Map<PlanningItem, List<TacticalStudyBlock>> normalised(
            Map<PlanningItem, List<TacticalStudyBlock>> observed) {

        Map<PlanningItem, List<TacticalStudyBlock>> fixed = new LinkedHashMap<>();
        canonical.forEach((item, template) -> {
            List<TacticalStudyBlock> seen = observed.getOrDefault(item, List.of());
            List<TacticalStudyBlock> blocks = new ArrayList<>(template.size());
            for (int index = 0; index < template.size(); index++) {
                TacticalStudyBlock canon = template.get(index);
                blocks.add(index < seen.size()
                        ? new TacticalStudyBlock(item, seen.get(index).methodology(),
                                canon.durationMinutes())
                        : canon);
            }
            fixed.put(item, blocks);
        });
        return fixed;
    }

    /** Os blocos, na ordem em que o calendário os apresenta. */
    private static List<TacticalStudyBlock> blocksInCalendarOrder(TacticalStudyPlan plan) {
        List<TacticalStudyBlock> blocks = new ArrayList<>(plan.getSchedule().size());
        TacticalSlots.ordered(plan).forEach(slot -> blocks.add(plan.getSchedule().get(slot)));
        return blocks;
    }

    /**
     * Blocos por item, na ordem de primeira aparição.
     *
     * <p>{@code LinkedHashMap}: a ordem das chaves <b>é</b> a ordem que o cromossomo pediu, e é ela
     * que entra como preferência no Kahn. Um {@code HashMap} aqui apagaria o gene.
     */
    private static Map<PlanningItem, List<TacticalStudyBlock>> groupByItem(
            List<TacticalStudyBlock> blocks) {

        Map<PlanningItem, List<TacticalStudyBlock>> byItem = new LinkedHashMap<>();
        blocks.forEach(block ->
                byItem.computeIfAbsent(block.item(), key -> new ArrayList<>()).add(block));
        return byItem;
    }

    /**
     * Kahn sobre as arestas rígidas, com a ordem pedida pelo cromossomo como preferência.
     *
     * <p>Todos os itens estão presentes depois de {@link #normalised}, então a ordenação é total e o
     * resíduo fica vazio para qualquer grafo acíclico. Ver a nota no resíduo.
     */
    private List<PlanningItem> topologicalOrder(List<PlanningItem> preference) {
        int[] pending = baseIndegree.clone();
        boolean[] placed = new boolean[pending.length];
        List<PlanningItem> order = new ArrayList<>(preference.size());

        // A ESTRUTURA DAS PASSADAS É PRESERVADA AO PÉ DA LETRA, e não por conservadorismo: ela
        // decide a saída. Um Kahn de manual, que sempre toma o pronto de menor índice de
        // preferência, dá ordem DIFERENTE — com preferência [B, A, C] e A pré-requisito de B, a
        // varredura dá [A, C, B] e o Kahn dá [A, B, C], porque a varredura já passou de B nesta
        // passada e o Kahn o toma na hora. Trocar uma pela outra mudaria o plano de toda execução da
        // v2 e invalidaria a medição. O que ficou mais rápido é só o TESTE por candidato: um
        // contador de pré-requisitos pendentes em vez de percorrer o grafo por identificador.
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (PlanningItem candidate : preference) {
                int at = positions.get(candidate);
                if (placed[at] || pending[at] > 0) {
                    continue;
                }
                order.add(candidate);
                placed[at] = true;
                progressed = true;
                for (int dependent : dependents[at]) {
                    pending[dependent]--;
                }
            }
        }

        // Resíduo só é alcançável com ciclo nas arestas rígidas, que o gerador de instâncias não
        // produz e que SinapseStudyOrder recusaria antes daqui com 422. Mantido na ordem pedida em
        // vez de silenciosamente descartado: um item que desaparecesse do plano sem aparecer em
        // topics-unscheduled-ids seria pior que um plano recusado.
        preference.stream().filter(item -> !placed[positions.get(item)]).forEach(order::add);
        return order;
    }

    /** Reempacota para frente, item por item, parando no primeiro bloco que não couber. */
    private TacticalStudyPlan place(List<PlanningItem> order,
            Map<PlanningItem, List<TacticalStudyBlock>> byItem) {

        AvailabilityAllocator allocator = prepared.rewound();
        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();

        for (PlanningItem item : order) {
            for (TacticalStudyBlock block : byItem.get(item)) {
                Optional<Instant> start = allocator.place((int) block.durationMinutes());
                if (start.isEmpty()) {
                    return new TacticalStudyPlan(schedule);
                }
                Instant end = start.get().plusSeconds(block.durationMinutes() * 60L);
                schedule.put(new TimeSlot(AvailabilityWindows.toLocal(start.get()),
                        AvailabilityWindows.toLocal(end)), block);
            }
        }
        return new TacticalStudyPlan(schedule);
    }
}
