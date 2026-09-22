package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;

/**
 * One way of turning a {@code PlanRequest} into a {@code PlanResponse}.
 *
 * <h2>Why there are two, behind one interface</h2>
 *
 * The greedy baseline and the genetic algorithm are the two <b>conditions of the experiment</b>. A
 * claim that the genetic algorithm plans better than a competent deterministic heuristic is only
 * measurable if both answer the same endpoint, from the same request, on the same build. That is
 * also why the engine is chosen <b>per request</b> rather than per deployment or per student
 * profile: two conditions that require two deploys cannot be compared without also comparing the
 * deploys.
 *
 * <h2>What an implementation owes its caller</h2>
 *
 * Every implementation satisfies the same output invariants — the eight
 * {@code RestSinapseCore.validated} applies, the four it does not, and contiguous
 * {@code sequenceIndex} — and every implementation declares which engine ran, under
 * {@link #FITNESS_ENGINE_KEY} in {@code fitness}. Both obligations are enforced from outside:
 * {@link PlanOutputInvariants} for the first, {@link PlanEngineSelector} for the second, and
 * {@code PlanEngineInvariantTest} parameterised over every registered engine for both.
 *
 * <p>Implementations are stateless with respect to a request, because {@link #plan} is called
 * concurrently. Anything that holds per-request state is created inside the call.
 */
public interface PlanEngine {

    /**
     * The {@code fitness} key under which every engine names itself.
     *
     * <p>{@code fitness} is opaque to the platform — read off the wire and never interpreted — so
     * this is the only way a stored plan can be attributed to a condition of the experiment months
     * later. That attribution is the reason the map is filled at all, which is why the key is part
     * of the interface rather than a convention each engine repeats.
     */
    String FITNESS_ENGINE_KEY = "engine";

    /**
     * The identifier this engine answers to in {@code algorithmParams.engine}.
     *
     * @return a stable, lower-case, hyphenated id; unique across registered engines
     */
    String id();

    /**
     * Plans one request.
     *
     * @param request the snapshot the platform assembled
     * @return a plan that satisfies every output invariant of the protocol
     * @throws PlanRejectedException           when the request is understood and cannot be planned
     * @throws PlanInvariantViolationException when this engine has a defect
     */
    PlanResponse plan(PlanRequest request);
}
