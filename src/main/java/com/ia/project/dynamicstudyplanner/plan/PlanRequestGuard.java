package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Refuses, with {@code 422}, the requests this scheduler cannot plan at all.
 *
 * <h2>What is checked here, and what deliberately is not</h2>
 *
 * Only conditions that would otherwise force the scheduler to emit a plan violating one of its own
 * output invariants, or to emit no plan at all. Each one is named in the error body.
 *
 * <p>Three things are <b>not</b> checked, and their absence is the decision, not an oversight:
 *
 * <ul>
 *   <li><b>{@code effortTier}.</b> This scheduler never reads it — it allocates by
 *       {@code estimatedMinutes}, which the platform already derives from the band and the student's
 *       effort factor ({@code docs/CORE_CONTRACT_SURVEY.md} §1.8). An unknown band therefore changes
 *       nothing about the plan, and refusing one would fail a request over a field the answer does not
 *       depend on. The contract carries it as a {@code String}, so a fifth value arrives here without
 *       a deserialisation error; it is carried past, unread. If a future version of this scheduler
 *       starts reading the band, that is the moment to reject a value outside
 *       {@code SHORT, STANDARD, LONG, EXTENDED} with {@code 422} naming it — not before.</li>
 *   <li><b>{@code goals[].priority} outside 1..5.</b> Documented but not validated by the contract
 *       record. Ordering by it is a comparison, so any integer sorts; a value out of band changes the
 *       order and breaks no invariant.</li>
 *   <li><b>Prerequisite edges pointing outside {@code topics}, or missing an endpoint or a
 *       strength.</b> Ignored by {@link HardPrerequisiteGraph} rather than refused, for the reason
 *       given there.</li>
 * </ul>
 */
public final class PlanRequestGuard {

    private PlanRequestGuard() {
    }

    /**
     * Runs every check, in a fixed order, and throws on the first one that fails.
     *
     * @param request the request as deserialised
     * @throws PlanRejectedException naming the offending items
     */
    public static void check(PlanRequest request) {
        checkHorizon(request.horizon());
        checkTopics(request.topics());
        checkAvailability(request.availability());
    }

    /** A horizon that is absent, half-declared or runs backwards denotes no stretch of calendar. */
    private static void checkHorizon(PlanRequest.Horizon horizon) {
        if (horizon == null || horizon.start() == null || horizon.end() == null) {
            throw new PlanRejectedException("unusable-horizon",
                    "The plan horizon must declare both a start date and an end date.",
                    List.of("horizon"));
        }
        if (horizon.end().isBefore(horizon.start())) {
            throw new PlanRejectedException("unusable-horizon",
                    "The plan horizon ends before it starts.",
                    List.of(horizon.start() + ".." + horizon.end()));
        }
    }

    /**
     * Topics must exist, be identifiable, be distinct and claim a positive number of minutes.
     *
     * <p>A duplicate identifier is refused rather than de-duplicated: the two entries could carry
     * different durations, and picking one silently would make the plan depend on list order for a
     * request the platform should never have sent.
     */
    private static void checkTopics(List<PlanRequest.Topic> topics) {
        if (topics.isEmpty()) {
            throw new PlanRejectedException("no-topics",
                    "A plan needs at least one topic; none were sent.", List.of("topics"));
        }
        Set<UUID> seen = new LinkedHashSet<>();
        for (int index = 0; index < topics.size(); index++) {
            PlanRequest.Topic topic = topics.get(index);
            if (topic.id() == null) {
                throw new PlanRejectedException("unidentified-topic",
                        "Every topic must carry an identifier; one arrived without one.",
                        List.of("topics[" + index + "]"));
            }
            if (!seen.add(topic.id())) {
                throw new PlanRejectedException("duplicate-topic",
                        "The same topic was sent more than once.", List.of(topic.id().toString()));
            }
        }
        checkDurations(topics);
    }

    /**
     * {@code durationMinutes > 0} is one of the eight invariants the platform enforces, and this
     * scheduler's durations come straight from {@code estimatedMinutes}. A topic claiming zero or
     * fewer minutes is a platform-side defect; dropping it quietly would hide that.
     */
    private static void checkDurations(List<PlanRequest.Topic> topics) {
        List<String> offending = topics.stream()
                .filter(topic -> topic.estimatedMinutes() <= 0)
                .map(topic -> topic.id().toString())
                .toList();
        if (!offending.isEmpty()) {
            throw new PlanRejectedException("unusable-topic-duration",
                    "Every topic must estimate a positive number of minutes.", offending);
        }
    }

    /**
     * There has to be somewhere to put a session, and every window has to be a window.
     *
     * <p>An empty or inverted interval is refused rather than dropped. The platform already drops
     * those when it resolves the student's zone ({@code docs/CORE_CONTRACT_SURVEY.md} §4, item 7), so
     * one arriving here means the two sides disagree about what was sent — worth an answer that says
     * so, rather than a plan quietly built from fewer windows than the student declared.
     */
    private static void checkAvailability(List<PlanRequest.AvailabilitySlot> availability) {
        if (availability.isEmpty()) {
            throw new PlanRejectedException("no-availability",
                    "A plan needs at least one availability window; none were sent.",
                    List.of("availability"));
        }
        for (int index = 0; index < availability.size(); index++) {
            PlanRequest.AvailabilitySlot slot = availability.get(index);
            boolean unusable = slot.start() == null || slot.end() == null
                    || !slot.start().isBefore(slot.end());
            if (unusable) {
                throw new PlanRejectedException("unusable-availability-slot",
                        "Every availability window must open before it closes.",
                        List.of("availability[" + index + "]"));
            }
        }
    }
}
