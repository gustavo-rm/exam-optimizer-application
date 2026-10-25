package com.ia.project.dynamicstudyplanner.diagnostic;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator;
import com.ia.project.dynamicstudyplanner.plan.HardDependents;
import com.ia.project.dynamicstudyplanner.plan.HardPrerequisiteGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A opção D do EOA-12b: por que cada tópico ficou de fora. Só análise; não muda a colocação.
 *
 * <p>As categorias e a precedência estão em {@code docs/MEDICAO_ALOCADOR.md} §5, fixadas antes de
 * rodar. A — nunca cabe: {@code AvailabilityAllocator.over(pedido).place(estimatedMinutes)} vazio
 * numa agenda vazia. B — dependente {@code HARD} transitivo de um de A. C — nem A nem B, com C-dep
 * quando algum pré-requisito {@code HARD} transitivo também ficou de fora. D — nem A nem B, e a
 * resposta não o declara em {@code topics-unscheduled-ids}.
 */
final class AbsenceClassifier {

    private final PlanRequest request;
    private final HardPrerequisiteGraph graph;
    private final Set<UUID> neverFits;
    private final Set<UUID> dependentsOfNeverFits;

    private AbsenceClassifier(PlanRequest request, HardPrerequisiteGraph graph, Set<UUID> neverFits,
            Set<UUID> dependentsOfNeverFits) {

        this.request = request;
        this.graph = graph;
        this.neverFits = neverFits;
        this.dependentsOfNeverFits = dependentsOfNeverFits;
    }

    static AbsenceClassifier of(PlanRequest request, HardPrerequisiteGraph graph) {
        Set<UUID> never = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        request.topics().stream()
                .filter(topic -> AvailabilityAllocator.over(request).place(topic.estimatedMinutes()).isEmpty())
                .forEach(topic -> never.add(topic.id()));
        Set<UUID> dependents = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        never.forEach(id -> dependents.addAll(HardDependents.of(graph, id)));
        dependents.removeAll(never);
        return new AbsenceClassifier(request, graph, never, dependents);
    }

    /** A contagem de uma resposta, ou de uma recusa ({@code response == null}). */
    record Counts(int absent, int a, int b, int cRoot, int cDep, int d, int aScheduled) {

        static String header() {
            return "absent,absent_A,absent_B,absent_C_root,absent_C_dep,absent_D,A_scheduled";
        }

        String csv() {
            return absent + "," + a + "," + b + "," + cRoot + "," + cDep + "," + d + "," + aScheduled;
        }
    }

    Counts classify(PlanResponse response) {
        Set<UUID> scheduled = scheduled(response);
        Set<String> declared = declared(response);
        List<UUID> absent = request.topics().stream().map(PlanRequest.Topic::id)
                .filter(id -> !scheduled.contains(id))
                .toList();
        int[] counts = new int[5];
        for (UUID id : absent) {
            counts[category(id, scheduled, declared, response != null)]++;
        }
        int aScheduled = (int) neverFits.stream().filter(scheduled::contains).count();
        return new Counts(absent.size(), counts[0], counts[1], counts[2], counts[3], counts[4], aScheduled);
    }

    /** 0 = A, 1 = B, 2 = C-raiz, 3 = C-dep, 4 = D; na ordem de precedência. */
    private int category(UUID id, Set<UUID> scheduled, Set<String> declared, boolean accepted) {
        if (neverFits.contains(id)) {
            return 0;
        }
        if (dependentsOfNeverFits.contains(id)) {
            return 1;
        }
        if (accepted && !declared.contains(id.toString())) {
            return 4;
        }
        boolean prerequisiteAbsent = ancestors(id).stream().anyMatch(other -> !scheduled.contains(other));
        return prerequisiteAbsent ? 3 : 2;
    }

    /** Uma linha por tópico da instância, para a análise estática. */
    List<String> staticRows(String instance) {
        List<String> rows = new ArrayList<>();
        for (PlanRequest.Topic topic : request.topics()) {
            String category = neverFits.contains(topic.id()) ? "A"
                    : dependentsOfNeverFits.contains(topic.id()) ? "B" : "-";
            rows.add(String.join(",", instance, topic.id().toString(), Integer.toString(topic.position()),
                    Integer.toString(topic.estimatedMinutes()), topic.effortTier(), category,
                    Integer.toString(graph.prerequisitesOf(topic.id()).size()),
                    Integer.toString(graph.dependentsOf(topic.id()).size())));
        }
        return rows;
    }

    static String staticHeader() {
        return "instance,topic_id,position,estimated_minutes,effort_tier,static_category,"
                + "hard_prerequisites,hard_dependents";
    }

    int neverFitsCount() {
        return neverFits.size();
    }

    int dependentsCount() {
        return dependentsOfNeverFits.size();
    }

    /**
     * A maior duração que cabe numa agenda vazia, isto é, a maior janela como o alocador a vê.
     *
     * <p>Busca binária sobre {@code place}, porque as janelas recortadas são privadas do alocador.
     */
    int largestWindowMinutes() {
        int low = 0;
        int high = (int) Math.min(Integer.MAX_VALUE, AvailabilityAllocator.over(request).availableMinutes());
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (AvailabilityAllocator.over(request).place(middle).isPresent()) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private Set<UUID> ancestors(UUID id) {
        Set<UUID> reached = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        Deque<UUID> pending = new ArrayDeque<>(graph.prerequisitesOf(id));
        while (!pending.isEmpty()) {
            UUID next = pending.pop();
            if (reached.add(next)) {
                pending.addAll(graph.prerequisitesOf(next));
            }
        }
        return reached;
    }

    private static Set<UUID> scheduled(PlanResponse response) {
        Set<UUID> ids = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        if (response != null) {
            response.sessions().forEach(session -> ids.add(session.topicId()));
        }
        return ids;
    }

    private static Set<String> declared(PlanResponse response) {
        Set<String> ids = new TreeSet<>();
        if (response != null && response.fitness().get("topics-unscheduled-ids") instanceof Collection<?> list) {
            list.forEach(id -> ids.add(String.valueOf(id)));
        }
        return ids;
    }
}
