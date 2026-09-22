package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Como o motor é escolhido, e por que um nome desconhecido é recusado.
 *
 * <h2>Escolhido por requisição, não por implantação</h2>
 *
 * As duas condições do experimento precisam rodar contra o mesmo <i>build</i>: comparar dois deploys
 * mede os deploys também. {@code algorithmParams} é {@code Map<String, Object>} no contrato —
 * conferido, não suposto — então a escolha cabe lá sem inventar campo nenhum.
 */
@DisplayName("Selecao de motor")
class PlanEngineSelectorTest {

    private static PlanRequest withParams(Map<String, Object> params) {
        PlanRequest base = PlanRequests.builder().build();
        return new PlanRequest(base.contractVersion(), base.horizon(), base.availability(),
                base.goals(), base.topics(), base.prerequisites(), base.history(),
                params, base.randomSeed());
    }

    @Test
    @DisplayName("sem escolha na requisicao, roda o motor padrao configurado")
    void semEscolhaRodaOPadrao() {
        assertThat(PlanEngines.selector().resolveId(withParams(Map.of())))
                .isEqualTo(GreedyBaselineEngine.ID);
    }

    @Test
    @DisplayName("a escolha da requisicao vence o padrao")
    void aEscolhaVenceOPadrao() {
        assertThat(PlanEngines.selector()
                .resolveId(withParams(Map.of(PlanEngineSelector.ENGINE_PARAM, GeneticPlanEngine.ID))))
                .isEqualTo(GeneticPlanEngine.ID);
    }

    @Test
    @DisplayName("um motor desconhecido e recusado com 422, nao silenciosamente trocado pelo padrao")
    void motorDesconhecidoERecusado() {
        // Cair no padrao rodaria a condicao A enquanto quem chamou registrou a condicao B, e as
        // medicoes sairiam erradas de um jeito que nada a jusante conseguiria detectar.
        assertThatThrownBy(() -> PlanEngines.selector()
                .plan(withParams(Map.of(PlanEngineSelector.ENGINE_PARAM, "quantum-annealing"))))
                .isInstanceOf(PlanRejectedException.class)
                .hasMessageContaining("quantum-annealing")
                .hasMessageContaining(GreedyBaselineEngine.ID)
                .hasMessageContaining(GeneticPlanEngine.ID);
    }

    @Test
    @DisplayName("um valor que nao e texto sob a chave tambem e recusado")
    void valorNaoTextualERecusado() {
        Map<String, Object> params = new HashMap<>();
        params.put(PlanEngineSelector.ENGINE_PARAM, 42);

        assertThatThrownBy(() -> PlanEngines.selector().plan(withParams(params)))
                .isInstanceOf(PlanRejectedException.class)
                .hasMessageContaining("must be a string");
    }

    @Test
    @DisplayName("um padrao que nenhum motor atende falha no arranque, nao na primeira requisicao")
    void padraoInvalidoFalhaNoArranque() {
        assertThatThrownBy(() -> new PlanEngineSelector(
                List.of(PlanEngines.greedy()), "nao-existe"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plan.engine.default")
                .hasMessageContaining("nao-existe");
    }

    @Test
    @DisplayName("dois motores com o mesmo id falham no arranque")
    void idsDuplicadosFalhamNoArranque() {
        // O id e como um plano guardado e atribuido a uma condicao do experimento meses depois.
        assertThatThrownBy(() -> new PlanEngineSelector(
                List.of(PlanEngines.greedy(), PlanEngines.greedy()), GreedyBaselineEngine.ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("answer to the id");
    }

    @Test
    @DisplayName("a resposta sempre declara o motor, mesmo se o motor nao o declarar")
    void aRespostaSempreDeclaraOMotor() {
        assertThat(PlanEngines.selector().plan(withParams(Map.of())).fitness())
                .containsEntry(PlanEngine.FITNESS_ENGINE_KEY, GreedyBaselineEngine.ID);
    }
}
