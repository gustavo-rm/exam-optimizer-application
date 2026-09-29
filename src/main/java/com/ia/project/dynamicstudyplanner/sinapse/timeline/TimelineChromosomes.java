package com.ia.project.dynamicstudyplanner.sinapse.timeline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.sinapse.SessionPlacement;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta os cromossomos táticos: o multiconjunto de blocos, e a população inicial que os permuta.
 *
 * <h2>A alocação é FIXA e a ordem é o gene — a decisão que define esta v2</h2>
 *
 * A v1 evolui <b>quantas</b> sessões cada item recebe, e deriva a ordem daquela alocação. Esta v2
 * evolui a <b>ordem</b>, e recebe a alocação pronta. As duas juntas seriam um terceiro sistema, e
 * compará-lo com a v1 mediria duas mudanças ao mesmo tempo.
 *
 * <p>Então a contagem de sessões por item é <b>determinística</b>: o piso de
 * {@code SinapseMinimumDays}, e o excedente do orçamento distribuído por importância normalizada com
 * o método do maior resto. Nenhum sorteio entra nisso.
 *
 * <p><b>Isto é limitação declarada da v2, e ela corta nas duas direções.</b> A v2 não busca a
 * alocação, então numa instância onde o ganho vem de alocar melhor ela não tem como competir com a
 * v1 — e o relatório precisa poder concluir isso. Em troca, uma diferença medida entre as duas é
 * atribuível à ordem, que é a única coisa que as separa.
 *
 * <h2>O orçamento é o mesmo dos dois motores</h2>
 *
 * Vem de {@code SessionBudget}, extraído de {@code GeneticPlanEngine} exatamente para que não
 * houvesse duas fórmulas. Com orçamentos diferentes, a comparação mediria o orçamento.
 *
 * <h2>Os blocos seguem a convenção de {@code SessionPlacement}</h2>
 *
 * O primeiro bloco de um item é terreno novo, com {@code estimatedMinutes} e
 * {@code ACTIVE_RECALL}; os seguintes são revisão, com {@code SessionPlacement.revisionMinutes} e
 * {@code SPACED_REPETITION_REVIEW}. A convenção é a da v1, de propósito: é o eixo que
 * {@code MethodologyMutation} depois explora, e partir de outro ponto faria as duas condições
 * começarem de planos diferentes por um motivo que não é a ordem.
 */
public final class TimelineChromosomes {

    /** Âncora dos slots provisórios: o reparador reatribui todo instante antes de o plano contar. */
    private static final LocalDateTime PROVISIONAL = LocalDateTime.parse("2000-01-01T00:00");

    private TimelineChromosomes() {
    }

    /**
     * Sessões por item: o piso, mais o excedente por importância normalizada.
     *
     * <p>Maior resto e não arredondamento por item: a soma tem de fechar com o orçamento exatamente,
     * e arredondar cada um por conta própria deixaria sobra ou falta. Empates desfeitos por nome do
     * item, para que a distribuição seja reproduzível.
     *
     * @param context o contexto, para os pisos e a importância
     * @param budget  o orçamento total de sessões
     * @return item {@literal ->} sessões, em ordem canônica do contexto
     */
    public static Map<PlanningItem, Integer> sessionsPerItem(EvolutionContext context, int budget) {
        List<PlanningItem> items = List.copyOf(context.importanceScores().keySet());
        Map<PlanningItem, Integer> sessions = new LinkedHashMap<>();
        int allocated = 0;
        for (PlanningItem item : items) {
            int floor = context.minimumDaysPerItem().getOrDefault(item, 1);
            sessions.put(item, floor);
            allocated += floor;
        }

        int remaining = Math.max(0, budget - allocated);
        if (remaining == 0) {
            return sessions;
        }

        Map<PlanningItem, Double> weights = context.normalizedImportance();
        double total = items.stream()
                .mapToDouble(item -> Math.max(0.0, weights.getOrDefault(item, 0.0)))
                .sum();

        record Share(PlanningItem item, int whole, double remainder) {
        }
        List<Share> shares = new ArrayList<>(items.size());
        int handedOut = 0;
        for (PlanningItem item : items) {
            double exact = total <= 0.0
                    ? remaining / (double) items.size()
                    : remaining * Math.max(0.0, weights.getOrDefault(item, 0.0)) / total;
            int whole = (int) Math.floor(exact);
            shares.add(new Share(item, whole, exact - whole));
            sessions.merge(item, whole, Integer::sum);
            handedOut += whole;
        }

        List<Share> byRemainder = shares.stream()
                .sorted(Comparator.comparingDouble(Share::remainder).reversed()
                        .thenComparing(share -> share.item().name()))
                .toList();
        for (int index = 0; handedOut < remaining; index++, handedOut++) {
            sessions.merge(byRemainder.get(index % byRemainder.size()).item(), 1, Integer::sum);
        }
        return sessions;
    }

    /**
     * Os blocos de um item, na convenção de {@code SessionPlacement}.
     *
     * @param item     o item
     * @param topic    o tópico, para {@code estimatedMinutes}
     * @param sessions quantas sessões ele recebeu; sempre pelo menos uma
     * @return o primeiro bloco de estudo seguido das revisões
     */
    public static List<TacticalStudyBlock> blocksOf(PlanningItem item, PlanRequest.Topic topic,
            int sessions) {

        List<TacticalStudyBlock> blocks = new ArrayList<>(Math.max(1, sessions));
        blocks.add(new TacticalStudyBlock(item, StudyMethodology.ACTIVE_RECALL,
                topic.estimatedMinutes()));

        int revisionMinutes = SessionPlacement.revisionMinutes(topic.estimatedMinutes());
        for (int index = 1; index < sessions; index++) {
            blocks.add(new TacticalStudyBlock(item, StudyMethodology.SPACED_REPETITION_REVIEW,
                    revisionMinutes));
        }
        return blocks;
    }

    /**
     * Um cromossomo com os itens na ordem dada.
     *
     * <p>Os slots saem de um cursor provisório e contíguo. O reparador reatribui todos os instantes a
     * partir da disponibilidade real antes de o plano ser pontuado, então o que este mapa carrega de
     * informação é só a <b>ordem</b> — que é exatamente o gene.
     *
     * @param order     os itens na ordem desejada
     * @param blocksBy  blocos por item
     * @return o cromossomo, ainda sem reparo
     */
    public static TacticalStudyPlan inOrder(List<PlanningItem> order,
            Map<PlanningItem, List<TacticalStudyBlock>> blocksBy) {

        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();
        LocalDateTime cursor = PROVISIONAL;
        for (PlanningItem item : order) {
            for (TacticalStudyBlock block : blocksBy.getOrDefault(item, List.of())) {
                LocalDateTime end = cursor.plusMinutes(block.durationMinutes());
                schedule.put(new TimeSlot(cursor, end), block);
                cursor = end;
            }
        }
        return new TacticalStudyPlan(schedule);
    }

    /**
     * Uma permutação sorteada da ordem dos itens.
     *
     * <p>Fisher-Yates sobre {@code RandomProvider}, que é a única fonte de aleatoriedade do AG. A
     * lista de entrada não é alterada.
     *
     * @param items os itens em ordem canônica
     * @return uma nova lista, permutada
     */
    public static List<PlanningItem> shuffled(List<PlanningItem> items) {
        List<PlanningItem> order = new ArrayList<>(items);
        for (int index = order.size() - 1; index > 0; index--) {
            int pick = RandomProvider.getInstance().nextInt(index + 1);
            PlanningItem held = order.get(index);
            order.set(index, order.get(pick));
            order.set(pick, held);
        }
        return order;
    }
}
