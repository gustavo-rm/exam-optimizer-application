package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeProvenance;
import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Requests whose prerequisite graphs are <b>generated</b> rather than hand-written.
 *
 * <h2>Why generated shapes and not more fixtures</h2>
 *
 * A hand-written fixture tests the case its author thought of. The properties under test here — a
 * dependent is never scheduled before its hard prerequisites, and the plan is reproducible — are
 * meant to hold for every shape a curriculum can take, and the shapes that break topological
 * reasoning are known: a <b>long chain</b>, because it leaves the sort no freedom and any
 * off-by-one in the ready set shows; a <b>diamond</b>, because one topic is reached by two paths
 * and a naive in-degree count releases it too early; and a <b>layered graph</b>, because it has
 * many valid orders and therefore many ways to pick a wrong one.
 *
 * <p>Generation is seeded, so a failure names a shape that can be rebuilt exactly.
 */
public final class PrerequisiteGraphs {

    /** A generated instance: the request, and the hard edges it was built from. */
    public record Shape(String name, PlanRequest request, List<PlanRequest.PrerequisiteEdge> hard) {

        @Override
        public String toString() {
            return name;
        }
    }

    /** Minutes per topic. Uniform, so placement order is the only thing under test. */
    private static final int TOPIC_MINUTES = 30;

    private static final LocalDate START = PlanRequests.HORIZON_START;

    private PrerequisiteGraphs() {
    }

    /** {@code t0 -> t1 -> ... -> t(n-1)}: one order satisfies it, and only one. */
    public static Shape chain(int topics) {
        List<UUID> ids = ids(topics);
        List<PlanRequest.PrerequisiteEdge> edges = new ArrayList<>();
        for (int index = 1; index < topics; index++) {
            edges.add(hard(ids.get(index - 1), ids.get(index)));
        }
        return shape("chain-" + topics, ids, edges);
    }

    /**
     * {@code a -> b, a -> c, b -> d, c -> d}, repeated {@code diamonds} times side by side.
     *
     * <p>The join is the point: {@code d} has two prerequisites reached by two different paths, so
     * a sort that releases a topic when <i>any</i> prerequisite is placed schedules it too early.
     */
    public static Shape diamonds(int diamonds) {
        List<UUID> ids = ids(diamonds * 4);
        List<PlanRequest.PrerequisiteEdge> edges = new ArrayList<>();
        for (int index = 0; index < diamonds; index++) {
            int base = index * 4;
            edges.add(hard(ids.get(base), ids.get(base + 1)));
            edges.add(hard(ids.get(base), ids.get(base + 2)));
            edges.add(hard(ids.get(base + 1), ids.get(base + 3)));
            edges.add(hard(ids.get(base + 2), ids.get(base + 3)));
        }
        return shape("diamonds-" + diamonds, ids, edges);
    }

    /**
     * A layered directed acyclic graph: every edge points from a lower layer to a higher one.
     *
     * <p>Acyclic by construction rather than by rejection sampling, so the generator cannot
     * accidentally produce the one input this property does not apply to.
     *
     * @param layers how many layers
     * @param width  topics per layer
     * @param seed   fixes the edges, so a failing case is reproducible from its name
     */
    public static Shape layered(int layers, int width, long seed) {
        List<UUID> ids = ids(layers * width);
        Random random = new Random(seed);
        List<PlanRequest.PrerequisiteEdge> edges = new ArrayList<>();
        for (int layer = 1; layer < layers; layer++) {
            for (int position = 0; position < width; position++) {
                UUID dependent = ids.get(layer * width + position);
                int parents = 1 + random.nextInt(width);
                for (int parent = 0; parent < parents; parent++) {
                    edges.add(hard(ids.get((layer - 1) * width + random.nextInt(width)), dependent));
                }
            }
        }
        return shape("layered-" + layers + "x" + width + "-seed" + seed, ids, edges);
    }

