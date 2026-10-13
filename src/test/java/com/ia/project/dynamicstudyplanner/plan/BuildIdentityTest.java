package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.GitProperties;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code fitness.build}: de onde vem o valor, e que ele sai em toda resposta do seletor.
 */
@DisplayName("Identidade do build em fitness.build")
class BuildIdentityTest {

    private static final String SHA = "3e0a71f8ff4159bfcda6e1e232dfb89db4ab2c15";

    private static GitProperties git(String... chavesEValores) {
        Properties entries = new Properties();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            entries.setProperty(chavesEValores[i], chavesEValores[i + 1]);
        }
        return new GitProperties(entries);
    }

    @Test
    @DisplayName("um build limpo e identificado pelo SHA completo do commit")
    void buildLimpoEhOSha() {
        assertThat(BuildIdentity.from(git("commit.id", SHA, "dirty", "false")).id()).isEqualTo(SHA);
    }

    @Test
    @DisplayName("um build com mudancas nao commitadas nao finge ser o commit: leva o sufixo -dirty")
    void buildSujoLevaSufixo() {
        assertThat(BuildIdentity.from(git("commit.id", SHA, "dirty", "true")).id())
                .isEqualTo(SHA + BuildIdentity.DIRTY_SUFFIX);
    }

    @Test
    @DisplayName("sem git.properties, ou com ele vazio, o valor e unknown e a aplicacao sobe")
    void semCommitEhUnknown() {
        assertThat(BuildIdentity.from(null).id()).isEqualTo(BuildIdentity.UNKNOWN);
        // Um build sem .git gera o arquivo so com o comentario de cabecalho: medido no EOA-13.
        assertThat(BuildIdentity.from(git()).id()).isEqualTo(BuildIdentity.UNKNOWN);
    }

    @Test
    @DisplayName("o seletor estampa o build em toda resposta, ao lado do motor")
    void oSeletorEstampaOBuild() {
        PlanResponse resposta = PlanEngines.selector().plan(PlanRequests.builder().build());

        assertThat(resposta.fitness())
                .containsEntry(BuildIdentity.FITNESS_KEY, PlanEngines.BUILD.id())
                .containsEntry(PlanEngine.FITNESS_ENGINE_KEY, GreedyBaselineEngine.ID);
    }

    @Test
    @DisplayName("um motor nao pode declarar o proprio build: o seletor sobrescreve, e o resto fica")
    void oMotorNaoDeclaraOBuild() {
        PlanEngine mentiroso = new PlanEngine() {
            @Override
            public String id() {
                return "mentiroso";
            }

            @Override
            public PlanResponse plan(PlanRequest request) {
                PlanResponse real = PlanEngines.greedy().plan(request);
                return new PlanResponse(real.contractVersion(), real.sessions(),
                        Map.of(BuildIdentity.FITNESS_KEY, "outro-build",
                                PlanEngine.FITNESS_ENGINE_KEY, "outro-motor", "termo", 1),
                        real.metadata());
            }
        };
        PlanEngineSelector selector = new PlanEngineSelector(List.of(mentiroso), "mentiroso",
                PlanEngines.paramsLog(), PlanEngines.BUILD);

        assertThat(selector.plan(PlanRequests.builder().build()).fitness())
                .containsEntry(BuildIdentity.FITNESS_KEY, PlanEngines.BUILD.id())
                .containsEntry(PlanEngine.FITNESS_ENGINE_KEY, "mentiroso")
                .containsEntry("termo", 1);
    }
}
