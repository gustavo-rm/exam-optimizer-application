package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights;
import com.ia.project.dynamicstudyplanner.plan.PlanEngines;
import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A fitness publicada no caminho SINAPSE lista os termos ativos — e só eles.
 *
 * <h2>O que este arquivo existe para impedir</h2>
 *
 * Um termo desativado reportado no seu valor neutro. {@code "fatigue-penalty": 1.0} seria aritmética
 * verdadeira e afirmação falsa: diria que a função olhou a fadiga e não achou problema, quando ela
 * não tem dado de fadiga nenhum. <b>Um termo inerte que aparenta estar ativo é pior que um termo
 * ausente</b>, porque o ausente é uma limitação documentada e o inerte é uma alegação — e uma que
 * favorece o sistema.
 *
 * <p>As asserções são de <b>ausência de chave</b>, deliberadamente. Uma asserção de "valor não é
 * 1.0" passaria com 0,999 e não diria nada sobre a chave existir.
 */
@DisplayName("Fitness SINAPSE: so os termos ativos aparecem")
class SinapseFitnessTermsTest {

    private static Map<String, Object> fitnessOf() {
        PlanResponse response = PlanEngines.genetic().plan(PlanRequests.builder().build());
        return response.fitness();
    }

    @Test
    @DisplayName("as duas penalidades desativadas nao aparecem, nem com valor neutro")
    void asPenalidadesDesativadasNaoAparecem() {
        Map<String, Object> fitness = fitnessOf();

        assertThat(fitness.keySet())
                .as("nenhuma chave de penalidade: a composicao deste caminho nao declara nenhuma")
                .noneSatisfy(key -> assertThat(key).startsWith("penalty."));
        assertThat(fitness.keySet())
                .as("e em particular nao estas duas, que exigem estado psicologico e historico de "
                        + "engajamento — dado que a plataforma nao coleta")
                .noneSatisfy(key -> assertThat(key).containsIgnoringCase("fatigue"))
                .noneSatisfy(key -> assertThat(key).containsIgnoringCase("dropout"));
    }

    @Test
    @DisplayName("os tres objetivos ativos aparecem sob o nome NOVO do termo de carga")
    void osTresObjetivosAparecemComSeusPesos() {
        Map<String, Object> fitness = fitnessOf();

        assertThat(fitness)
                .containsKey("objective.syllabusMastery.weight")
                .containsKey("objective.retention.weight")
                .containsKey("objective." + DailyLoadBudgetObjective.NAME + ".weight");

        // E o nome ANTIGO nao aparece. O termo perdeu dois dos tres insumos, entao "cognitiveLoad"
        // — com a citacao de Sweller que o acompanha — descreveria um modelo que este nao e. Uma
        // execucao arquivada tem de ser autodescritiva um ano depois: quem ler "cognitiveLoad" num
        // FitnessBreakdown vai supor insumos que nunca estiveram la.
        assertThat(fitness.keySet())
                .as("o termo foi renomeado; o nome antigo nao pode sobreviver no relatorio")
                .noneSatisfy(key -> assertThat(key).containsIgnoringCase("cognitiveLoad"));

        double sum = FitnessWeights.SYLLABUS_MASTERY + FitnessWeights.RETENTION
                + FitnessWeights.COGNITIVE_LOAD;
        assertThat(sum)
                .as("os pesos nao mudaram: as duas penalidades sao fatores multiplicativos, nao "
                        + "somandos, entao remove-las nao exige renormalizacao alguma")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("o caminho declara qual composicao rodou")
    void oCaminhoDeclaraQualComposicaoRodou() {
        assertThat(fitnessOf())
                .containsEntry("path", SinapseFitnessConfig.PATH);
    }

}
