package com.ia.project.dynamicstudyplanner.plan;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * O fecho transitivo dos dependentes {@code HARD} de um tópico, para a regra P4.
 */
public final class HardDependents {

    private HardDependents() {
    }

    /**
     * Todos os tópicos que dependem, direta ou indiretamente, de {@code root}.
     *
     * @param graph o grafo da condição de proveniência do pedido
     * @param root  o tópico pulado
     * @return os dependentes transitivos, sem o próprio {@code root}
     */
    public static Set<UUID> of(HardPrerequisiteGraph graph, UUID root) {
        Set<UUID> reached = new TreeSet<>(HardPrerequisiteGraph.BY_TEXT);
        Deque<UUID> pending = new ArrayDeque<>(graph.dependentsOf(root));
        while (!pending.isEmpty()) {
            UUID next = pending.pop();
            if (reached.add(next)) {
                pending.addAll(graph.dependentsOf(next));
            }
        }
        reached.remove(root);
        return reached;
    }
}