    /** The same topics and hard edges, with soft edges pointing the opposite way. */
    public static Shape withInvertedSoftEdges(Shape shape) {
        List<PlanRequest.PrerequisiteEdge> edges = new ArrayList<>(shape.hard());
        for (PlanRequest.PrerequisiteEdge edge : shape.hard()) {
            // Deliberately backwards: a preference that the hard constraint forbids honouring is
            // what the repair must leave alone and the fitness term must price.
            edges.add(new PlanRequest.PrerequisiteEdge(edge.dependentTopicId(),
                    edge.prerequisiteTopicId(), EdgeStrength.SOFT, EdgeProvenance.TEXTBOOK_ORDER));
        }
        return new Shape(shape.name() + "+inverted-soft",
                rebuild(shape.request(), edges), shape.hard());
    }

    /**
     * The same request with one short window, so the plan is necessarily partial.
     *
     * @param request the generated request
     * @return the request with a single ninety-minute window on the horizon's first day
     */
    public static PlanRequest withOneDayOfAvailability(PlanRequest request) {
        Instant from = START.atTime(LocalTime.of(8, 0)).toInstant(ZoneOffset.UTC);
        return new PlanRequest(request.contractVersion(), request.horizon(),
                List.of(new PlanRequest.AvailabilitySlot(from, from.plusSeconds(90 * 60L))),
                request.goals(), request.topics(), request.prerequisites(), request.history(),
                request.algorithmParams(), request.randomSeed());
    }

    /** Identifiers derived from the index, so a failure names a topic that can be found. */
    private static List<UUID> ids(int topics) {
        List<UUID> ids = new ArrayList<>(topics);
        for (int index = 0; index < topics; index++) {
            ids.add(new UUID(0xc000000000004000L, 0x8000000000000000L | index));
        }
        return ids;
    }

    private static PlanRequest.PrerequisiteEdge hard(UUID prerequisite, UUID dependent) {
        return new PlanRequest.PrerequisiteEdge(prerequisite, dependent, EdgeStrength.HARD,
                EdgeProvenance.CURATED);
    }

    private static Shape shape(String name, List<UUID> ids,
            List<PlanRequest.PrerequisiteEdge> edges) {

        List<PlanRequest.Topic> topics = new ArrayList<>(ids.size());
        for (int index = 0; index < ids.size(); index++) {
            topics.add(new PlanRequest.Topic(ids.get(index), PlanRequests.SUBJECT_FIRST,
                    index + 1, "STANDARD", TOPIC_MINUTES));
        }

        // Enough calendar to hold every topic twice over: this file is about order, and a plan
        // truncated for lack of room would test the truncation instead.
        int days = Math.max(14, ids.size());
        PlanRequest request = new PlanRequest(PlanRequest.VERSION,
                new PlanRequest.Horizon(START, START.plusDays(days - 1L)),
                availability(days),
                List.of(new PlanRequest.Goal(PlanRequests.SUBJECT_FIRST, null, 3)),
                topics, edges, List.of(), Map.of(), PlanRequests.SEED);
        return new Shape(name, request, List.copyOf(edges));
    }

    private static PlanRequest rebuild(PlanRequest request,
            List<PlanRequest.PrerequisiteEdge> edges) {

        return new PlanRequest(request.contractVersion(), request.horizon(), request.availability(),
                request.goals(), request.topics(), List.copyOf(edges), request.history(),
                request.algorithmParams(), request.randomSeed());
    }

    private static List<PlanRequest.AvailabilitySlot> availability(int days) {
        List<PlanRequest.AvailabilitySlot> slots = new ArrayList<>(days);
        for (int day = 0; day < days; day++) {
            Instant from = START.plusDays(day).atTime(LocalTime.of(8, 0)).toInstant(ZoneOffset.UTC);
            slots.add(new PlanRequest.AvailabilitySlot(from, from.plusSeconds(4 * 3600L)));
        }
        return slots;
    }
}
