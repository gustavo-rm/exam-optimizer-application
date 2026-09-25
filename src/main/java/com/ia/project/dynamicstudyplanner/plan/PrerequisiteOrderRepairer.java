package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Removes the soft-order inversions a study order can be rid of, and leaves the rest priced.
 *
 * <h2>Why this does not implement {@code ChromosomeRepairer}</h2>
 *
 * {@code ga.tactical.repair.ChromosomeRepairer} is the right <b>pattern</b> and this follows it: a
 * deterministic artifact-in, artifact-out pass that resolves violations rather than discarding the
 * individual, never throwing and never adding work the student's calendar cannot hold — the rule
 * {@code SpacedRepetitionRepairer} states as "swap, do not add".
 *
 * <p>Its <b>signature</b> is the wrong one here, and the reason is the encoding rather than taste.
 * {@code ChromosomeRepairer} repairs a {@code TacticalStudyPlan}: a map from fixed {@code TimeSlot}s
 * to blocks. Those slots are not independent of the order — {@link SoftPrerequisiteEdges} aside,
 * {@code SessionPlacement} walks the study order and consumes availability as it goes, so a slot's
 * length is {@code estimatedMinutes} <b>of the topic that happened to be placed there</b>. Repairing
 * an inversion after placement therefore means moving a topic into a slot cut for a different topic,
 * which is only legal when the two happen to share an estimate. That subset is arbitrary: it has
 * nothing to do with which inversions matter and everything to do with which topics coincidentally
 * take the same number of minutes.
 *
 * <p>Repairing the <b>order</b>, before placement, fixes the same inversions without that
 * restriction, because the slot geometry is then derived from the repaired order rather than fought
 * against. The cost is that this cannot see the calendar — it cannot know that a swap pushes a topic
 * past the horizon — which is why placement keeps the last word and truncates.
 *
 * <p>That limitation is not a gap to be closed inside v1; it is the finding v1 exists to record. A
 * stage that repairs order and a stage that allocates effort cannot see each other's constraints,
 * and the argument for a timeline chromosome is exactly that a single encoding would.
 *
 * <h2>The repair, and why it terminates</h2>
 *
 * One improving move at a time: take the first inverted preference in a fixed scan, try moving the
 * prerequisite to immediately before its dependent, and keep the result only if it is still a
 * topological order of the {@code HARD} graph <b>and</b> strictly reduces the total number of
 * inversions. Each accepted move lowers a non-negative integer bounded by the number of soft edges,
 * so the loop runs at most that many times and needs no iteration cap to be safe.
 *
 * <p>Moving the prerequisite earlier, rather than the dependent later, is deliberate: the order is
 * truncated from the end when the calendar fills, so making a topic later risks dropping it
 * altogether, while making one earlier can only make the prefix arrive sooner.
 *
 * <p>A residue always remains possible — two preferences can contradict each other, or one can
 * contradict a {@code HARD} edge. Those are exactly what
 * {@code SoftPrerequisiteOrderConstraint} prices, and the two halves are meant to be read together:
 * the repair removes what is removable, the term reports what was not.
 */
public final class PrerequisiteOrderRepairer {

    private PrerequisiteOrderRepairer() {
    }

    /**
     * The outcome of a repair pass.
     *
     * @param order            the topics, reordered
     * @param inversionsBefore soft preferences inverted by the incoming order
     * @param inversionsAfter  soft preferences still inverted; always {@code <= inversionsBefore}
     */
    public record Result(List<PlanRequest.Topic> order, int inversionsBefore,
            int inversionsAfter) {

        /** @return how many inversions the pass removed */
        public int repaired() {
            return inversionsBefore - inversionsAfter;
        }
    }

