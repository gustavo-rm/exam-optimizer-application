package com.ia.project.dynamicstudyplanner.plan;

/**
 * The two literals this module shares between its controller, its security chain and its tests.
 *
 * <p>They live here rather than on the controller because the security chain is <b>not</b> gated by
 * the profile while the controller is: the chain that makes {@code /plans} public must exist even
 * when the endpoint does not, so that the absence of the profile produces an honest {@code 404}
 * instead of the {@code 401} the application's catch-all rule would otherwise return for a path
 * nobody handles. See {@link BaselinePlanSecurityConfig}.
 */
public final class PlanProtocol {

    /**
     * The Spring profile that switches the baseline scheduler on.
     *
     * <p>Without it the application boots exactly as it did before this module existed: no bean of
     * this package is created and {@code POST /plans} has no handler.
     */
    public static final String PROFILE = "baseline-core";

    /**
     * The path the platform posts a {@code PlanRequest} to.
     *
     * <p>Fixed by {@code sinapse-platform}: {@code sinapse.core.plan-path} is {@code /plans}
     * ({@code docs/CORE_CONTRACT_SURVEY.md} §1.7), so this side does not get to choose it. Note that
     * it is deliberately <b>not</b> under {@code /api/v1/**} — it is not part of this service's
     * public API, it is the Core protocol.
     */
    public static final String PLANS_PATH = "/plans";

    private PlanProtocol() {
    }
}
