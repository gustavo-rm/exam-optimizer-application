package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.plan.PlanRequestGuard;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The order the topics are studied in: a topological sort of the {@code HARD} edges, made total.
 *
 * <h2>Kahn's algorithm with an ordered ready set</h2>
 *
 * A topic becomes <i>ready</i> when every topic it hard-requires has already been placed. The ready
 * set is a priority queue, so the topological constraint is absolute — the queue never contains a
 * topic whose prerequisites are outstanding — and the comparator only ever chooses <b>among topics
 * that are all legal next</b>. No preference can therefore push a dependent topic ahead of its
 * prerequisite; preferences only decide which of several legal topics goes next.
 *
 * <h2>How the two preference criteria combine</h2>
 *
 * <b>Lexicographically, priority first, and deliberately not as a weighted score.</b> Among the ready
 * topics:
 *
 * <ol>
 *   <li><b>goal priority, higher first.</b> The highest priority among the ready topics wins
 *       outright;</li>
 *   <li><b>target date, nearer first,</b> and only as a tie-break within one priority level. A topic
 *       whose subject declared no target date sorts after every topic that did;</li>
 *   <li><b>curricular position, lower first</b> — the first tie-break of the topological sort;</li>
 *   <li><b>topic identifier as text, lexicographically</b> — the final tie-break.</li>
 * </ol>
 *
 * <p>Lexicographic and not weighted because a weighted score would need two coefficients, and there
 * is no measurement to set them from: any pair would be an invented trade-off between "more pressing"
 * and "sooner", presented as arithmetic. Reading them in order makes the trade-off explicit — a
 * priority-5 subject is always served before a priority-4 one, however close the priority-4 deadline
 * is. Weighing deadline against priority is precisely the judgement the genetic algorithm exists to
 * make, and a baseline that guessed at it would stop being a baseline.
 *
 * <p><b>The fourth criterion is not cosmetic.</b> Priority, date and position can all tie — two topics
 * of the same subject cannot, since position is unique within a subject, but two topics of two
 * subjects that declared the same priority and the same date can. Without the identifier the winner
 * would come from the queue's internal layout, which is a function of insertion order, and the plan
 * would stop being reproducible. Identifiers are unique per request, so with it the order is total.
 */
final class StudyOrder {

    private StudyOrder() {
    }

    /**
     * Places every topic in study order.
     *
     * @param request the request, already accepted by {@link PlanRequestGuard}
     * @param graph   the applied {@code HARD} constraints
     * @return every topic, in the order it is to be studied
     * @throws PlanRejectedException with {@code 422} when the {@code HARD} edges contain a cycle
     */
    static List<PlanRequest.Topic> of(PlanRequest request, HardPrerequisiteGraph graph) {
        GoalPressure pressure = GoalPressure.of(request.goals());
        Map<UUID, PlanRequest.Topic> byId = index(request.topics());
        Map<UUID, Integer> outstanding = outstandingPrerequisites(byId.keySet(), graph);

        Queue<PlanRequest.Topic> ready = new PriorityQueue<>(comparator(pressure));
        outstanding.forEach((id, count) -> {
            if (count == 0) {
                ready.add(byId.get(id));
            }
        });

        List<PlanRequest.Topic> order = new ArrayList<>(byId.size());
        while (!ready.isEmpty()) {
            PlanRequest.Topic placed = ready.poll();
            order.add(placed);
            release(placed.id(), graph, outstanding, byId, ready);
        }
        if (order.size() != byId.size()) {
            throw cycleRejection(graph, outstanding);
        }
        return List.copyOf(order);
    }

    /**
     * Discounts one placed prerequisite from everything that depends on it, enqueuing whatever that
     * frees.
     */
    private static void release(UUID placed, HardPrerequisiteGraph graph,
            Map<UUID, Integer> outstanding, Map<UUID, PlanRequest.Topic> byId,
            Queue<PlanRequest.Topic> ready) {

        for (UUID dependent : graph.dependentsOf(placed)) {
            if (outstanding.merge(dependent, -1, Integer::sum) == 0) {
                ready.add(byId.get(dependent));
            }
        }
    }

    /**
     * The four criteria, in order. See the class comment for why they are read in sequence rather
     * than weighed against one another.
     */
    private static Comparator<PlanRequest.Topic> comparator(GoalPressure pressure) {
        Comparator<PlanRequest.Topic> byPriority = Comparator
                .comparingInt((PlanRequest.Topic topic) -> pressure.priorityOf(topic.subjectId()))
                .reversed();
        return byPriority
                .thenComparing(topic -> pressure.deadlineOf(topic.subjectId()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(PlanRequest.Topic::position)
                .thenComparing(PlanRequest.Topic::id, HardPrerequisiteGraph.BY_TEXT);
    }

    private static Map<UUID, PlanRequest.Topic> index(List<PlanRequest.Topic> topics) {
        Map<UUID, PlanRequest.Topic> byId = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        topics.forEach(topic -> byId.put(topic.id(), topic));
        return byId;
    }

    private static Map<UUID, Integer> outstandingPrerequisites(Set<UUID> ids,
            HardPrerequisiteGraph graph) {

        Map<UUID, Integer> outstanding = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        ids.forEach(id -> outstanding.put(id, graph.prerequisitesOf(id).size()));
        return outstanding;
    }

    /**
     * Builds the {@code 422} for a cyclic graph, naming the topics of one concrete cycle.
     *
     * <h2>Detected, never repaired, and never looped on</h2>
     *
     * Kahn's algorithm cannot loop: it places each topic at most once and stops when nothing is ready.
     * A cycle shows up as topics left over, and those left-over topics are exactly the ones still
     * waiting on a prerequisite that will never be placed.
     *
     * <p>This scheduler does not break the cycle. It could — dropping the lexicographically last edge
     * would produce a plan — but the plan would silently contradict a curated curriculum edge, and the
     * student would study a topic before its prerequisite with nothing anywhere saying so. A cycle in
     * {@code HARD} edges is a defect in the curriculum graph, and the only party that can fix it is
     * the one that curated it. So the answer names the cycle and refuses.
     */
    private static PlanRejectedException cycleRejection(HardPrerequisiteGraph graph,
            Map<UUID, Integer> outstanding) {

        Set<UUID> residue = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        outstanding.forEach((id, count) -> {
            if (count > 0) {
                residue.add(id);
            }
        });
        List<String> named = graph.cycleWithin(residue).stream().map(UUID::toString).toList();
        return new PlanRejectedException("hard-prerequisite-cycle",
                "The HARD prerequisites contain a cycle, so no study order can satisfy them: "
                        + String.join(" -> ", named) + " -> " + named.get(0)
                        + ". This scheduler reports the cycle and refuses; it does not choose an edge "
                        + "to ignore, because that would contradict a curated prerequisite silently.",
                named);
    }
}
