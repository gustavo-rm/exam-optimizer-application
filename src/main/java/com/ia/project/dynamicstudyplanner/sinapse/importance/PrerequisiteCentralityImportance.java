package com.ia.project.dynamicstudyplanner.sinapse.importance;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteCycles;
import com.ia.project.dynamicstudyplanner.sinapse.TopicPlanningItems;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Importance from the curriculum's shape: how much of the syllabus is stranded without this topic.
 *
 * <h2>The rule</h2>
 *
 * A topic's raw importance is {@code 1 + |{u : this topic is a transitive HARD prerequisite of u}|}
 * — the number of topics that cannot be reached until it is learned, plus one. A foundation that
 * eleven later topics build on outranks a leaf that nothing depends on.
 *
 * <h2>Why this is the hypothesis and not the default</h2>
 *
 * It is <b>objective</b>: the student declares nothing, which is what the platform's decision L1
 * (ADR 0012) set out to preserve and what {@link GoalPriorityImportance} unavoidably gives up. And
 * it turns the prerequisite graph, which today only <em>constrains</em> the plan, into a
 * <em>signal</em> about it — the edges are already carried and already trusted enough to refuse a
 * plan over, so ranking with them costs no new data.
 *
 * <p>What it is not is validated. Structural centrality is a plausible proxy for "worth studying
 * first" and nothing here measures whether it plans better than asking the student. That is the
 * point of having both: a hypothesis with nothing to test against is not measurable.
 *
 * <h2>The count is restricted to topics in scope, and that restriction is the honest part</h2>
 *
 * The platform sends {@code graph.edgesTouchingSubjects(subjectIds)}, so the edge set has a specific
 * shape ({@code docs/CORE_CONTRACT_SURVEY.md} §4, item 2):
 *
 * <ul>
 *   <li>every edge <b>between two planned topics</b> is sent — the in-scope subgraph is complete;</li>
 *   <li>edges with <b>one endpoint outside</b> the planned subjects are sent too;</li>
 *   <li>edges <b>wholly outside</b> are not sent at all.</li>
 * </ul>
 *
 * So counting the out-of-scope endpoints would reach exactly one hop past the boundary and then
 * stop — <b>not a smaller closure but a biased one</b>, inflating whichever topics happen to sit on
 * the edge of the planned subjects. {@link HardPrerequisiteGraph} already drops those edges, and
 * this strategy is built on it for that reason rather than by inheritance.
 *
 * <p>What is computed is therefore centrality <b>within the planning scope</b>: complete over the
 * topics being planned, silent about a curriculum it cannot see. A topic may well have dependents
 * beyond the request; ranking it as if it did not is a smaller error than ranking it by a count
 * that is complete for some topics and truncated for others.
 *
 * <h2>Determinism</h2>
 *
 * Reachability is a <b>set cardinality</b>, so it cannot depend on the order the edges arrived in —
 * and the traversal runs over {@link HardPrerequisiteGraph}'s sorted adjacency anyway.
 * {@code ImportanceDeterminismTest} shuffles the same graph into several orders and asserts the
 * values are identical.
 *
 * <h2>A cycle is a refusal, not a loop</h2>
 *
 * "How many topics depend on this one" has no answer inside a cycle: every topic on it transitively
 * depends on every other, itself included. The traversal would terminate — a visited set makes
 * reachability safe on a cyclic graph — and would return a number that means nothing. So the cycle
 * is refused before it is counted, through {@link PrerequisiteCycles}, with the same category and
 * the same message the study order uses. That is the behaviour EOA-2 fixed for the greedy baseline.
 */
@Component
public class PrerequisiteCentralityImportance implements ImportanceStrategy {

    /** The id this strategy answers to in {@code algorithmParams.importance}. */
    public static final String ID = "prerequisite-centrality";

    /**
     * Added to every count so that a leaf is ranked low rather than ranked out.
     *
     * <p>A topic nothing depends on has a transitive-dependent count of zero, and zero importance
     * would leave it <b>scheduled and invisible</b>: it still receives its sessions, because the day
     * floor guarantees them, but no term of the fitness would register whether it was learned. Worse,
     * a syllabus with no {@code HARD} edges at all would give every topic zero and collapse the term
     * to the uniform fallback without anything saying so.
     *
     * <p>The same reasoning, and the same value, as {@code GoalPriorityImportance}'s floor for a
     * topic whose subject appears in no goal.
     */
    public static final double LEAF_FLOOR = 1.0;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Map<PlanningItem, Double> importanceOf(PlanRequest request) {
        HardPrerequisiteGraph graph =
                HardPrerequisiteGraph.of(request.topics(), request.prerequisites());
        TreeSet<UUID> inScope = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        request.topics().forEach(topic -> inScope.add(topic.id()));

        // Before counting, not after: inside a cycle the count is meaningless rather than expensive.
        PrerequisiteCycles.requireAcyclic(graph, inScope);

        Map<PlanningItem, Double> importance = new LinkedHashMap<>();
        for (PlanRequest.Topic topic : request.topics()) {
            importance.put(TopicPlanningItems.toItem(topic),
                    LEAF_FLOOR + transitiveDependentCount(graph, topic.id(), inScope));
        }
        // unmodifiableMap over LinkedHashMap, never Map.copyOf: the iteration order reaches the
        // floating-point sum in EvolutionContext.normalize. See GoalPriorityImportance.
        return Collections.unmodifiableMap(importance);
    }

    /**
     * How many in-scope topics transitively depend on this one.
     *
     * <p>Breadth-first over the dependent direction, with a visited set. The set is what makes the
     * answer independent of traversal order and safe on any shape of graph; the cycle check above is
     * about the answer being <em>meaningful</em>, not about termination.
     *
     * @param graph   the applied {@code HARD} constraints
     * @param topicId the topic being ranked
     * @param inScope the topics that may be counted
     * @return the count, excluding the topic itself
     */
    private static int transitiveDependentCount(HardPrerequisiteGraph graph, UUID topicId,
            TreeSet<UUID> inScope) {

        TreeSet<UUID> reached = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        Deque<UUID> frontier = new ArrayDeque<>();
        frontier.add(topicId);

        while (!frontier.isEmpty()) {
            for (UUID dependent : graph.dependentsOf(frontier.poll())) {
                if (inScope.contains(dependent) && reached.add(dependent)) {
                    frontier.add(dependent);
                }
            }
        }
        // The topic itself is never counted: on an acyclic graph it cannot reach itself, so the
        // only way it would appear is a cycle, which was already refused.
        return reached.size();
    }
}
