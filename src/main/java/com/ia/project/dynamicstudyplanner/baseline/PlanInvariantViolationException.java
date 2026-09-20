package com.ia.project.dynamicstudyplanner.baseline;

/**
 * This scheduler produced a plan that breaks one of its own output invariants.
 *
 * <h2>This is a defect in this module, never a bad request</h2>
 *
 * Everything a caller can get wrong is refused earlier, by {@link PlanRequestGuard}, with
 * {@code 422}. By the time {@link PlanOutputInvariants} runs, the request has been accepted and the
 * plan has been built — so a violation here means the scheduler itself is wrong. It is answered with
 * {@code 500}, and it is the one outcome of this module that should never be observed.
 *
 * <p>Failing loudly is the point. The platform validates eight of the twelve invariants on receipt
 * ({@code docs/CORE_CONTRACT_SURVEY.md} §3) and silently trusts the other four, so a plan that
 * overlaps itself or leaves the horizon would be stored as a real study plan. Refusing to emit it is
 * cheaper than discovering it in the student's calendar.
 */
public class PlanInvariantViolationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /**
     * @param detail which invariant broke, and on which session
     */
    public PlanInvariantViolationException(String detail) {
        super(detail);
    }
}
