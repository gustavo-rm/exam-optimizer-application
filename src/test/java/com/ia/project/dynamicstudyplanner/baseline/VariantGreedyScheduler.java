package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;
import com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;
import com.ia.project.dynamicstudyplanner.plan.PlacedSession;
import com.ia.project.dynamicstudyplanner.plan.PlacementVariant;
import com.ia.project.dynamicstudyplanner.plan.PlanOutputInvariants;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.plan.PlanRequestGuard;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteProvenance;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteReport;
import com.ia.project.dynamicstudyplanner.plan.SkipAheadAllocator;
import com.ia.project.dynamicstudyplanner.plan.SoftPrerequisiteEdges;
import com.ia.project.dynamicstudyplanner.plan.VariantEngine;
import com.ia.project.dynamicstudyplanner.plan.VariantPlan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Cópia de teste de {@link GreedyBaselineScheduler#schedule}, com a colocação trocável (EOA-12b).
 *
 * <p>Tudo, exceto {@code allocate}, é o código de produção copiado linha a linha, e em
 * {@link PlacementVariant#BASE} a resposta é a mesma do motor de produção (a medição confere o hash em
 * toda célula). Em {@link PlacementVariant#SKIP_AHEAD} vale a opção P, com as regras de
 * {@code docs/MEDICAO_ALOCADOR.md} §2. Nada aqui é usado em produção, e a D2b (ADR-0012) não
 * autoriza levar isto para {@code src/main}.
 */
public final class VariantGreedyScheduler implements VariantEngine {

    private final String coreVersion;
    private final PrerequisiteProvenance provenance;
    private final PlacementVariant variant;

    public VariantGreedyScheduler(String coreVersion, PrerequisiteProvenance provenance,
            PlacementVariant variant) {

        this.coreVersion = coreVersion;
        this.provenance = provenance;
        this.variant = variant;
    }

    @Override
    public String id() {
        return GreedyBaselineEngine.ID;
    }

    @Override
    public VariantPlan plan(PlanRequest request) {
        PlanRequestGuard.check(request);

        EdgeProvenanceFilter filter = provenance.resolve(request);
        HardPrerequisiteGraph graph =
                HardPrerequisiteGraph.of(request.topics(), request.prerequisites(), filter);
        SoftPrerequisiteEdges soft =
                SoftPrerequisiteEdges.of(request.topics(), request.prerequisites(), filter);

        List<PlanRequest.Topic> order = StudyOrder.of(request, graph);
        RevisionPolicy revision = RevisionPolicy.of(request);
        AvailabilityAllocator allocator = AvailabilityAllocator.over(request);

        List<PlacedSession> placed = variant == PlacementVariant.BASE
                ? allocate(order, revision, allocator)
                : skipAhead(order, revision, SkipAheadAllocator.over(request), graph);
        if (placed.isEmpty()) {
            throw new PlanRejectedException("plan-would-be-empty",
                    "No session fits: the availability windows inside the horizon cannot hold even "
                            + "the first topic of the study order. An empty plan is refused rather "
                            + "than returned, because the platform rejects one anyway.",
                    List.of(order.get(0).id().toString()));
        }

        PrerequisiteReport measured = PrerequisiteReport.measured(filter, graph, soft, order,
                topicsScheduled(placed));
        PlanResponse response = new PlanResponse(
                PlanRequest.VERSION,
                sequence(placed),
                BaselineFitness.of(order.size(), placed, allocator.availableMinutes(),
                        graph.appliedEdgeCount(), byAbsence(measured, order, placed)),
                new PlanResponse.ExecutionMetadata(coreVersion, request.randomSeed(), 0, 0L));

        PlanOutputInvariants.check(request, response, graph);
        return new VariantPlan(response, measured.unscheduled());
    }

    /**
     * A regra P9: sob P, os não agendados são os ausentes de fato, na ordem do motor.
     *
     * <p>Na base o plano é prefixo da ordem e as duas leituras coincidem, então a resposta da base
     * fica a de produção.
     */
    private PrerequisiteReport byAbsence(PrerequisiteReport measured, List<PlanRequest.Topic> order,
            List<PlacedSession> placed) {

        if (variant == PlacementVariant.BASE) {
            return measured;
        }
        Set<UUID> scheduled = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        placed.forEach(session -> scheduled.add(session.topicId()));
        List<String> absent = order.stream().map(PlanRequest.Topic::id)
                .filter(id -> !scheduled.contains(id))
                .map(UUID::toString)
                .toList();
        return new PrerequisiteReport(measured.policy(), measured.provenance(), measured.hardEdges(),
                measured.softEdges(), measured.inversionsBefore(), measured.inversionsAfter(), absent);
    }

    /** Opção P: o tópico que não cabe é pulado com os seus dependentes {@code HARD}, e a ordem segue. */
    private static List<PlacedSession> skipAhead(List<PlanRequest.Topic> order,
            RevisionPolicy revision, SkipAheadAllocator allocator, HardPrerequisiteGraph graph) {

        throw new UnsupportedOperationException("P ainda não implementada");
    }

    private static int topicsScheduled(List<PlacedSession> placed) {
        return (int) placed.stream()
                .filter(session -> session.kind() == SessionKind.STUDY)
                .count();
    }

    /** Cópia de {@code GreedyBaselineScheduler.allocate}: para no primeiro tópico que não cabe. */
    private static List<PlacedSession> allocate(List<PlanRequest.Topic> order,
            RevisionPolicy revision, AvailabilityAllocator allocator) {

        List<PlacedSession> placed = new ArrayList<>();
        for (PlanRequest.Topic topic : order) {
            List<PlacedSession> ofTopic = placeTopic(topic, revision, allocator);
            if (ofTopic.isEmpty()) {
                break;
            }
            placed.addAll(ofTopic);
        }
        return List.copyOf(placed);
    }

    /** Cópia de {@code GreedyBaselineScheduler.placeTopic}. */
    private static List<PlacedSession> placeTopic(PlanRequest.Topic topic, RevisionPolicy revision,
            AvailabilityAllocator allocator) {

        Optional<Instant> study = allocator.place(topic.estimatedMinutes());
        if (study.isEmpty()) {
            return List.of();
        }
        PlacedSession studySession = new PlacedSession(topic.id(), SessionKind.STUDY, study.get(),
                topic.estimatedMinutes());
        if (!revision.needsRevision(topic.id())) {
            return List.of(studySession);
        }
        int minutes = RevisionPolicy.revisionMinutes(topic.estimatedMinutes());
        Optional<Instant> again = allocator.place(minutes);
        if (again.isEmpty()) {
            return List.of();
        }
        return List.of(studySession,
                new PlacedSession(topic.id(), SessionKind.REVISION, again.get(), minutes));
    }

    private static List<PlanResponse.ScheduledSession> sequence(List<PlacedSession> placed) {
        return IntStream.range(0, placed.size())
                .mapToObj(index -> new PlanResponse.ScheduledSession(placed.get(index).topicId(),
                        placed.get(index).kind(), placed.get(index).start(),
                        placed.get(index).durationMinutes(), index))
                .toList();
    }
}
