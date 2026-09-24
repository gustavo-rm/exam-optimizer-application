package com.ia.project.dynamicstudyplanner.plan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The one place a cycle in the {@code HARD} prerequisites becomes a refusal.
 *
 * <h2>Why this is shared rather than repeated</h2>
 *
 * A cycle is detected at two different moments of a plan: when importance is derived from the
 * curriculum's structure, which happens before the search, and when the study order is built, which
 * happens after it. Both must refuse, and <b>both must refuse the same way</b> — same category, same
 * message, same offenders — or a caller would see the outcome change depending on which
 * configuration happened to reach the cycle first.
 *
 * <p>EOA-2 fixed that behaviour for the greedy baseline: a declared failure naming the topics in the
 * cycle, never a loop and never a silently broken edge. This is that behaviour, extracted so the two
 * call sites cannot drift.
 */
public final class PrerequisiteCycles {

    /** The refusal category, shared by every engine and every strategy. */
    public static final String REASON = "prerequisite-cycle";

    private PrerequisiteCycles() {
    }

    /**
     * Refuses when the {@code HARD} edges among these topics contain a cycle.
     *
     * @param graph    the applied {@code HARD} constraints
     * @param topicIds the topics in scope
     * @throws PlanRejectedException with {@code 422}, naming the topics in the cycle
     */
    public static void requireAcyclic(HardPrerequisiteGraph graph, Collection<UUID> topicIds) {
        TreeSet<UUID> residue = residueAfterTopologicalSort(graph, topicIds);
        if (!residue.isEmpty()) {
            throw rejection(graph, residue);
        }
    }

    /**
     * The topics a topological sort cannot place: exactly those on or behind a cycle.
     *
     * <p>Kahn's algorithm over sorted collections, so the result does not depend on the order the
     * edges arrived in. Only the <b>set</b> matters here, and a set is order-invariant anyway; the
     * sorting is for the message built from it.
     */
    private static TreeSet<UUID> residueAfterTopologicalSort(HardPrerequisiteGraph graph,
            Collection<UUID> topicIds) {

        TreeMap<UUID, Integer> blocking = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        topicIds.forEach(id -> blocking.put(id, graph.prerequisitesOf(id).size()));

        TreeSet<UUID> ready = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        blocking.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });

        TreeSet<UUID> placed = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        while (!ready.isEmpty()) {
            UUID next = ready.pollFirst();
            placed.add(next);
            for (UUID dependent : graph.dependentsOf(next)) {
                Integer remaining = blocking.get(dependent);
                if (remaining != null && blocking.put(dependent, remaining - 1) == 1) {
                    ready.add(dependent);
                }
            }
        }

        TreeSet<UUID> residue = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        blocking.keySet().stream().filter(id -> !placed.contains(id)).forEach(residue::add);
        return residue;
    }

    /**
     * The refusal, naming the topics on the cycle.
     *
     * @param graph   the applied constraints, to walk the cycle
     * @param residue the topics a topological sort could not place; must not be empty
     * @return the exception to throw
     */
    public static PlanRejectedException rejection(HardPrerequisiteGraph graph,
            java.util.Set<UUID> residue) {

        List<UUID> path = graph.cycleWithin(residue);
        List<String> named = new ArrayList<>();
        path.forEach(id -> named.add(id.toString()));
        return new PlanRejectedException(REASON,
                "The hard prerequisites form a cycle, so no order satisfies them and no topic in it "
                        + "can be ranked by what depends on it. The topics in the cycle are named; "
                        + "one of the edges between them has to go.",
                List.copyOf(named));
    }
}
