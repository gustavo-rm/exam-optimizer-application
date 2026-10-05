package com.ia.project.dynamicstudyplanner.diagnostic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * As instâncias do catálogo real e as transformações que o diagnóstico aplica a elas.
 *
 * <p>Toda transformação devolve um {@code PlanRequest} novo e nunca altera o original: o que entra
 * no motor é sempre o documento lido do arquivo, mais exatamente a mudança declarada no nome do
 * método. Nada aqui inventa campo, normaliza valor ou reordena lista que o motor leia.
 */
final class DiagnosticInstances {

    /** Os três perfis, na ordem do README da plataforma. */
    static final List<String> PROFILES = List.of("apertado", "medio", "folgado");

    private static final String RESOURCE = "/instances/plan-request-catalogo-real-%s.json";

    private DiagnosticInstances() {
    }

    /**
     * Lê uma instância com o {@code ObjectMapper} do contexto — o mesmo que desserializa o corpo de
     * {@code POST /plans} —, para que a entrada seja a que o controller entregaria ao seletor.
     */
    static PlanRequest load(ObjectMapper mapper, String profile) {
        String path = String.format(RESOURCE, profile);
        try (InputStream in = DiagnosticInstances.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Instância ausente: " + path);
            }
            return mapper.readValue(in, PlanRequest.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * O mesmo pedido, com o motor escolhido pelo nome e a semente trocada.
     *
     * <p>Os demais {@code algorithmParams} do arquivo ficam: o Core os ignora, e retirá-los seria
     * uma diferença em relação ao que a plataforma envia.
     */
    static PlanRequest forEngine(PlanRequest request, String engine, long seed) {
        Map<String, Object> params = new HashMap<>(request.algorithmParams());
        params.put(PlanEngineSelector.ENGINE_PARAM, engine);
        return new PlanRequest(request.contractVersion(), request.horizon(), request.availability(),
                request.goals(), request.topics(), request.prerequisites(), request.history(),
                params, seed);
    }

    /** Minutos de janela dentro do horizonte, contados pelo alocador de produção. */
    static long capacity(PlanRequest request) {
        return AvailabilityAllocator.over(request).availableMinutes();
    }

    /** Soma de {@code estimatedMinutes} dos tópicos. */
    static long demand(PlanRequest request) {
        return request.topics().stream().mapToLong(PlanRequest.Topic::estimatedMinutes).sum();
    }

    /**
     * Mantém só os tópicos de {@code keep}, as arestas entre eles e as metas de disciplinas que
     * ainda têm tópico.
     */
    static PlanRequest retainTopics(PlanRequest request, Set<UUID> keep) {
        List<PlanRequest.Topic> topics = request.topics().stream()
                .filter(topic -> keep.contains(topic.id()))
                .toList();
        Set<UUID> subjects = topics.stream().map(PlanRequest.Topic::subjectId)
                .collect(Collectors.toSet());
        List<PlanRequest.PrerequisiteEdge> edges = request.prerequisites().stream()
                .filter(edge -> keep.contains(edge.prerequisiteTopicId())
                        && keep.contains(edge.dependentTopicId()))
                .toList();
        List<PlanRequest.Goal> goals = request.goals().stream()
                .filter(goal -> subjects.contains(goal.subjectId()))
                .toList();
        return new PlanRequest(request.contractVersion(), request.horizon(), request.availability(),
                goals, topics, edges, request.history(), request.algorithmParams(),
                request.randomSeed());
    }

    /** O mesmo pedido com outra lista de janelas. */
    static PlanRequest withAvailability(PlanRequest request,
            List<PlanRequest.AvailabilitySlot> availability) {
        return new PlanRequest(request.contractVersion(), request.horizon(), availability,
                request.goals(), request.topics(), request.prerequisites(), request.history(),
                request.algorithmParams(), request.randomSeed());
    }

    /** O mesmo pedido com outra lista de metas. */
    static PlanRequest withGoals(PlanRequest request, List<PlanRequest.Goal> goals) {
        return new PlanRequest(request.contractVersion(), request.horizon(), request.availability(),
                goals, request.topics(), request.prerequisites(), request.history(),
                request.algorithmParams(), request.randomSeed());
    }

    /**
     * Os {@code k} primeiros tópicos por {@code position}, com a disponibilidade reescalada para
     * manter {@code rho} = capacidade / demanda.
     *
     * <h2>Como a disponibilidade é reescalada — suposição de projeto</h2>
     *
     * As janelas são mantidas <b>inteiras</b> e tomadas em ordem cronológica, até o prefixo cuja
     * capacidade fica mais perto do alvo {@code rho * demanda(k)}. Encurtar cada janela daria um
     * {@code rho} exato, mas mudaria o comprimento das janelas, que é justamente uma das variáveis
     * suspeitas neste diagnóstico: a curva passaria a medir duas coisas ao mesmo tempo. O
     * {@code rho} efetivo de cada ponto vai para o CSV.
     */
    static PlanRequest firstTopicsAtSameRho(PlanRequest request, int k) {
        double rho = capacity(request) / (double) demand(request);
        Set<UUID> keep = request.topics().stream()
                .sorted(Comparator.comparingInt(PlanRequest.Topic::position))
                .limit(k)
                .map(PlanRequest.Topic::id)
                .collect(Collectors.toSet());
        PlanRequest reduced = retainTopics(request, keep);
        double target = rho * demand(reduced);
        return withAvailability(reduced, prefixClosestTo(request.availability(), target));
    }

    private static List<PlanRequest.AvailabilitySlot> prefixClosestTo(
            List<PlanRequest.AvailabilitySlot> slots, double targetMinutes) {

        List<PlanRequest.AvailabilitySlot> sorted = slots.stream()
                .sorted(Comparator.comparing(PlanRequest.AvailabilitySlot::start))
                .toList();
        List<PlanRequest.AvailabilitySlot> best = new ArrayList<>();
        List<PlanRequest.AvailabilitySlot> prefix = new ArrayList<>();
        double bestGap = targetMinutes;
        long minutes = 0;
        for (PlanRequest.AvailabilitySlot slot : sorted) {
            prefix.add(slot);
            minutes += Duration.between(slot.start(), slot.end()).toMinutes();
            double gap = Math.abs(minutes - targetMinutes);
            if (gap < bestGap) {
                bestGap = gap;
                best = new ArrayList<>(prefix);
            }
        }
        return best;
    }
}
