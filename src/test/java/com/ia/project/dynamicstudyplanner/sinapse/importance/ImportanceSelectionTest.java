package com.ia.project.dynamicstudyplanner.sinapse.importance;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.plan.PlanEngines;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os três critérios de aceitação da escolha de estratégia, verificados de ponta a ponta.
 *
 * <ol>
 *   <li>trocar de estratégia não exige recompilar nem reimplantar;</li>
 *   <li>duas execuções com estratégias diferentes e a mesma semente dão planos <b>diferentes</b> e
 *       <b>ambos reproduzíveis</b>;</li>
 *   <li>a resposta permite reconstruir qual estratégia rodou, <b>sem consultar configuração</b>.</li>
 * </ol>
 */
@DisplayName("Selecao de estrategia de importancia")
class ImportanceSelectionTest {

    /**
     * Um edital com cadeia de pré-requisitos e prioridades de meta <b>opostas</b>.
     *
     * <p>As metas dão prioridade 5 à disciplina dos tópicos 1 e 2 e prioridade 1 à dos 3 e 4; a
     * cadeia {@code 4 -> 3 -> 2 -> 1} faz a centralidade rankear na ordem inversa. Sem essa oposição
     * as duas estratégias poderiam concordar por acidente e o teste de "planos diferentes" passaria
     * sem medir nada.
     *
     * <p>E com calendário <b>folgado</b>. A disponibilidade nominal do fixture comporta um único
     * tópico, e num plano truncado a um tópico a importância não tem o que reordenar: as duas
     * estratégias coincidiriam e o teste passaria vacuamente. É a mesma armadilha registrada em
     * {@code docs/SINAPSE_ADAPTER.md} §3.8 — sem folga, não há decisão a tomar.
     */
    private static PlanRequests.Builder opposed() {
        List<PlanRequest.AvailabilitySlot> generosa = new java.util.ArrayList<>();
        for (int day = 1; day <= 12; day++) {
            String date = String.format(java.util.Locale.ROOT, "2026-09-%02d", day);
            generosa.add(PlanRequests.slot(date + "T09:00:00Z", date + "T13:00:00Z"));
        }
        return PlanRequests.builder()
                .withAvailability(List.copyOf(generosa))
                .withGoals(List.of(
                        PlanRequests.goal(PlanRequests.SUBJECT_FIRST, "2026-09-20", 5),
                        PlanRequests.goal(PlanRequests.SUBJECT_SECOND, "2026-10-15", 1)))
                .withPrerequisites(List.of(
                        PlanRequests.hard(PlanRequests.TOPIC_4, PlanRequests.TOPIC_3),
                        PlanRequests.hard(PlanRequests.TOPIC_3, PlanRequests.TOPIC_2),
                        PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_1)));
    }

    private static PlanRequest with(String strategyId, long seed) {
        PlanRequest base = opposed().withSeed(seed).build();
        Map<String, Object> params = new HashMap<>(base.algorithmParams());
        if (strategyId != null) {
            params.put(ImportanceStrategies.PARAM, strategyId);
        }
        return new PlanRequest(base.contractVersion(), base.horizon(), base.availability(),
                base.goals(), base.topics(), base.prerequisites(), base.history(),
                params, base.randomSeed());
    }

    private static String signature(PlanResponse response) {
        StringBuilder signature = new StringBuilder();
        response.sessions().forEach(session -> signature
                .append(session.sequenceIndex()).append(':')
                .append(session.topicId()).append(':')
                .append(session.scheduledStart()).append('|'));
        return signature.toString();
    }

    @Test
    @DisplayName("trocar de estrategia e so mudar a requisicao: nada e recompilado nem reimplantado")
    void trocarDeEstrategiaEhSoMudarARequisicao() {
        // O mesmo motor, a mesma instancia, o mesmo processo: so o payload muda.
        var engine = PlanEngines.genetic();

        assertThat(engine.plan(with(GoalPriorityImportance.ID, 7L)).fitness())
                .containsEntry(ImportanceStrategy.FITNESS_KEY, GoalPriorityImportance.ID);
        assertThat(engine.plan(with(PrerequisiteCentralityImportance.ID, 7L)).fitness())
                .containsEntry(ImportanceStrategy.FITNESS_KEY, PrerequisiteCentralityImportance.ID);
    }

    @Test
    @DisplayName("sem escolha na requisicao, roda a estrategia padrao configurada")
    void semEscolhaRodaAPadrao() {
        assertThat(PlanEngines.genetic().plan(with(null, 7L)).fitness())
                .as("o padrao e a substituta cujo significado uma decisao de produto ja endossou")
                .containsEntry(ImportanceStrategy.FITNESS_KEY, GoalPriorityImportance.ID);
    }

    @Test
    @DisplayName("estrategias diferentes com a mesma semente dao planos diferentes")
    void estrategiasDiferentesDaoPlanosDiferentes() {
        String comMetas = signature(PlanEngines.genetic().plan(with(GoalPriorityImportance.ID, 7L)));
        String comGrafo = signature(
                PlanEngines.genetic().plan(with(PrerequisiteCentralityImportance.ID, 7L)));

        assertThat(comGrafo)
                .as("mesma semente, mesmo motor: se os planos coincidissem, a escolha de estrategia "
                        + "nao estaria alcancando o termo de maior peso da fitness")
                .isNotEqualTo(comMetas);
    }

    @Test
    @DisplayName("e cada uma delas e reproduzivel")
    void cadaUmaEhReproduzivel() {
        for (String strategyId : List.of(GoalPriorityImportance.ID,
                PrerequisiteCentralityImportance.ID)) {
            String primeira = signature(PlanEngines.genetic().plan(with(strategyId, 7L)));
            String segunda = signature(PlanEngines.genetic().plan(with(strategyId, 7L)));

            assertThat(segunda)
                    .as("%s: planos diferentes nao servem de nada se nenhum dos dois se repete",
                            strategyId)
                    .isEqualTo(primeira);
        }
    }

    @Test
    @DisplayName("a resposta sozinha diz qual estrategia rodou, sem consultar configuracao")
    void aRespostaSozinhaDizQualEstrategiaRodou() {
        // O criterio decisivo: um resultado registrado tem de ser reconstruivel meses depois, quando
        // ninguem sabe o que a configuracao daquele deploy dizia. Importance alimenta metade da
        // fitness, entao sem esta chave o numero guardado nao significa nada.
        // Pelo seletor, como a plataforma chama: e ele que estampa "engine", e sao as tres chaves
        // juntas — motor, caminho e estrategia — que tornam a execucao reconstruivel.
        PlanResponse response = PlanEngines.all().get(1)
                .plan(with(PrerequisiteCentralityImportance.ID, 7L));

        assertThat(response.fitness())
                .containsEntry(ImportanceStrategy.FITNESS_KEY, PrerequisiteCentralityImportance.ID)
                .containsKey("engine")
                .containsKey("path");
    }

    @Test
    @DisplayName("uma estrategia desconhecida e recusada, nao trocada em silencio pelo padrao")
    void estrategiaDesconhecidaERecusada() {
        assertThatThrownBy(() -> PlanEngines.genetic().plan(with("pagerank", 7L)))
                .isInstanceOf(PlanRejectedException.class)
                .hasMessageContaining("pagerank")
                .hasMessageContaining(GoalPriorityImportance.ID)
                .hasMessageContaining(PrerequisiteCentralityImportance.ID);
    }

    @Test
    @DisplayName("um padrao que nenhuma estrategia atende falha no arranque")
    void padraoInvalidoFalhaNoArranque() {
        assertThatThrownBy(() -> new ImportanceStrategies(
                List.of(new GoalPriorityImportance()), "nao-existe"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plan.fitness.sinapse.importance-strategy")
                .hasMessageContaining("nao-existe");
    }
}
