package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.tactical.TacticalSlots;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Leitura de um plano tático nos testes da opção P (EOA-12b).
 */
public final class TacticalPlans {

    private TacticalPlans() {
    }

    /**
     * O calendário como lista ordenada de pares, para comparar dois planos por valor.
     *
     * <p>{@code TacticalStudyPlan} não define {@code equals}; os pares ({@code TimeSlot},
     * {@code TacticalStudyBlock}) são records e comparam por valor, na ordem de inserção.
     */
    public static List<Map.Entry<TimeSlot, TacticalStudyBlock>> entries(TacticalStudyPlan plan) {
        return plan.getSchedule().entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** {@link SessionPlacement.Result} comparável por valor. */
    public record Snapshot(List<Map.Entry<TimeSlot, TacticalStudyBlock>> schedule, int topicsScheduled,
            int topicsTotal, long scheduledMinutes) {

        public static Snapshot of(SessionPlacement.Result result) {
            return new Snapshot(entries(result.plan()), result.topicsScheduled(), result.topicsTotal(),
                    result.scheduledMinutes());
        }
    }

    /** @return os tópicos do plano, na ordem da primeira sessão de cada um */
    public static List<UUID> topicsInCalendarOrder(TacticalStudyPlan plan,
            Map<PlanningItem, UUID> topicIdsByItem) {

        Set<UUID> seen = new LinkedHashSet<>();
        TacticalSlots.ordered(plan).forEach(slot ->
                seen.add(topicIdsByItem.get(plan.getSchedule().get(slot).item())));
        return List.copyOf(seen);
    }

    /** @return o início da primeira sessão de {@code topic} */
    public static Instant firstStartOf(TacticalStudyPlan plan, Map<PlanningItem, UUID> topicIdsByItem,
            UUID topic) {

        return TacticalSlots.ordered(plan).stream()
                .filter(slot -> topic.equals(topicIdsByItem.get(plan.getSchedule().get(slot).item())))
                .map(TimeSlot::startTime)
                .map(AvailabilityWindows::toInstant)
                .findFirst()
                .orElseThrow();
    }

    /**
     * O plano como resposta, já passado por {@link PlanOutputInvariants}.
     *
     * @throws com.ia.project.dynamicstudyplanner.plan.PlanInvariantViolationException se violar
     */
    public static PlanResponse checked(PlanRequest request, TacticalStudyPlan plan,
            HardPrerequisiteGraph graph) {

        PlanResponse response = new PlanResponse(PlanRequest.VERSION,
                TacticalSessions.of(plan, TopicPlanningItems.topicIdsByItem(request.topics())),
                Map.of(), new PlanResponse.ExecutionMetadata("test", request.randomSeed(), 0, 0L));
        PlanOutputInvariants.check(request, response, graph);
        return response;
    }
}
