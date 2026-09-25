package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeProvenance;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Which edges of the prerequisite graph a run is allowed to see, by where they came from.
 *
 * <h2>Three conditions, and why they are a filter rather than a flag</h2>
 *
 * {@code EdgeProvenance} crosses the wire for one reason, stated in its own Javadoc and decided by
 * ADR 0006: so that the ablation is possible <b>without adding instrumentation afterwards</b>. The
 * question it exists to answer is whether the scheduler does better with curated edges only, with
 * curated plus curricular ordering, or with everything including automatically derived edges.
 *
 * <p>Answering that needs the three conditions to differ in exactly one thing — which edges are
 * visible — and in nothing else. So this is applied at the single point where edges become
 * constraints ({@link HardPrerequisiteGraph}) and where they become priced preferences
 * ({@link SoftPrerequisiteEdges}), and it applies to <b>every</b> consumer of the graph, including
 * {@code prerequisite-centrality} importance. A condition in which the scheduler ignores a derived
 * edge while the importance term still counts it would not be one condition, it would be two.
 *
 * <h2>The set is cumulative on purpose</h2>
 *
 * {@link #CURATED} ⊂ {@link #CURATED_TEXTBOOK} ⊂ {@link #ALL}, so the three runs form a ladder and a
 * difference between two of them is attributable to the edges the wider one added. Three arbitrary
 * subsets would each differ from the others in two directions and no single comparison would
 * isolate a cause.
 *
 * <h2>An edge with no provenance is admitted by every condition</h2>
 *
 * The contract does not mark {@code provenance} optional and the platform always sends it, but an
 * edge that arrives without one cannot be attributed to any condition. Dropping it would silently
 * narrow the graph in a way the condition's name does not describe, so it is kept everywhere: the
 * filter's job is to exclude edges it can attribute, not to exclude edges it cannot read.
 */
public enum EdgeProvenanceFilter {

    /** Human-asserted edges only — the narrowest graph, and the only fully trusted one. */
    CURATED("curated", EnumSet.of(EdgeProvenance.CURATED)),

    /** Curated edges plus the curricular ordering of each subject. */
    CURATED_TEXTBOOK("curated-textbook",
            EnumSet.of(EdgeProvenance.CURATED, EdgeProvenance.TEXTBOOK_ORDER)),

    /** Every edge the platform sent, automatic derivation included. */
    ALL("all", EnumSet.allOf(EdgeProvenance.class));

    /** The key a request sets in {@code algorithmParams} to choose a condition. */
    public static final String PARAM = "provenance";

    private final String id;
    private final Set<EdgeProvenance> admitted;

    EdgeProvenanceFilter(String id, Set<EdgeProvenance> admitted) {
        this.id = id;
        this.admitted = admitted;
    }

    /** @return the id as it is written in {@code algorithmParams} and echoed in {@code fitness} */
    public String id() {
        return id;
    }

    /**
     * Whether this condition can see the edge.
     *
     * @param edge one edge as the platform sent it
     * @return {@code true} when the edge's provenance is in this condition, or is absent
     */
    public boolean admits(PlanRequest.PrerequisiteEdge edge) {
        return edge.provenance() == null || admitted.contains(edge.provenance());
    }

    /**
     * The condition answering to an id.
     *
     * @param id the id, as written in configuration or in {@code algorithmParams}
     * @return the condition, or {@code null} when no condition answers to that id
     */
    public static EdgeProvenanceFilter byId(String id) {
        return Arrays.stream(values())
                .filter(filter -> filter.id.equals(id))
                .findFirst()
                .orElse(null);
    }

    /** @return every id, sorted, for an error message a caller can act on */
    public static List<String> ids() {
        return Arrays.stream(values())
                .map(EdgeProvenanceFilter::id)
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
