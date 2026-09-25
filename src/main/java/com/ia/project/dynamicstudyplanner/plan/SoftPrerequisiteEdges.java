package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The {@code SOFT} prerequisite edges that apply to a request, as preferences rather than rules.
 *
 * <h2>Why these are a separate type from {@link HardPrerequisiteGraph}</h2>
 *
 * The contract is explicit that {@code SOFT} is a preference and {@code HARD} is a rule
 * ({@code EdgeStrength}), and the two are consumed in incompatible ways. A hard edge decides
 * <b>which orders exist</b>: the topological sort will not emit a dependent before its
 * prerequisite, and a cycle among hard edges makes the request unplannable. A soft edge decides
 * <b>what an order costs</b>: every order remains available, and an inversion is priced.
 *
 * <p>Sharing one type would have meant a graph whose consumers must remember which half of it is
 * binding — the kind of distinction that survives exactly as long as the person who wrote it. A
 * cycle among soft edges in particular is not an error and must not reach
 * {@code PrerequisiteCycles}: it only means no order satisfies every preference, which is the
 * ordinary case that the penalty exists to measure.
 *
 * <h2>The same admission rules as the hard graph, for the same reasons</h2>
 *
 * An edge applies when it is {@code SOFT}, carries both endpoints, both endpoints are topics of this
 * request, and the run's {@link EdgeProvenanceFilter} admits it. An edge pointing outside the
 * request's topics is normal traffic and is dropped, because no ordering of the topics that were
 * sent can satisfy it — pricing it would charge every plan a constant nobody can avoid.
 *
 * <p>Duplicates collapse: the same pair sent twice is one preference, so it is counted once and
 * cannot weight itself up by being repeated.
 */
public final class SoftPrerequisiteEdges {

    /** One preference: {@code prerequisite} would ideally be studied before {@code dependent}. */
    public record Edge(UUID prerequisite, UUID dependent) {
    }

    private final List<Edge> edges;

    private SoftPrerequisiteEdges(List<Edge> edges) {
        this.edges = edges;
    }

    /**
     * Reduces the request's edges to the soft preferences that apply.
     *
     * @param topics the topics in scope
     * @param edges  every edge the platform sent, of either strength
     * @param filter the run's provenance condition
     * @return the applicable preferences, ordered by prerequisite then dependent identifier text
     */
    public static SoftPrerequisiteEdges of(List<PlanRequest.Topic> topics,
            List<PlanRequest.PrerequisiteEdge> edges, EdgeProvenanceFilter filter) {

        Set<UUID> known = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        topics.forEach(topic -> known.add(topic.id()));

        // TreeMap of TreeSet, never a hash: this list is walked to build the severity sum of a
        // fitness term, and floating-point addition is not associative, so its order is arithmetic.
        Map<UUID, Set<UUID>> dependentsOf = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        for (PlanRequest.PrerequisiteEdge edge : edges) {
            if (applies(edge, known, filter)) {
                dependentsOf
                        .computeIfAbsent(edge.prerequisiteTopicId(),
                                key -> new TreeSet<>(HardPrerequisiteGraph.BY_TEXT))
                        .add(edge.dependentTopicId());
            }
        }

        List<Edge> applicable = new ArrayList<>();
        dependentsOf.forEach((prerequisite, dependents) ->
                dependents.forEach(dependent -> applicable.add(new Edge(prerequisite, dependent))));
        return new SoftPrerequisiteEdges(List.copyOf(applicable));
    }

    private static boolean applies(PlanRequest.PrerequisiteEdge edge, Set<UUID> known,
            EdgeProvenanceFilter filter) {

        return edge.strength() == EdgeStrength.SOFT
                && edge.prerequisiteTopicId() != null
                && edge.dependentTopicId() != null
                && known.contains(edge.prerequisiteTopicId())
                && known.contains(edge.dependentTopicId())
                && filter.admits(edge);
    }

    /** @return the applicable preferences, in a fixed order */
    public List<Edge> all() {
        return edges;
    }

    /** @return how many preferences apply; the denominator of the violation term */
    public int size() {
        return edges.size();
    }

    /** @return whether no preference applies, in which case no plan can violate one */
    public boolean isEmpty() {
        return edges.isEmpty();
    }

    /**
     * The soft prerequisites of each topic that has one.
     *
     * @return dependent topic to the topics it would ideally follow, both maps explicitly ordered
     */
    public Map<UUID, Set<UUID>> prerequisitesByDependent() {
        Map<UUID, Set<UUID>> byDependent = new TreeMap<>(HardPrerequisiteGraph.BY_TEXT);
        for (Edge edge : edges) {
            byDependent
                    .computeIfAbsent(edge.dependent(),
                            key -> new TreeSet<>(HardPrerequisiteGraph.BY_TEXT))
                    .add(edge.prerequisite());
        }
        return Collections.unmodifiableMap(byDependent);
    }

    /** @return the preferences sorted for a stable report, never used for arithmetic */
    public List<String> asText() {
        return edges.stream()
                .map(edge -> edge.prerequisite() + " -> " + edge.dependent())
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
