package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Which {@link EdgeProvenanceFilter} a request runs under.
 *
 * <h2>Per request, with a configured default — the same shape as the engine and the importance</h2>
 *
 * The condition is read from {@code algorithmParams.provenance}, a free map in the contract, and
 * falls back to {@code plan.prerequisites.provenance} when the request names none. Per request
 * rather than per deployment for the reason the engine choice gives: two conditions that need two
 * deploys cannot be compared without also comparing the deploys.
 *
 * <p>There is <b>no code default</b>. A deployment that cannot say which edges its plans were built
 * from fails at startup rather than answering with a condition nobody chose — the same rule
 * {@code ImportanceStrategies} applies, and for the same reason: the condition is what attributes a
 * stored plan to an arm of the experiment.
 *
 * <h2>An unknown id is refused, never silently replaced by the default</h2>
 *
 * Falling back would run one condition while the caller recorded another, which is the one failure
 * mode an ablation cannot survive: every number collected under that id would be attributed to the
 * wrong arm, and nothing in the answer would say so.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class PrerequisiteProvenance {

    /** The key under which the resolved condition is echoed in {@code fitness}. */
    public static final String FITNESS_KEY = "prerequisite-provenance";

    private final EdgeProvenanceFilter configured;

    /**
     * @param configuredId the condition to use when a request names none; no code default
     */
    public PrerequisiteProvenance(
            @Value("${plan.prerequisites.provenance}") String configuredId) {

        this.configured = EdgeProvenanceFilter.byId(configuredId);
        if (configured == null) {
            throw new IllegalStateException("plan.prerequisites.provenance is '" + configuredId
                    + "', which is not one of " + EdgeProvenanceFilter.ids() + ". Failing at "
                    + "startup rather than on the first request.");
        }
    }

    /**
     * The condition this request runs under.
     *
     * @param request the request as the platform sent it
     * @return the filter to apply to every edge of this run
     * @throws PlanRejectedException when the request names a condition that does not exist
     */
    public EdgeProvenanceFilter resolve(PlanRequest request) {
        Object requested = request.algorithmParams().get(EdgeProvenanceFilter.PARAM);
        if (requested == null) {
            return configured;
        }
        if (!(requested instanceof String id)) {
            throw new PlanRejectedException("unusable-provenance-filter",
                    "algorithmParams." + EdgeProvenanceFilter.PARAM + " must be a string naming "
                            + "one of " + EdgeProvenanceFilter.ids() + ".",
                    List.of(String.valueOf(requested)));
        }
        EdgeProvenanceFilter filter = EdgeProvenanceFilter.byId(id);
        if (filter == null) {
            throw new PlanRejectedException("unknown-provenance-filter",
                    "No provenance condition answers to '" + id + "'. Available: "
                            + EdgeProvenanceFilter.ids() + ". The request is refused rather than "
                            + "run on the default, because the condition is what attributes the "
                            + "resulting plan to an arm of the ablation.",
                    List.of(id));
        }
        return filter;
    }
}
