package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.plan.PlanInvariantViolationException;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;
import com.ia.project.dynamicstudyplanner.plan.PlanRequestGuard;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlacedSession;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Turns a {@code PlanRequest} into a {@code PlanResponse} by one deterministic greedy pass.
 *
 * <h2>This is the experimental baseline, not a mock</h2>
 *
 * A deterministic greedy scheduler that honours hard prerequisites is the simple comparison a claim
 * about the genetic algorithm has to beat. It is a condition of the experiment, expected to stay in
 * the codebase once the genetic algorithm is wired to this contract, and it is the only thing
 * answering {@code POST /plans} today.
 *
 * <h2>The pass, in order</h2>
 *
 * <ol>
 *   <li>{@link PlanRequestGuard} refuses what cannot be planned, with {@code 422};</li>
 *   <li>{@link HardPrerequisiteGraph} reduces the edges to the constraints that apply;</li>
 *   <li>{@link StudyOrder} sorts the topics topologically, breaking ties by goal priority, then
 *       target date, then curricular position, then topic identifier as text;</li>
 *   <li>{@link RevisionPolicy} decides which topics also get a {@code REVISION} block;</li>
 *   <li>{@link AvailabilityAllocator} lays the blocks into the availability windows, forward only;</li>
 *   <li>{@code sequenceIndex} is counted over the finished list, so it is chronological, unique and
 *       contiguous from zero;</li>
 *   <li>{@link PlanOutputInvariants} asserts the twelve properties of the answer before it leaves.</li>
 * </ol>
 *
 * <h2>Not enough availability: a declared partial plan, never a silent one</h2>
 *
 * When the windows cannot hold every topic, the plan is a <b>prefix of the study order</b>: allocation
 * stops at the first topic that does not fit and every topic after it is left out. It does not skip
 * ahead to whatever still fits, and that is the point — a prefix of a topological order is closed
 * under prerequisites, so the student never receives a topic whose hard prerequisite is missing from
 * the plan. Skipping would produce a fuller calendar and an invalid one.
 *
 * <p>A topic is placed whole or not at all: if its revision block does not fit, its study block is
 * given back and the topic counts as unscheduled. {@code fitness} then declares what happened —
 * {@code topics-scheduled}, {@code topics-unscheduled} and {@code partial} — so a partial plan is
 * visible in the answer rather than inferred from a short list. If <b>nothing</b> fits, the request is
 * refused with {@code 422} instead: an empty schedule is one of the eight things the platform rejects
 * outright ({@code docs/CORE_CONTRACT_SURVEY.md} §3, check 3), so answering with one would only move
 * the failure to the other side of the wire.
 *
 * <h2>{@code effortTier} is never read</h2>
 *
 * Allocation is by {@code estimatedMinutes}, which the platform has already derived from the band and
 * scaled by the student's own effort factor ({@code docs/CORE_CONTRACT_SURVEY.md} §1.8). The band
 * itself is carried past unread, so a value outside {@code SHORT, STANDARD, LONG, EXTENDED} — which
 * the contract transports as a {@code String} and therefore delivers here without a deserialisation
 * error — changes nothing about the plan and is not rejected. Validating it would refuse a request
 * over a field the answer does not depend on. {@link PlanRequestGuard} states the same thing, and
 * {@code GreedyBaselineSchedulerTest} pins it.
 *
 * <h2>{@code generations} and {@code elapsedMillis} are zero, and that is honest</h2>
 *
 * This scheduler runs no generations, so {@code generations} is {@code 0}. {@code elapsedMillis} is
 * {@code 0} because measuring it would make two runs of the same request differ, and determinism is a
 * requirement here; the platform reads both fields off the wire and drops them, so nothing downstream
 * loses information.
 *
 * <h2>Thread safety</h2>
 *
 * Stateless apart from the injected version string. Every collaborator that holds state — the
 * allocator's cursor, the policy's history index — is built inside {@link #schedule} and belongs to
 * that one call, so any number of threads may share one instance.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class GreedyBaselineScheduler {

    /** No evolutionary run happened. Reported as such rather than left plausible. */
    private static final int GENERATIONS = 0;

    /** See the class comment: a measured duration would break byte-for-byte reproducibility. */
    private static final long ELAPSED_MILLIS = 0L;

    /**
     * The build version, reported as {@code metadata.coreVersion}.
     *
     * <p>Comes from {@code application-baseline-core.properties}, where Maven's resource filtering
     * substitutes {@code project.version}. There is no default: a deployment that cannot say which
     * build answered fails to start, rather than answering with a version string somebody typed.
     */
    private final String coreVersion;

    public GreedyBaselineScheduler(@Value("${baseline.core.version}") String coreVersion) {
        this.coreVersion = coreVersion;
    }

    /**
     * Plans one request.
     *
     * @param request the request as the platform sent it
     * @return the plan, already checked against every invariant this module promises
     * @throws PlanRejectedException          with {@code 422} when the request cannot be planned
     * @throws PlanInvariantViolationException with {@code 500} when this scheduler has a defect
     */
    public PlanResponse schedule(PlanRequest request) {
        PlanRequestGuard.check(request);

        HardPrerequisiteGraph graph =
                HardPrerequisiteGraph.of(request.topics(), request.prerequisites());
        List<PlanRequest.Topic> order = StudyOrder.of(request, graph);
        RevisionPolicy revision = RevisionPolicy.of(request);
        AvailabilityAllocator allocator = AvailabilityAllocator.over(request);

        List<PlacedSession> placed = allocate(order, revision, allocator);
        if (placed.isEmpty()) {
            throw new PlanRejectedException("plan-would-be-empty",
                    "No session fits: the availability windows inside the horizon cannot hold even "
                            + "the first topic of the study order. An empty plan is refused rather "
                            + "than returned, because the platform rejects one anyway.",
                    List.of(order.get(0).id().toString()));
        }

        PlanResponse response = new PlanResponse(
                PlanRequest.VERSION,
                sequence(placed),
                BaselineFitness.of(order.size(), placed, allocator.availableMinutes(),
                        graph.appliedEdgeCount()),
                new PlanResponse.ExecutionMetadata(coreVersion, request.randomSeed(),
                        GENERATIONS, ELAPSED_MILLIS));

        PlanOutputInvariants.check(request, response, graph);
        return response;
    }

    /**
     * Walks the study order, placing each topic, and stops at the first one that does not fit.
     *
     * <p>Stopping rather than skipping is what keeps the scheduled set closed under prerequisites.
     * See the class comment.
     */
    private static List<PlacedSession> allocate(List<PlanRequest.Topic> order,
            RevisionPolicy revision, AvailabilityAllocator allocator) {

        List<PlacedSession> placed = new ArrayList<>();
        for (PlanRequest.Topic topic : order) {
            List<PlacedSession> ofTopic = placeTopic(topic, revision, allocator);
            if (ofTopic.isEmpty()) {
                break;
            }
            placed.addAll(ofTopic);
        }
        return List.copyOf(placed);
    }

    /**
     * Places one topic's blocks: its study block, and its revision block when it has earned one.
     *
     * <p>The revision follows its own study block directly, so a topic occupies one contiguous run of
     * the calendar. Either both blocks are placed or neither is: an empty result means the topic is
     * unscheduled, and the caller stops there.
     */
    private static List<PlacedSession> placeTopic(PlanRequest.Topic topic, RevisionPolicy revision,
            AvailabilityAllocator allocator) {

        Optional<Instant> study = allocator.place(topic.estimatedMinutes());
        if (study.isEmpty()) {
            return List.of();
        }
        PlacedSession studySession = new PlacedSession(topic.id(), SessionKind.STUDY, study.get(),
                topic.estimatedMinutes());
        if (!revision.needsRevision(topic.id())) {
            return List.of(studySession);
        }
        int minutes = RevisionPolicy.revisionMinutes(topic.estimatedMinutes());
        Optional<Instant> again = allocator.place(minutes);
        if (again.isEmpty()) {
            return List.of();
        }
        return List.of(studySession,
                new PlacedSession(topic.id(), SessionKind.REVISION, again.get(), minutes));
    }

    /**
     * Numbers the finished list.
     *
     * <p>The list is already chronological, because the allocator's cursor only moves forward, so the
     * index is a count rather than a sort — which is what makes it unique and contiguous without
     * either property having to be arranged.
     */
    private static List<PlanResponse.ScheduledSession> sequence(List<PlacedSession> placed) {
        return IntStream.range(0, placed.size())
                .mapToObj(index -> toSession(placed.get(index), index))
                .toList();
    }

    private static PlanResponse.ScheduledSession toSession(PlacedSession placed, int index) {
        return new PlanResponse.ScheduledSession(placed.topicId(), placed.kind(), placed.start(),
                placed.durationMinutes(), index);
    }
}
