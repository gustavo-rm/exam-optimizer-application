package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The order the genetic engine's topics are laid onto the calendar.
 *
 * <h2>Topological first, the algorithm's preference second</h2>
 *
 * Hard prerequisites are a constraint and the plan is refused if they cannot be satisfied, so the
 * order is a topological one. Within what the constraints leave free, topics are ordered by
 * <b>how many sessions the genetic algorithm gave them</b>, most first.
 *
 * <p>That is the one place the algorithm's decision reaches the calendar. The chromosome allocates
 * effort across topics; it has no calendar and cannot express "this one first"
 * ({@code docs/revisao-ag/01-auditoria-fitness.md} §3.3). Reading its allocation as a priority is
 * the faithful use of what it does decide: a topic the search chose to invest in is a topic that
 * should survive a truncated plan.
 *
 * <p>The baseline orders by goal priority, then target date, then curricular position. This one does
 * not reuse that, deliberately: the two are the conditions of an experiment, and an engine that
 * borrowed the other's ordering would be measuring a blend of the two.
 *
 * <h2>Ties break on the identifier's text, never on a hash</h2>
 *
 * Two topics with equal allocation are ordered by {@code topicId} as text. Every map and set here is
 * sorted for the same reason: the plan has to be identical on two threads and two JVMs, and
 * {@code UUID.hashCode()} ordering is neither promised nor stable.
 *
 * <h2>Truncation, not skipping</h2>
 *
 * Allocation walks this order and stops at the first topic that does not fit, so the scheduled set is
 * a <b>prefix</b> of a topological order and therefore closed under prerequisites. Skipping ahead to
 * whatever still fits would fill the calendar more fully and could hand a student a topic whose
 * prerequisite is absent from the plan. The baseline reasons the same way, and
 * {@code PlanOutputInvariants} refuses either engine's plan if the property breaks.
 */
public final class SinapseStudyOrder {

    private SinapseStudyOrder() {
    }

    /**
     * Orders the topics for placement.
     *
     * @param topics the topics as they arrived
     * @param graph  the hard prerequisites that apply
     * @param plan   the chromosome, for the sessions each topic was given
     * @param items  topic id per planning item, to read the chromosome by topic
     * @return every topic, in placement order
     * @throws PlanRejectedException with {@code 422} when the hard prerequisites contain a cycle
     */
    public static List<PlanRequest.Topic> of(List<PlanRequest.Topic> topics,
            HardPrerequisiteGraph graph, StudyPlan plan, Map<PlanningItem, UUID> items) {

        Map<UUID, PlanRequest.Topic> byId = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        topics.forEach(topic -> byId.put(topic.id(), topic));
        Map<UUID, Integer> sessions = sessionsByTopic(plan, items);

        Comparator<UUID> preference = Comparator
                .comparingInt((UUID id) -> -sessions.getOrDefault(id, 0))
                .thenComparing(HardPrerequisiteGraph.BY_TEXT);

        List<UUID> ordered = kahn(byId.keySet(), graph, preference);
        if (ordered.size() != byId.size()) {
            throw cycle(graph, byId.keySet(), ordered);
        }
        return ordered.stream().map(byId::get).toList();
    }

    /** Sessions the chromosome allocated, per topic. */
    private static Map<UUID, Integer> sessionsByTopic(StudyPlan plan, Map<PlanningItem, UUID> items) {
        Map<UUID, Integer> sessions = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        items.forEach((item, topicId) -> sessions.put(topicId, plan.getDaysForItem(item)));
        return sessions;
    }

    /**
     * Kahn's algorithm with a preference order over the ready set.
     *
     * <p>The ready set is a {@code TreeSet} under the preference comparator, so "which of the topics
     * that are currently unblocked comes next" is decided by allocation and then by identifier — not
     * by insertion order, which would make the result depend on how the edges happened to be listed.
     */
    private static List<UUID> kahn(Iterable<UUID> all, HardPrerequisiteGraph graph,
            Comparator<UUID> preference) {

        Map<UUID, Integer> blocking = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        for (UUID id : all) {
            blocking.put(id, graph.prerequisitesOf(id).size());
        }

        TreeSet<UUID> ready = new TreeSet<>(preference);
        blocking.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });

        List<UUID> ordered = new ArrayList<>(blocking.size());
        while (!ready.isEmpty()) {
            UUID next = ready.pollFirst();
            ordered.add(next);
            for (UUID dependent : graph.dependentsOf(next)) {
                Integer remaining = blocking.get(dependent);
                if (remaining != null && blocking.put(dependent, remaining - 1) == 1) {
                    ready.add(dependent);
                }
            }
        }
        return ordered;
    }

    /** Names the cycle that stopped the sort, so the payload can be fixed. */
    private static PlanRejectedException cycle(HardPrerequisiteGraph graph,
            Iterable<UUID> all, List<UUID> ordered) {

        Map<UUID, Boolean> placed = new LinkedHashMap<>();
        ordered.forEach(id -> placed.put(id, Boolean.TRUE));
        TreeSet<UUID> residue = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        for (UUID id : all) {
            if (!placed.containsKey(id)) {
                residue.add(id);
            }
        }
        List<UUID> path = graph.cycleWithin(residue);
        Deque<String> named = new ArrayDeque<>();
        path.forEach(id -> named.add(id.toString()));
        return new PlanRejectedException("prerequisite-cycle",
                "The hard prerequisites form a cycle, so no order satisfies them. The topics in the "
                        + "cycle are named; one of the edges between them has to go.",
                List.copyOf(named));
    }
}
