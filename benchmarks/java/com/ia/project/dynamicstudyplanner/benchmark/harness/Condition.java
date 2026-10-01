package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PrecedencePolicy;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine;
import com.ia.project.dynamicstudyplanner.sinapse.TimelinePlanEngine;
import com.ia.project.dynamicstudyplanner.sinapse.importance.GoalPriorityImportance;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategies;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One cell of the condition matrix: an engine crossed with a provenance condition.
 *
 * <h2>Adding an engine is {@link #ENGINES} plus one line</h2>
 *
 * That is the requirement this class exists to satisfy, and it holds because a condition carries an
 * engine <b>id</b> and nothing else about the engine. The harness never names a class, never builds
 * one, and never branches on which engine it is running: it writes the id into
 * {@code algorithmParams} and hands the request to {@link PlanEngineSelector}, exactly as the
 * platform would. The timeline chromosome enters as a third id in the list below, and every metric,
 * every invariant and every CSV column applies to it unchanged.
 *
 * <p>The one place the harness does distinguish engines is the cost group, and it distinguishes them
 * by a <b>property</b> rather than by identity: an engine that reports zero generations evaluated no
 * population, so its evaluation count is zero. A third evolutionary engine reports its own
 * generations and gets its own count with no edit here.
 *
 * <h2>Every run goes through the selector</h2>
 *
 * Not through {@code GeneticPlanEngine} or {@code GreedyBaselineScheduler} directly, even though
 * both are beans the harness could inject. The selector is what stamps {@code fitness.engine}, and
 * an engine cannot mislabel itself through it — so a row's attribution is the production path's own
 * and not the harness's bookkeeping. It is also the only way the measurement exercises the same
 * entry point the platform calls.
 *
 * <h2>Importance is held fixed, and written into the request rather than assumed</h2>
 *
 * The matrix does not vary {@code importance}: it is not one of the axes this measurement is about,
 * and crossing it in would double every cell to answer a different question. It is nonetheless
 * written into every request explicitly, so a row in the CSV says which meaning of importance
 * produced it instead of depending on what the deployment's properties happened to say.
 *
 * @param engine     the engine id, as it appears in {@code algorithmParams.engine}
 * @param provenance the ablation condition
 * @param policy     how this cell treats ordering preferences: repair them or price them. {@code null}
 *                   leaves the engine on its own default, which is what the published matrix uses —
 *                   see {@code PrecedencePolicies} for why the default is per engine
 */
public record Condition(String engine, EdgeProvenanceFilter provenance, PrecedencePolicy policy) {

    /** A condição com a política que o motor já tem por padrão. */
    public Condition(String engine, EdgeProvenanceFilter provenance) {
        this(engine, provenance, null);
    }

    /**
     * The engines in the matrix.
     *
     * <p>v1 is the genetic engine with the tactical prerequisite stage; the greedy scheduler is the
     * baseline; v2 is the timeline chromosome. <b>v2 was added as exactly one line here</b>, which is
     * the claim this class was built to make good on — no metric, no invariant and no CSV column
     * changed to accommodate it.
     */
    public static final List<String> ENGINES = List.of(
            GreedyBaselineEngine.ID,
            GeneticPlanEngine.ID,
            TimelinePlanEngine.ID);

    /** The three cumulative ablation conditions, narrowest first. */
    public static final List<EdgeProvenanceFilter> PROVENANCES = List.of(
            EdgeProvenanceFilter.CURATED,
            EdgeProvenanceFilter.CURATED_TEXTBOOK,
            EdgeProvenanceFilter.ALL);

    /** Held fixed: the default a product decision already endorsed. Recorded on every row. */
    public static final String IMPORTANCE = GoalPriorityImportance.ID;

    /** @return every engine crossed with every provenance condition, in a fixed order */
    public static List<Condition> matrix() {
        List<Condition> matrix = new ArrayList<>(ENGINES.size() * PROVENANCES.size());
        for (String engine : ENGINES) {
            for (EdgeProvenanceFilter provenance : PROVENANCES) {
                matrix.add(new Condition(engine, provenance));
            }
        }
        return List.copyOf(matrix);
    }

    /**
     * The instance's request, set up for this condition and this seed.
     *
     * @param request the instance's request, which carries no {@code algorithmParams} of its own
     * @param seed    the replication unit
     * @return a request naming this condition, ready for {@link PlanEngineSelector#plan}
     */
    public PlanRequest applyTo(PlanRequest request, long seed) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(PlanEngineSelector.ENGINE_PARAM, engine);
        params.put(EdgeProvenanceFilter.PARAM, provenance.id());
        params.put(ImportanceStrategies.PARAM, IMPORTANCE);
        // Ausente quando a celula nao nomeia politica: e assim que a matriz publicada deixa cada
        // motor no padrao dele, que e o que mantem os numeros de 09, 10 e 11 reproduzindo.
        if (policy != null) {
            params.put(PrecedencePolicy.PARAM, policy.id());
        }

        return new PlanRequest(request.contractVersion(), request.horizon(), request.availability(),
                request.goals(), request.topics(), request.prerequisites(), request.history(),
                params, seed);
    }

    /** {@code ga/curated-textbook} ou {@code ga-timeline/all/lexicographic}. */
    public String label() {
        return engine + "/" + provenance.id() + (policy == null ? "" : "/" + policy.id());
    }

    /** @return a política desta célula, resolvida para o que o motor usaria quando ela é nula */
    public PrecedencePolicy effectivePolicy() {
        if (policy != null) {
            return policy;
        }
        return GeneticPlanEngine.ID.equals(engine)
                ? PrecedencePolicy.LEXICOGRAPHIC
                : PrecedencePolicy.WEIGHTED;
    }
}
