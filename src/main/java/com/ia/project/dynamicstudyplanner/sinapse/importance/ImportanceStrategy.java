package com.ia.project.dynamicstudyplanner.sinapse.importance;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;

import java.util.Map;

/**
 * Where the weight of the heaviest term in the fitness comes from.
 *
 * <h2>Why this is an interface and not a decision</h2>
 *
 * {@code ScoreGainObjective} carries <b>0.50 — half the fitness</b> — and computes
 * {@code importance x mastery(days)}. On the concurso product, {@code importance} was the subject's
 * value on the exam: {@code questionCount x thematic-axis weight}, external and objective, written
 * in the published syllabus. <b>The SINAPSE domain has no such input.</b> {@code PlanRequest}
 * carries no question count, no axis weight and no syllabus.
 *
 * <p>So the substitute changes what the dominant term <em>means</em>, and the two candidates change
 * it in different directions:
 *
 * <ul>
 *   <li>{@link GoalPriorityImportance} reads {@code goals[].priority} — <b>self-declared by the
 *       student</b>. It is the obvious substitute and it sits in tension with the platform's own
 *       decision L1 (ADR 0012, "the student declares nothing"), which avoided self-declared input
 *       deliberately.</li>
 *   <li>{@link PrerequisiteCentralityImportance} reads the curriculum's structure — how many topics
 *       transitively depend on a topic. <b>Objective</b>, consistent with L1, and it turns a graph
 *       that today only constrains into a signal.</li>
 * </ul>
 *
 * <p>Both are implemented because the second is a <b>hypothesis</b>, and a hypothesis with nothing
 * to test against is not measurable. The first is the default: it is the one whose meaning a product
 * decision already endorsed. This interface exists so that choosing between them is a configuration
 * change and a measurement, not a rewrite.
 *
 * <h2>What an implementation owes its caller</h2>
 *
 * <b>Raw values, on any scale it likes.</b> Priorities run 1..5 and centrality counts run 0..n, and
 * the two would be incomparable if either tried to scale itself. Normalisation is a single shared
 * step — {@code EvolutionContext.normalize} projects whatever arrives onto the unit simplex — so the
 * two conditions produce fitness values on the same scale and can be compared. Implementing the
 * normalisation here, per strategy, is the one thing that would break that;
 * {@code ImportanceScaleTest} asserts the outcome for every registered strategy.
 *
 * <p>Implementations are deterministic: the same request produces the same values, whatever order
 * the collections inside it happen to arrive in. And they are stateless, because
 * {@link #importanceOf} is called concurrently.
 */
public interface ImportanceStrategy {

    /**
     * The {@code fitness} key under which the strategy that ran names itself.
     *
     * <p>Without it a recorded run is not reproducible, because nothing says what
     * {@code importance} meant in that execution — and the term it feeds is half the fitness. The
     * answer has to be reconstructable from the response alone, without consulting the
     * configuration the deployment happened to hold at the time.
     */
    String FITNESS_KEY = "importance-strategy";

    /**
     * The identifier this strategy answers to in {@code algorithmParams.importance}.
     *
     * @return a stable, lower-case, hyphenated id; unique across registered strategies
     */
    String id();

    /**
     * Raw importance per planning item.
     *
     * @param request the request as the platform sent it
     * @return item {@literal ->} raw importance, in the topics' arrival order; never normalised
     * @throws com.ia.project.dynamicstudyplanner.plan.PlanRejectedException when the request cannot
     *         be ranked at all, which today means only a cycle in the {@code HARD} prerequisites
     */
    Map<PlanningItem, Double> importanceOf(PlanRequest request);
}
