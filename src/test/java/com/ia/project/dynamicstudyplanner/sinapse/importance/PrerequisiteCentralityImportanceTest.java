package com.ia.project.dynamicstudyplanner.sinapse.importance;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteCycles;
import com.ia.project.dynamicstudyplanner.sinapse.TopicPlanningItems;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A centralidade: o que ela conta, o que ela recusa contar, e por que não depende de ordem.
 */
@DisplayName("Centralidade de pre-requisitos como importancia")
class PrerequisiteCentralityImportanceTest {

    private final PrerequisiteCentralityImportance strategy = new PrerequisiteCentralityImportance();

    private double importanceOf(PlanRequest request, int topicIndex) {
        Map<PlanningItem, Double> importance = strategy.importanceOf(request);
        return importance.get(TopicPlanningItems.toItem(request.topics().get(topicIndex)));
    }

    /** Uma cadeia 1 -> 2 -> 3 -> 4: o primeiro sustenta tres, o ultimo nao sustenta nada. */
    private static PlanRequest chain() {
        return PlanRequests.builder()
                .withPrerequisites(List.of(
                        PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                        PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_3),
                        PlanRequests.hard(PlanRequests.TOPIC_3, PlanRequests.TOPIC_4)))
                .build();
    }

    @Nested
    @DisplayName("o que a contagem significa")
    class OQueAContagemSignifica {

        @Test
        @DisplayName("conta dependentes TRANSITIVOS, nao so diretos")
        void contaDependentesTransitivos() {
            // A distincao inteira da estrategia. Contando so os diretos, todo topico da cadeia
            // valeria o mesmo (1 dependente) e o sinal desapareceria.
            PlanRequest chain = chain();

            assertThat(importanceOf(chain, 0)).as("topico 1 sustenta 2, 3 e 4").isEqualTo(4.0);
            assertThat(importanceOf(chain, 1)).as("topico 2 sustenta 3 e 4").isEqualTo(3.0);
            assertThat(importanceOf(chain, 2)).as("topico 3 sustenta 4").isEqualTo(2.0);
            assertThat(importanceOf(chain, 3)).as("topico 4 nao sustenta nada").isEqualTo(1.0);
        }

        @Test
        @DisplayName("arestas SOFT nao contam: elas nao restringem, entao nao sinalizam")
        void arestasSoftNaoContam() {
            PlanRequest soft = PlanRequests.builder()
                    .withPrerequisites(List.of(
                            PlanRequests.soft(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                            PlanRequests.soft(PlanRequests.TOPIC_2, PlanRequests.TOPIC_3)))
                    .build();

            assertThat(strategy.importanceOf(soft).values())
                    .as("uma preferencia de ordem nao deixa nenhum topico encalhado")
                    .containsOnly(PrerequisiteCentralityImportance.LEAF_FLOOR);
        }

        @Test
        @DisplayName("sem arestas HARD, todo topico vale o piso e a normalizacao fica uniforme")
        void semArestasTodoTopicoValeOPiso() {
            PlanRequest none = PlanRequests.builder().withPrerequisites(List.of()).build();

            assertThat(strategy.importanceOf(none).values())
                    .containsOnly(PrerequisiteCentralityImportance.LEAF_FLOOR);
        }

        @Test
        @DisplayName("um topico que varios sustentam conta cada dependente uma vez so")
        void contaCadaDependenteUmaVezSo() {
            // Diamante: 1 -> 2, 1 -> 3, 2 -> 4, 3 -> 4. O topico 4 e alcancavel por dois caminhos
            // a partir do 1, e continua sendo UM dependente.
            PlanRequest diamond = PlanRequests.builder()
                    .withPrerequisites(List.of(
                            PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                            PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_3),
                            PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_4),
                            PlanRequests.hard(PlanRequests.TOPIC_3, PlanRequests.TOPIC_4)))
                    .build();

            assertThat(importanceOf(diamond, 0))
                    .as("2, 3 e 4 — e o 4 uma vez, nao duas")
                    .isEqualTo(4.0);
        }

        @Test
        @DisplayName("aresta com ponta fora do escopo nao conta: seria contar um salto e parar")
        void arestaComPontaForaDoEscopoNaoConta() {
            // A plataforma envia arestas que tocam as disciplinas planejadas, inclusive com uma
            // ponta fora. Conta-las alcancaria exatamente um salto alem da fronteira e pararia —
            // nao um fecho menor, um fecho ENVIESADO, inflando quem estiver na borda do escopo.
            PlanRequest withOutside = PlanRequests.builder()
                    .withPrerequisites(List.of(
                            PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_OUTSIDE),
                            PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2)))
                    .build();

            assertThat(importanceOf(withOutside, 0))
                    .as("so o topico 2 conta; o de fora nao e comparavel com os de dentro")
                    .isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("determinismo")
    class Determinismo {

        @Test
        @DisplayName("o mesmo grafo embaralhado em ordens diferentes da os mesmos valores")
        void oMesmoGrafoEmbaralhadoDaOsMesmosValores() {
            List<PlanRequest.PrerequisiteEdge> edges = new ArrayList<>(List.of(
                    PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                    PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_3),
                    PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_4),
                    PlanRequests.hard(PlanRequests.TOPIC_3, PlanRequests.TOPIC_4)));

            Map<PlanningItem, Double> reference = strategy.importanceOf(
                    PlanRequests.builder().withPrerequisites(List.copyOf(edges)).build());

            for (long seed = 1; seed <= 25; seed++) {
                Collections.shuffle(edges, new Random(seed));
                Map<PlanningItem, Double> shuffled = strategy.importanceOf(
                        PlanRequests.builder().withPrerequisites(List.copyOf(edges)).build());

                assertThat(shuffled)
                        .as("embaralhamento %d: a contagem e a cardinalidade de um conjunto, entao "
                                + "a ordem das arestas nao pode alcanca-la", seed)
                        .containsExactlyEntriesOf(reference);
            }
        }

        @Test
        @DisplayName("a ordem de iteracao do mapa e a de chegada dos topicos, nao a do hash")
        void aOrdemDoMapaEhADeChegada() {
            // A ordem do mapa entra na soma de ponto flutuante de EvolutionContext.normalize, entao
            // ela e aritmetica e nao cosmetica. Ver SinapseAdapterIsolationTest.
            PlanRequest request = chain();

            assertThat(strategy.importanceOf(request).keySet())
                    .containsExactlyElementsOf(
                            request.topics().stream().map(TopicPlanningItems::toItem).toList());
        }
    }

    @Nested
    @DisplayName("ciclo HARD")
    class CicloHard {

        @Test
        @DisplayName("e recusado como em EOA-2: falha declarada, nomeando os topicos, nao um laco")
        void eRecusadoComoEmEoa2() {
            // Dentro de um ciclo "quantos dependem deste" nao tem resposta: todos dependem de todos,
            // inclusive de si mesmos. A travessia terminaria — o conjunto de visitados torna a
            // alcancabilidade segura — e devolveria um numero sem significado.
            PlanRequest cyclic = PlanRequests.builder()
                    .withPrerequisites(List.of(
                            PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                            PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_3),
                            PlanRequests.hard(PlanRequests.TOPIC_3, PlanRequests.TOPIC_1)))
                    .build();

            assertThatThrownBy(() -> strategy.importanceOf(cyclic))
                    .isInstanceOf(PlanRejectedException.class)
                    .extracting(thrown -> ((PlanRejectedException) thrown).reason())
                    .isEqualTo(PrerequisiteCycles.REASON);
        }

        @Test
        @DisplayName("a recusa nomeia os topicos do ciclo, para o payload poder ser corrigido")
        void aRecusaNomeiaOsTopicos() {
            PlanRequest cyclic = PlanRequests.builder()
                    .withPrerequisites(List.of(
                            PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                            PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_1)))
                    .build();

            assertThatThrownBy(() -> strategy.importanceOf(cyclic))
                    .isInstanceOf(PlanRejectedException.class)
                    .extracting(thrown -> ((PlanRejectedException) thrown).offending())
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                    .contains(PlanRequests.TOPIC_1.toString(), PlanRequests.TOPIC_2.toString());
        }

        @Test
        @DisplayName("a outra estrategia nao depende do grafo e planeja o mesmo pedido")
        void aOutraEstrategiaPlanejaOMesmoPedido() {
            // Registra que a recusa e da centralidade, nao do pedido: goal-priority nao le arestas.
            PlanRequest cyclic = PlanRequests.builder()
                    .withPrerequisites(List.of(
                            PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                            PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_1)))
                    .build();

            assertThat(new GoalPriorityImportance().importanceOf(cyclic)).isNotEmpty();
        }
    }
}