    /**
     * Repairs what can be repaired.
     *
     * @param order the study order, already topological over the hard graph
     * @param hard  the constraints the repaired order must still satisfy
     * @param soft  the preferences being repaired
     * @return the repaired order and the two counts, for reporting
     */
    public static Result repair(List<PlanRequest.Topic> order, HardPrerequisiteGraph hard,
            SoftPrerequisiteEdges soft) {

        List<UUID> current = new ArrayList<>(order.stream().map(PlanRequest.Topic::id).toList());
        int before = inversions(current, soft);
        int best = before;

        boolean improved = true;
        while (improved && best > 0) {
            improved = false;
            for (SoftPrerequisiteEdges.Edge edge : soft.all()) {
                List<UUID> candidate = moveEarlier(current, edge, hard);
                if (candidate == null) {
                    continue;
                }
                int after = inversions(candidate, soft);
                if (after < best) {
                    current = candidate;
                    best = after;
                    improved = true;
                    break;
                }
            }
        }
        return new Result(reorder(order, current), before, best);
    }

    /**
     * The order with {@code edge}'s prerequisite moved to immediately before its dependent.
     *
     * @return the candidate order, or {@code null} when the preference is already honoured or the
     *         move would put a topic before one of its hard prerequisites
     */
    private static List<UUID> moveEarlier(List<UUID> order, SoftPrerequisiteEdges.Edge edge,
            HardPrerequisiteGraph hard) {

        int prerequisiteAt = order.indexOf(edge.prerequisite());
        int dependentAt = order.indexOf(edge.dependent());
        if (prerequisiteAt < 0 || dependentAt < 0 || prerequisiteAt < dependentAt) {
            return null;
        }

        List<UUID> candidate = new ArrayList<>(order);
        candidate.remove(prerequisiteAt);
        candidate.add(dependentAt, edge.prerequisite());
        return topological(candidate, hard) ? candidate : null;
    }

    /** Whether every topic still follows all of its hard prerequisites. */
    private static boolean topological(List<UUID> order, HardPrerequisiteGraph hard) {
        Map<UUID, Integer> position = positions(order);
        for (Map.Entry<UUID, Integer> entry : position.entrySet()) {
            Set<UUID> required = hard.prerequisitesOf(entry.getKey());
            for (UUID prerequisite : required) {
                Integer at = position.get(prerequisite);
                if (at != null && at > entry.getValue()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * How many soft preferences an order inverts, without changing it.
     *
     * <p>For the arm of the comparison that does not repair: the greedy baseline states no opinion
     * about preferences and must keep stating none, so its plans are <b>measured</b> here and left
     * alone. Reporting the figure is what lets the two arms be compared on the axis this stage is
     * about.
     *
     * @param order the study order as that engine produced it
     * @param soft  the applicable preferences
     * @return how many of them the order inverts
     */
    public static int inversionsIn(List<PlanRequest.Topic> order, SoftPrerequisiteEdges soft) {
        return inversions(order.stream().map(PlanRequest.Topic::id).toList(), soft);
    }

    /** How many soft preferences the order inverts. */
    private static int inversions(List<UUID> order, SoftPrerequisiteEdges soft) {
        Map<UUID, Integer> position = positions(order);
        int inverted = 0;
        for (SoftPrerequisiteEdges.Edge edge : soft.all()) {
            Integer prerequisiteAt = position.get(edge.prerequisite());
            Integer dependentAt = position.get(edge.dependent());
            if (prerequisiteAt != null && dependentAt != null && prerequisiteAt > dependentAt) {
                inverted++;
            }
        }
        return inverted;
    }

    /** Position of each topic. Insertion-ordered, never a hash, so a debug print is stable. */
    private static Map<UUID, Integer> positions(List<UUID> order) {
        Map<UUID, Integer> position = new LinkedHashMap<>();
        for (int index = 0; index < order.size(); index++) {
            position.put(order.get(index), index);
        }
        return position;
    }

    /** Puts the original topic objects back in the repaired sequence. */
    private static List<PlanRequest.Topic> reorder(List<PlanRequest.Topic> original,
            List<UUID> ids) {

        Map<UUID, PlanRequest.Topic> byId = new LinkedHashMap<>();
        original.forEach(topic -> byId.put(topic.id(), topic));
        return ids.stream().map(byId::get).toList();
    }
}
