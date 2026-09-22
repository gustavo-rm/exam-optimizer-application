package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Chooses the engine a request runs on, and guarantees the answer says which one it was.
 *
 * <h2>Chosen per request, from {@code algorithmParams}</h2>
 *
 * {@code algorithmParams} is a {@code Map<String, Object>} in the contract — verified, not assumed:
 * {@code PlanRequest} declares it as one and copies it with {@code Map.copyOf}. So carrying the
 * choice there invents no field and needs no contract change. The key is {@value #ENGINE_PARAM};
 * absent or null, the configured default runs.
 *
 * <p>Per request rather than per deployment is the point: see {@link PlanEngine}. Two experimental
 * conditions have to run against one build to be comparable.
 *
 * <h2>An unknown engine is refused, not defaulted</h2>
 *
 * A request naming an engine that does not exist is answered with {@code 422} listing the ids that
 * do. Falling back to the default would run condition A while the caller recorded condition B, and
 * the resulting measurements would be wrong in a way nothing downstream could detect. The same
 * reasoning applies to a non-string value under the key.
 *
 * <h2>The engine name is stamped here, not trusted from the engine</h2>
 *
 * {@link #plan} overwrites {@link PlanEngine#FITNESS_ENGINE_KEY} in the returned {@code fitness} map
 * with the id of the engine that was actually invoked. An engine cannot mislabel its own output,
 * and an engine that forgets to label it cannot ship an unattributed plan.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class PlanEngineSelector {

    /** The {@code algorithmParams} key carrying the caller's choice. */
    public static final String ENGINE_PARAM = "engine";

    private final Map<String, PlanEngine> byId;
    private final String defaultEngineId;

    /**
     * @param engines         every registered engine, injected by Spring
     * @param defaultEngineId the id to use when the request names none; from
     *                        {@code plan.engine.default}. There is no code default — a deployment
     *                        that cannot say which condition it runs by default fails to start
     */
    public PlanEngineSelector(List<PlanEngine> engines,
            @Value("${plan.engine.default}") String defaultEngineId) {

        Map<String, PlanEngine> registry = new TreeMap<>();
        for (PlanEngine engine : engines) {
            PlanEngine clash = registry.put(engine.id(), engine);
            if (clash != null) {
                throw new IllegalStateException("Two engines answer to the id '" + engine.id()
                        + "': " + clash.getClass().getName() + " and " + engine.getClass().getName()
                        + ". The id is how a stored plan is attributed to a condition of the "
                        + "experiment, so it has to be unique.");
            }
        }
        this.byId = Map.copyOf(registry);
        this.defaultEngineId = defaultEngineId;

        if (!byId.containsKey(defaultEngineId)) {
            throw new IllegalStateException("plan.engine.default is '" + defaultEngineId
                    + "', which no registered engine answers to. Registered: " + registeredIds()
                    + ". Failing at startup rather than on the first request.");
        }
    }

    /**
     * Runs the request on the engine it asked for, or on the configured default.
     *
     * @param request the request as the platform sent it
     * @return the plan, with {@code fitness.engine} set to the engine that produced it
     * @throws PlanRejectedException when the request names an engine that does not exist
     */
    public PlanResponse plan(PlanRequest request) {
        PlanEngine engine = resolve(request);
        return stamp(engine.plan(request), engine.id());
    }

    /** @return the id of the engine this request runs on */
    public String resolveId(PlanRequest request) {
        return resolve(request).id();
    }

    private PlanEngine resolve(PlanRequest request) {
        Object requested = request.algorithmParams().get(ENGINE_PARAM);
        if (requested == null) {
            return byId.get(defaultEngineId);
        }
        if (!(requested instanceof String name)) {
            throw new PlanRejectedException("unusable-engine",
                    "algorithmParams." + ENGINE_PARAM + " must be a string naming one of the "
                            + "registered engines: " + registeredIds() + ".",
                    List.of(String.valueOf(requested)));
        }
        PlanEngine engine = byId.get(name);
        if (engine == null) {
            throw new PlanRejectedException("unknown-engine",
                    "No engine answers to '" + name + "'. Registered engines: " + registeredIds()
                            + ". The request is refused rather than run on the default, because a "
                            + "plan recorded under the wrong condition is worse than no plan.",
                    List.of(name));
        }
        return engine;
    }

    /**
     * Replaces the engine key with the id of the engine that ran.
     *
     * <p>Insertion order puts the engine first, which costs nothing and makes the attribution the
     * first thing a person reads in a stored plan. The contract record copies the map with
     * {@code Map.copyOf} afterwards, whose iteration order is its own, so the order is a
     * convenience here and not a promise.
     */
    private static PlanResponse stamp(PlanResponse response, String engineId) {
        Map<String, Object> fitness = new LinkedHashMap<>();
        fitness.put(PlanEngine.FITNESS_ENGINE_KEY, engineId);
        response.fitness().forEach((key, value) -> {
            if (!PlanEngine.FITNESS_ENGINE_KEY.equals(key)) {
                fitness.put(key, value);
            }
        });
        return new PlanResponse(response.contractVersion(), response.sessions(),
                fitness, response.metadata());
    }

    /** @return the registered ids, sorted, for an error message a caller can act on */
    public List<String> registeredIds() {
        return byId.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }
}
