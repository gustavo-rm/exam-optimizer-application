package com.ia.project.dynamicstudyplanner.benchmark.metric;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Group (a): validity. A violation does not make a run <b>worse</b>, it makes it <b>void</b>.
 *
 * <h2>Why these are checked here and not trusted to production</h2>
 *
 * {@code PlanOutputInvariants} already refuses a plan that breaks most of these, and would have
 * thrown before the harness ever saw it. That is precisely why the harness checks them again: an
 * instrument that relies on the system under test to police itself measures nothing when the system
 * is wrong in the same way twice. These checks read only the request and the answer, and share no
 * code with the scheduler.
 *
 * <p>A violated invariant is a <b>defect report</b>, not a data point. The harness aborts rather
 * than recording the row, because a row from an invalid run would sit in the CSV looking like
 * evidence.
 */
public final class Invariants {

    private Invariants() {
    }

    /**
     * Every way this answer could be invalid.
     *
     * @param request  the instance
     * @param response the answer
     * @param filter   the provenance condition, which decides which edges were constraints at all
     * @return the violations, empty when the run is valid
     */
    public static List<String> violations(PlanRequest request, PlanResponse response,
            EdgeProvenanceFilter filter) {

        List<String> violations = new ArrayList<>();
        violations.addAll(hardInversions(request, response, filter));
        violations.addAll(insideAvailability(request, response));
        violations.addAll(noOverlap(response));
        violations.addAll(contiguousSequence(response));
        return violations;
    }

    /**
     * Zero {@code HARD} inversions — the property EOA-7 exists to guarantee.
     *
     * <p>The strong reading: no session of a dependent may begin before <b>every</b> session of its
     * hard prerequisites has ended.
     */
    public static List<String> hardInversions(PlanRequest request, PlanResponse response,
            EdgeProvenanceFilter filter) {

        HardPrerequisiteGraph graph =
                HardPrerequisiteGraph.of(request.topics(), request.prerequisites(), filter);
        Map<UUID, Instant> firstStart = new LinkedHashMap<>();
        Map<UUID, Instant> lastEnd = new LinkedHashMap<>();
        for (PlanResponse.ScheduledSession session : response.sessions()) {
            Instant end = session.scheduledStart().plusSeconds(session.durationMinutes() * 60L);
            firstStart.merge(session.topicId(), session.scheduledStart(),
                    (a, b) -> b.isBefore(a) ? b : a);
            lastEnd.merge(session.topicId(), end, (a, b) -> b.isAfter(a) ? b : a);
        }

        List<String> violations = new ArrayList<>();
        for (UUID dependent : firstStart.keySet()) {
            for (UUID prerequisite : graph.prerequisitesOf(dependent)) {
                Instant ends = lastEnd.get(prerequisite);
                if (ends == null) {
                    violations.add("HARD: " + dependent + " scheduled, prerequisite "
                            + prerequisite + " absent from the plan");
                } else if (ends.isAfter(firstStart.get(dependent))) {
                    violations.add("HARD: " + dependent + " starts " + firstStart.get(dependent)
                            + ", before prerequisite " + prerequisite + " ends " + ends);
                }
            }
        }
        return violations;
    }

    /** Every session inside one declared availability window. */
    private static List<String> insideAvailability(PlanRequest request, PlanResponse response) {
        List<String> violations = new ArrayList<>();
        for (PlanResponse.ScheduledSession session : response.sessions()) {
            Instant start = session.scheduledStart();
            Instant end = start.plusSeconds(session.durationMinutes() * 60L);
            boolean fits = request.availability().stream()
                    .anyMatch(slot -> !start.isBefore(slot.start()) && !end.isAfter(slot.end()));
            if (!fits) {
                violations.add("WINDOW: session " + session.sequenceIndex() + " (" + start
                        + " to " + end + ") is in no availability window");
            }
        }
        return violations;
    }

    /** No two sessions overlap in time. */
    private static List<String> noOverlap(PlanResponse response) {
        List<PlanResponse.ScheduledSession> ordered = response.sessions().stream()
                .sorted(Comparator.comparing(PlanResponse.ScheduledSession::scheduledStart))
                .toList();

        List<String> violations = new ArrayList<>();
        for (int index = 1; index < ordered.size(); index++) {
            PlanResponse.ScheduledSession previous = ordered.get(index - 1);
            Instant previousEnd = previous.scheduledStart()
                    .plusSeconds(previous.durationMinutes() * 60L);
            if (ordered.get(index).scheduledStart().isBefore(previousEnd)) {
                violations.add("OVERLAP: session " + ordered.get(index).sequenceIndex()
                        + " starts before session " + previous.sequenceIndex() + " ends");
            }
        }
        return violations;
    }

    /** {@code sequenceIndex} runs 0, 1, 2, ... with no gap and no repeat. */
    private static List<String> contiguousSequence(PlanResponse response) {
        List<Integer> indices = response.sessions().stream()
                .map(PlanResponse.ScheduledSession::sequenceIndex)
                .sorted()
                .toList();

        List<String> violations = new ArrayList<>();
        for (int position = 0; position < indices.size(); position++) {
            if (indices.get(position) != position) {
                violations.add("SEQUENCE: expected " + position + " at position " + position
                        + ", found " + indices.get(position));
                break;
            }
        }
        return violations;
    }

    /**
     * Everything about an answer that a second run must reproduce exactly.
     *
     * <p>Compared as text rather than field by field so that a field added to the contract joins the
     * comparison automatically instead of being silently exempt from it.
     *
     * @param response the answer
     * @return a signature covering the schedule and the reported fitness
     */
    public static String signature(PlanResponse response) {
        StringBuilder signature = new StringBuilder();
        response.sessions().forEach(session -> signature
                .append(session.sequenceIndex()).append(':')
                .append(session.topicId()).append('@')
                .append(session.scheduledStart()).append('/')
                .append(session.durationMinutes()).append('/')
                .append(session.kind()).append('|'));
        new java.util.TreeMap<>(response.fitness())
                .forEach((key, value) -> signature.append(key).append('=').append(value).append(';'));
        return signature.toString();
    }
}
