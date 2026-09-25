package com.ia.project.dynamicstudyplanner.benchmark.metric;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.SoftPrerequisiteEdges;
import com.ia.project.dynamicstudyplanner.sinapse.AvailabilityWindows;
import com.ia.project.dynamicstudyplanner.sinapse.SinapseEvolutionContexts;
import com.ia.project.dynamicstudyplanner.sinapse.TopicPlanningItems;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Scores any engine's plan with the <b>same</b> evaluator, after the fact.
 *
 * <h2>Why every plan is re-scored here rather than read off the answer</h2>
 *
 * This is the methodological centre of the harness. The genetic engine <i>maximises</i> the fitness
 * and reports it; the greedy scheduler optimises no objective function at all and reports none.
 * Taking each engine's own number and putting the two in a column called "fitness" would compare a
 * quantity one engine was built to maximise against a quantity the other has never seen — a
 * comparison biased by construction, in the direction of whichever engine the metric came from.
 *
 * <p>So the plan that comes back over the wire is converted back into the tactical chromosome it
 * describes and handed to <b>one</b> evaluator: the production composition, injected, not rebuilt.
 * Both engines are then scored on identical terms.
 *
 * <p>Even so, the resulting number is reported as <b>the genetic engine's objective</b> and never as
 * the comparison. It is still the thing one engine optimises and the other ignores, and a greedy
 * plan scoring lower on it is not evidence of a worse plan — only of a plan that was not searching
 * for that maximum. The conclusions in the report rest on the outcome metrics, which are properties
 * of the schedule and belong to neither engine's objective.
 *
 * <h2>The rebuild is lossless for everything the fitness reads</h2>
 *
 * The terms read start times, durations, the item and its difficulty band, and the session kind.
 * All five survive the round trip through {@code PlanResponse}. Nothing the evaluator consults is
 * lost, so the score is the score the engine's own plan would have earned.
 */
public final class PlanScoring {

    private final FitnessComposition composition;
    private final RetentionAlgorithm retention;

    /**
     * @param composition the production composition, injected from the context — not rebuilt here,
     *                    because a rebuilt copy would silently drift from the one that ran
     * @param retention   the recurrence the retention term reads
     */
    public PlanScoring(FitnessComposition composition, RetentionAlgorithm retention) {
        this.composition = composition;
        this.retention = retention;
    }

    /** @return the name of the composition every plan is scored against */
    public String compositionPath() {
        return composition.path();
    }

    /**
     * Rebuilds the context the engine would have run under.
     *
     * @param request    the instance
     * @param filter     the provenance condition the run used; the soft edges follow from it
     * @param importance the importance strategy the run used
     * @return the context, assembled by production's own assembler
     */
    public EvolutionContext contextFor(PlanRequest request, EdgeProvenanceFilter filter,
            ImportanceStrategy importance) {

        List<PlanningItem> items = TopicPlanningItems.of(request.topics());
        SoftPrerequisiteEdges soft =
                SoftPrerequisiteEdges.of(request.topics(), request.prerequisites(), filter);
        return SinapseEvolutionContexts.of(request, items,
                AvailabilityWindows.of(request.availability()), composition.evaluator(), retention,
                importance, SinapseEvolutionContexts.softPrerequisites(soft, request.topics()));
    }

    /**
     * The plan the answer describes, as the chromosome the fitness understands.
     *
     * @param request  the instance, to resolve a topic identifier back to its planning item
     * @param response the answer, from any engine
     * @return the tactical plan
     */
    public TacticalStudyPlan rebuild(PlanRequest request, PlanResponse response) {
        Map<UUID, PlanningItem> byId = new TreeMap<>();
        request.topics().forEach(topic -> byId.put(topic.id(), TopicPlanningItems.toItem(topic)));

        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();
        for (PlanResponse.ScheduledSession session : response.sessions()) {
            Instant end = session.scheduledStart().plusSeconds(session.durationMinutes() * 60L);
            TimeSlot slot = new TimeSlot(AvailabilityWindows.toLocal(session.scheduledStart()),
                    AvailabilityWindows.toLocal(end));
            schedule.put(slot, new TacticalStudyBlock(byId.get(session.topicId()),
                    methodologyOf(session.kind()), session.durationMinutes()));
        }
        return new TacticalStudyPlan(schedule);
    }

    /**
     * Scores a rebuilt plan.
     *
     * @param plan    the rebuilt chromosome
     * @param context the context it is scored under
     * @return the full decomposition, term by term
     */
    public FitnessBreakdown score(TacticalStudyPlan plan, EvolutionContext context) {
        FitnessEvaluator evaluator = composition.evaluator();
        return evaluator.explain(plan, context);
    }

    /** The same mapping {@code SessionPlacement} uses, in reverse. */
    private static StudyMethodology methodologyOf(SessionKind kind) {
        return kind == SessionKind.REVISION
                ? StudyMethodology.SPACED_REPETITION_REVIEW
                : StudyMethodology.ACTIVE_RECALL;
    }
}
