package com.ia.project.dynamicstudyplanner.benchmark.instance;

import java.util.Map;
import java.util.UUID;

/**
 * Uma instância da medição de curadoria: o pedido, as coordenadas, e o que o gerador sabe e o pedido
 * pode não dizer.
 *
 * <h2>A faixa latente</h2>
 *
 * No nível {@link EffortMapping#UNIFORM} o pedido manda todo tópico como {@code STANDARD}. A faixa
 * que o curador <i>teria</i> anotado continua existindo na estrutura, e é guardada aqui, para que o
 * relatório possa perguntar "que tipo de tópico ficou de fora" nos dois níveis com a mesma régua.
 *
 * @param point          as coordenadas
 * @param benchmark      o pedido, na forma que o harness existente já mede
 * @param latentTier     tópico {@literal ->} índice em {@link EffortMapping#TIERS}, a faixa da estrutura
 * @param edges          quantas arestas o pedido carrega (as mesmas em todo nível de força)
 * @param hardEdges      quantas delas são {@code HARD} neste nível
 * @param backwardEdges  quantas apontam contra a posição curricular
 * @param crossEdges     quantas ligam disciplinas diferentes
 */
public record CurationInstance(
        CurationPoint point,
        BenchmarkInstance benchmark,
        Map<UUID, Integer> latentTier,
        int edges,
        int hardEdges,
        int backwardEdges,
        int crossEdges) {
}
