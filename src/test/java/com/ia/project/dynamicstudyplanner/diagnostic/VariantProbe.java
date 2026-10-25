package com.ia.project.dynamicstudyplanner.diagnostic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.plan.BuildIdentity;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlanEngine;
import com.ia.project.dynamicstudyplanner.plan.PlanInvariantViolationException;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.plan.VariantEngine;
import com.ia.project.dynamicstudyplanner.plan.VariantPlan;
import com.ia.project.dynamicstudyplanner.sinapse.RequestConditions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link EngineProbe} para as cópias de motor do EOA-12b: o mesmo avaliador e as mesmas métricas.
 *
 * <p>A resposta da cópia recebe o mesmo carimbo do seletor de produção ({@code fitness.engine} e
 * {@code fitness.build}), para que o hash canônico da cópia em {@code BASE} seja comparável com o da
 * resposta de produção. O hash é o de {@link RealCatalogRepairTest}: chaves de mapa em ordem, sem
 * {@value BuildIdentity#FITNESS_KEY}.
 */
final class VariantProbe {

    private final PlanScoring scoring;
    private final RequestConditions conditions;
    private final ObjectMapper hashing;
    private final String buildId;
    private final int generations;

    VariantProbe(PlanScoring scoring, RequestConditions conditions, ObjectMapper mapper, String buildId,
            int generations) {

        this.scoring = scoring;
        this.conditions = conditions;
        this.hashing = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.buildId = buildId;
        this.generations = generations;
    }

    /**
     * Uma execução medida.
     *
     * @param outcome       as métricas, no formato de {@link EngineProbe.Outcome}
     * @param prefixReading o que a leitura de prefixo de produção teria nomeado como não agendado
     */
    record Measured(EngineProbe.Outcome outcome, List<String> prefixReading) {
    }

    Measured run(String instance, VariantEngine engine, PlanRequest request) {
        long capacity = DiagnosticInstances.capacity(request);
        int budget = "greedy-baseline".equals(engine.id()) ? 0 : generations;
        long started = System.nanoTime();
        try {
            VariantPlan plan = engine.plan(request);
            double wallMillis = (System.nanoTime() - started) / 1e6;
            return new Measured(accepted(instance, engine.id(), request, stamp(plan.response(),
                    engine.id()), capacity, wallMillis), plan.prefixReading());
        } catch (PlanRejectedException rejected) {
            double wallMillis = (System.nanoTime() - started) / 1e6;
            return new Measured(EngineProbe.Outcome.failed(instance, request, rejected.reason(), "",
                    rejected.getMessage() + " offending=" + rejected.offending(), budget, wallMillis),
                    List.of());
        } catch (PlanInvariantViolationException violation) {
            double wallMillis = (System.nanoTime() - started) / 1e6;
            return new Measured(EngineProbe.Outcome.failed(instance, request,
                    "plan-invariant-violation", violation.getMessage(), "", budget, wallMillis),
                    List.of());
        }
    }

    /** Cópia de {@code EngineProbe.accepted}: o mesmo avaliador a posteriori, as mesmas métricas. */
    private EngineProbe.Outcome accepted(String instance, String engine, PlanRequest request,
            PlanResponse response, long capacity, double wallMillis) {

        EvolutionContext context = scoring.contextFor(request, conditions.provenance(request),
                conditions.importance(request));
        FitnessBreakdown posthoc = scoring.score(scoring.rebuild(request, response), context);
        List<PlanResponse.ScheduledSession> sessions = response.sessions();
        long minutes = sessions.stream().mapToLong(PlanResponse.ScheduledSession::durationMinutes).sum();
        int topics = (int) sessions.stream().map(PlanResponse.ScheduledSession::topicId)
                .distinct().count();
        return new EngineProbe.Outcome(instance, engine, request.randomSeed(), EngineProbe.ACCEPTED, "",
                "", topics, sessions.size(), minutes, capacity, EngineProbe.term(posthoc, "syllabusMastery"),
                posthoc.aggregate(), posthoc.rawScore(), response.metadata().generations(),
                wallMillis, response);
    }

    /** O carimbo de {@code PlanEngineSelector.stamp}. */
    private PlanResponse stamp(PlanResponse response, String engineId) {
        Map<String, Object> fitness = new LinkedHashMap<>();
        fitness.put(PlanEngine.FITNESS_ENGINE_KEY, engineId);
        fitness.put(BuildIdentity.FITNESS_KEY, buildId);
        response.fitness().forEach(fitness::putIfAbsent);
        return new PlanResponse(response.contractVersion(), response.sessions(), fitness,
                response.metadata());
    }

    /** As invariantes de novo, fora do motor, para a coluna ficar explícita. */
    static String recheck(PlanRequest request, PlanResponse response, HardPrerequisiteGraph graph) {
        if (response == null) {
            return "n/a";
        }
        try {
            PlanOutputInvariants.check(request, response, graph);
            return "pass";
        } catch (PlanInvariantViolationException violation) {
            return Csv.quote("fail: " + violation.getMessage());
        }
    }

    /** O hash canônico da resposta inteira, sem o commit do build. Vazio numa recusa. */
    String responseHash(PlanResponse response) {
        if (response == null) {
            return "";
        }
        Map<String, Object> fitness = new HashMap<>(response.fitness());
        fitness.remove(BuildIdentity.FITNESS_KEY);
        return sha256(new PlanResponse(response.contractVersion(), response.sessions(), fitness,
                response.metadata()));
    }

    /** O hash só das sessões, para contar planos distintos. Vazio numa recusa. */
    String sessionsHash(PlanResponse response) {
        return response == null ? "" : sha256(response.sessions());
    }

    private String sha256(Object value) {
        try {
            byte[] json = hashing.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
