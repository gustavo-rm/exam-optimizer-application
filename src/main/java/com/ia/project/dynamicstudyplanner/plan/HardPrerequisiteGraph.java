package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The {@code HARD} prerequisite edges, in both directions, as a constraint graph.
 *
 * <h2>Which edges become constraints</h2>
 *
 * An edge is applied only when it is {@code HARD}, carries both endpoints, and both endpoints are
 * topics of this request. The other edges are ignored, each for its own reason:
 *
 * <ul>
 *   <li><b>{@code SOFT}</b> is a preference, not a constraint — the contract says so explicitly
 *       ({@code EdgeStrength}). It is not ignored: {@link SoftPrerequisiteEdges} collects the same
 *       edges and the genetic path prices an inversion of one. It is simply never allowed to decide
 *       which orders exist.</li>
 *   <li><b>An edge the run's {@link EdgeProvenanceFilter} does not admit.</b> The three provenance
 *       conditions are an ablation, and an edge outside the condition has to be invisible to every
 *       consumer of the graph, not only to the scheduler.</li>
 *   <li><b>An endpoint outside {@code topics}</b> cannot be scheduled by this plan, so the edge
 *       cannot be satisfied by any ordering of the topics that were sent. The platform sends these
 *       deliberately ({@code docs/CORE_CONTRACT_SURVEY.md} §4, item 2), so they are normal traffic
 *       and not an error. Ignoring the edge schedules the dependent topic; honouring it would mean
 *       dropping the topic for a prerequisite nobody asked to be planned.</li>
 *   <li><b>A missing endpoint or a missing strength</b> cannot be read as a constraint at all. The
 *       platform never sends one, and refusing the whole plan over an edge that carries no
 *       information would be a worse answer than leaving it out.</li>
 * </ul>
 *
 * <p>Duplicate edges collapse: both directions are sets, so the same pair sent twice constrains once
 * and the in-degree stays right.
 *
 * <h2>Every collection here is explicitly ordered</h2>
 *
 * {@code TreeMap} and {@code TreeSet} over {@link #BY_TEXT}, never {@code HashMap} or
 * {@code HashSet}. This graph decides which topic {@link StudyOrder} considers next and which topics
 * a cycle rejection names, so its iteration order is observable in the answer.
 */
public final class HardPrerequisiteGraph {

    /**
     * Topic identifiers compared as text, lexicographically.
     *
     * <p>Not {@code UUID.compareTo}: that compares the two halves as <b>signed</b> longs, so
     * {@code ffffffff-…} sorts before {@code 00000000-…} — an order no reader of the identifier would
     * predict. The tie-break this module promises is the lexicographic one, so it compares the text.
     */
    public static final Comparator<UUID> BY_TEXT = Comparator.comparing(UUID::toString);

    /** Prerequisite topic to the topics that depend on it. */
    private final Map<UUID, Set<UUID>> dependents;

    /** Dependent topic to the topics it requires first. */
    private final Map<UUID, Set<UUID>> prerequisites;

    private HardPrerequisiteGraph(Map<UUID, Set<UUID>> dependents,
            Map<UUID, Set<UUID>> prerequisites) {
        this.dependents = dependents;
        this.prerequisites = prerequisites;
    }

    /**
     * Builds the graph from the topics and edges of one request.
     *
     * @param topics the topics in scope, each with a non-null identifier
     * @param edges  every edge the platform sent, of either strength
     * @param filter the run's provenance condition; an edge it does not admit is not a constraint
     * @return the applied constraints, in both directions
     */
    public static HardPrerequisiteGraph of(List<PlanRequest.Topic> topics,
            List<PlanRequest.PrerequisiteEdge> edges, EdgeProvenanceFilter filter) {

        Set<UUID> known = new TreeSet<>(BY_TEXT);
        topics.forEach(topic -> known.add(topic.id()));

        Map<UUID, Set<UUID>> dependents = new TreeMap<>(BY_TEXT);
        Map<UUID, Set<UUID>> prerequisites = new TreeMap<>(BY_TEXT);
        for (PlanRequest.PrerequisiteEdge edge : edges) {
            if (applies(edge, known, filter)) {
                link(dependents, edge.prerequisiteTopicId(), edge.dependentTopicId());
                link(prerequisites, edge.dependentTopicId(), edge.prerequisiteTopicId());
            }
        }
        return new HardPrerequisiteGraph(dependents, prerequisites);
    }

    private static boolean applies(PlanRequest.PrerequisiteEdge edge, Set<UUID> known,
            EdgeProvenanceFilter filter) {

        return edge.strength() == EdgeStrength.HARD
                && edge.prerequisiteTopicId() != null
                && edge.dependentTopicId() != null
                && known.contains(edge.prerequisiteTopicId())
                && known.contains(edge.dependentTopicId())
                && filter.admits(edge);
    }

    private static void link(Map<UUID, Set<UUID>> side, UUID from, UUID to) {
        side.computeIfAbsent(from, key -> new TreeSet<>(BY_TEXT)).add(to);
    }

    /** The topics that require {@code topicId}, in lexicographic order. */
    public Set<UUID> dependentsOf(UUID topicId) {
        return dependents.getOrDefault(topicId, Set.of());
    }

    /** The topics {@code topicId} requires first, in lexicographic order. */
    public Set<UUID> prerequisitesOf(UUID topicId) {
        return prerequisites.getOrDefault(topicId, Set.of());
    }

    /** How many distinct constraints were applied. Reported in {@code fitness}. */
    public int appliedEdgeCount() {
        return prerequisites.values().stream().mapToInt(Set::size).sum();
    }

    /** Every topic that requires at least one other, in lexicographic order. */
    public Set<UUID> constrainedTopics() {
        return Collections.unmodifiableSet(prerequisites.keySet());
    }

    /**
     * Extracts one concrete cycle from the topics a topological sort could not place.
     *
     * <p>Walks backwards along prerequisites, always taking the lexicographically first one that is
     * itself unplaced, until a topic repeats. Every unplaced topic has at least one unplaced
     * prerequisite — that is what left it unplaced — so the walk cannot end anywhere but on a repeat,
     * and it terminates because the residue is finite.
     *
     * <p>The result is reported prerequisite-first, which is the direction the edges point, so the
     * error body reads as the chain that closes on itself rather than backwards.
     *
     * @param residue the topics the sort could not place; never empty when this is called
     * @return one cycle, as topic identifiers, prerequisite first
     */
    public List<UUID> cycleWithin(Set<UUID> residue) {
        UUID current = residue.stream().min(BY_TEXT).orElseThrow();
        List<UUID> walk = new ArrayList<>();
        Set<UUID> visited = new LinkedHashSet<>();
        while (visited.add(current)) {
            walk.add(current);
            current = prerequisitesOf(current).stream()
                    .filter(residue::contains)
                    .min(BY_TEXT)
                    .orElseThrow();
        }
        List<UUID> cycle = new ArrayList<>(walk.subList(walk.indexOf(current), walk.size()));
        Collections.reverse(cycle);
        return List.copyOf(cycle);
    }
}
