package com.ia.project.dynamicstudyplanner;

import com.ia.project.dynamicstudyplanner.api.mapper.FullPlannerResultMapper;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.usecase.GenerateStudyPlanUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garantias sobre a aplicação montada — o contexto do Spring e o artefato de implantação.
 *
 * <h2>O que havia aqui antes</h2>
 *
 * Um {@code contextLoads()} vazio, sem nenhuma asserção. O diagnóstico da etapa 01 §2.1(c) apontou
 * que ele <b>não</b> era inútil: como {@code FitnessEvaluator} é um {@code @Component} cujo construtor
 * valida se os pesos dos objetivos somam 1,0, subir o contexto já falharia se a composição de beans
 * de produção ficasse desbalanceada. O problema é que essa proteção era acidental — invisível para
 * quem lesse o teste, e perdida no dia em que alguém apagasse "o teste vazio".
 *
 * <p>A etapa 01b mantém a proteção e a torna explícita, e acrescenta a garantia sobre o jar.
 */
@SpringBootTest
@DisplayName("Aplicacao: contexto do Spring e artefato de implantacao")
class DynamicStudyPlannerApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private FitnessEvaluator fitnessEvaluator;

    @Test
    @DisplayName("o contexto sobe com a composicao de producao inteira")
    void contextLoads() {
        assertThat(context).isNotNull();

        // Estes são os beans do caminho de produção. Nomeá-los transforma "o contexto subiu" em
        // "o contexto subiu com o que o endpoint precisa".
        assertThat(context.getBean(GenerateStudyPlanUseCase.class)).isNotNull();
        assertThat(context.getBean(FullPlannerResultMapper.class)).isNotNull();
        assertThat(fitnessEvaluator)
                .as("o construtor do FitnessEvaluator valida a soma dos pesos: se o contexto subiu, "
                        + "a composicao de beans de producao passou por essa validacao")
                .isNotNull();
    }

    /**
     * Trava a composição de fitness que o Spring monta em produção — tipos <b>e</b> pesos.
     *
     * <h2>Por que os tipos, e não só a soma</h2>
     *
     * A etapa 03 registrou (achado E2) que a composição de produção é remontada à mão em seis
     * lugares fora dela: três programas de benchmark e três classes de teste. Diferente da cadeia de
     * alocação — que a etapa 03b unificou em {@code AllocationChains} —, essa duplicação é
     * <b>deliberada</b>: os benchmarks precisam construir variantes com pesos perturbados, que é
     * literalmente o que {@code WeightSensitivityMain} mede. Unificar removeria a capacidade de
     * variar.
     *
     * <p>O risco que sobra é outro: acrescentar um {@code FitnessObjective} novo em produção custa
     * uma anotação, o Spring o injeta sozinho, e as seis cópias continuam rodando sem ele — em
     * silêncio. Os números publicados em {@code docs/revisao-ag/} passariam a medir uma fitness que
     * não é a de produção.
     *
     * <p>Verificar a soma dos pesos não pega esse caso: um objetivo novo com peso 0,0 mantém a soma
     * em 1,0. Por isso este teste fixa também <b>quais</b> tipos compõem a fitness. Acrescentar,
     * remover ou trocar um objetivo falha aqui, e a mensagem manda atualizar as cópias.
     *
     * <p>Decisão registrada em {@code docs/adr/0003-composicao-de-producao-unica.md}.
     */
    @Test
    @DisplayName("a composicao de fitness fiada pelo Spring e exatamente a canonica")
    void aComposicaoDeFitnessFiadaPeloSpringEhACanonica() {
        List<FitnessObjective> objetivos = context.getBeanProvider(FitnessObjective.class)
                .stream().toList();

        assertThat(objetivos)
                .as("""
                        A composicao de fitness de producao mudou.

                        Ela e remontada a mao, de proposito, em seis lugares fora da producao:
                          benchmarks/.../harness/BenchmarkHarness
                          benchmarks/.../robustness/WeightSensitivityMain
                          benchmarks/.../robustness/WeightTradeoffMain
                          src/test/.../ga/IndividualTest
                          src/test/.../ga/GaEdgeCasesTest
                          src/test/.../ga/PrerequisiteSequencingDiagnosticTest

                        Atualize as seis no MESMO commit, ou os numeros publicados em
                        docs/revisao-ag/ passarao a medir uma fitness que nao e a de producao.""")
                .extracting(objetivo -> objetivo.getClass().getSimpleName())
                .containsExactlyInAnyOrder(
                        "ScoreGainObjective", "RetentionObjective", "CognitiveLoadObjective");

        double soma = objetivos.stream().mapToDouble(FitnessObjective::getWeight).sum();
        assertThat(soma)
                .as("a fitness agregada so e comparavel entre releases se os pesos somarem 1,0")
                .isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("a classe principal e empacotavel: public static void main")
    void aClassePrincipalEhEmpacotavel() throws NoSuchMethodException {
        // Trava um defeito real encontrado na etapa 01b: o main estava declarado sem `public`, e o
        // goal `repackage` do spring-boot-maven-plugin nao encontrava a classe principal. O
        // resultado era que `mvn package` falhava e o jar executavel descrito no README nao existia.
        //
        // Sem esta assercao, a unica coisa que pega a regressao e a etapa de empacotamento do CI —
        // que roda depois dos testes e custa muito mais tempo para dar o retorno.
        Method main = DynamicStudyPlannerApplication.class.getDeclaredMethod("main", String[].class);

        assertThat(Modifier.isPublic(main.getModifiers()))
                .as("repackage procura um main PUBLICO; sem isso o jar executavel nao e gerado")
                .isTrue();
        assertThat(Modifier.isStatic(main.getModifiers()))
                .as("repackage procura um main ESTATICO")
                .isTrue();
        assertThat(main.getReturnType()).isEqualTo(void.class);
    }
}
