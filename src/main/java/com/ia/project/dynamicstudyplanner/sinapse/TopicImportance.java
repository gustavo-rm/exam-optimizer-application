package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * How much each topic matters, from the goals the student declared.
 *
 * <h2>The single point of calculation</h2>
 *
 * Importance is {@code goals[].priority}, propagated to every topic of the goal's subject. That is
 * the whole rule, and it lives in this one method so that <b>EOA-6 can replace it without touching
 * anything else</b>: the adapter, the context builder and the fitness all read the map this class
 * returns and none of them knows how it was produced.
 *
 * <p>The propagation is what the contract prescribes: "every topic of the subject is in scope with
 * it; there is no exclusion list". A goal names a {@code subjectId} and a priority from 1 to 5, and
 * every topic carrying that {@code subjectId} inherits it. Raw values are fine — the core normalises
 * importance onto the simplex before any term reads it, so the scale the priorities arrive on does
 * not reach the fitness.
 *
 * <h2>Two project assumptions, both declared and both tested</h2>
 *
 * <ol>
 *   <li><b>A topic whose subject has no goal is planned at priority 1</b>, the lowest the contract
 *       documents, rather than at zero or refused. Zero would leave the topic with no importance at
 *       all: it would still be scheduled, because every in-scope topic gets its study session, but
 *       no term of the fitness would register whether it was learned — an invisible topic. Refusing
 *       would reject a payload the contract permits, since nothing in it says every topic's subject
 *       must appear in {@code goals}. Priority 1 is a floor on a field that exists, not a stand-in
 *       for a field that does not.</li>
 *   <li><b>Two goals naming the same subject collapse to the higher priority.</b> The contract sends
 *       no goal identifier, so two entries for one subject are indistinguishable; taking the maximum
 *       is the reading that never lowers a priority the student expressed.</li>
 * </ol>
 *
 * Both are recorded in {@code docs/SINAPSE_ADAPTER.md} §3 and pinned by
 * {@code SinapseAssumptionsTest}.
 */
public final class TopicImportance {

    /**
     * The priority a topic gets when its subject appears in no goal.
     *
     * <p>See assumption 1 in the class comment. Deliberately the contract's own documented minimum,
     * so that the value is traceable to the contract rather than chosen here.
     */
    public static final double PRIORITY_WITHOUT_GOAL = 1.0;

    private TopicImportance() {
    }

    /**
     * Raw importance per planning item.
     *
     * @param topics the topics as they arrived
     * @param goals  the goals as they arrived
     * @return item {@literal ->} priority, in the topics' arrival order
     */
    public static Map<PlanningItem, Double> of(List<PlanRequest.Topic> topics,
            List<PlanRequest.Goal> goals) {

        Map<UUID, Integer> bySubject = prioritiesBySubject(goals);
        Map<PlanningItem, Double> importance = new LinkedHashMap<>();
        for (PlanRequest.Topic topic : topics) {
            Integer priority = bySubject.get(topic.subjectId());
            importance.put(TopicPlanningItems.toItem(topic),
                    priority == null ? PRIORITY_WITHOUT_GOAL : (double) priority);
        }
        // Collections.unmodifiableMap sobre LinkedHashMap, e NAO Map.copyOf: a ordem de iteracao de
        // um mapa de Map.copyOf e embaralhada por uma semente aleatoria POR EXECUCAO DA JVM
        // (documentado como "unspecified and subject to change"), e esta ordem entra numa conta —
        // EvolutionContext.normalize soma raw.values() para dividir as importancias pelo total, e
        // soma de ponto flutuante nao e associativa. Com Map.copyOf o total mudava entre execucoes
        // da JVM, as importancias normalizadas mudavam no ultimo bit, e num otimo degenerado o
        // desempate virava — o mesmo pedido com a mesma semente dava planos diferentes em execucoes
        // diferentes. Foi pego por PlanEngineDeterminismTest, intermitente, 1 em 3.
        return Collections.unmodifiableMap(importance);
    }

    /**
     * Highest priority per subject.
     *
     * <p>{@code TreeMap} over the identifier's text, so the fold is deterministic whatever order the
     * goals arrive in — a {@code HashMap} here would make the collapse of duplicate subjects depend
     * on {@code UUID.hashCode()}.
     */
    private static Map<UUID, Integer> prioritiesBySubject(List<PlanRequest.Goal> goals) {
        Map<UUID, Integer> bySubject =
                new TreeMap<>(com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph.BY_TEXT);
        for (PlanRequest.Goal goal : goals) {
            if (goal.subjectId() != null) {
                bySubject.merge(goal.subjectId(), goal.priority(), Math::max);
            }
        }
        return bySubject;
    }
}
