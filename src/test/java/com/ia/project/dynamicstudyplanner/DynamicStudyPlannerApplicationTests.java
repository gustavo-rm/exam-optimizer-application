package com.ia.project.dynamicstudyplanner;

import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.plan.PlanController;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.sinapse.DailyLoadBudgetObjective;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

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
 * que ele <b>não</b> era inútil: como o construtor de {@code FitnessEvaluator} valida se os pesos
 * dos objetivos somam 1,0, subir o contexto já falharia se a composição de produção ficasse
 * desbalanceada. O problema é que essa proteção era acidental — invisível para quem lesse o teste, e
 * perdida no dia em que alguém apagasse "o teste vazio".
 *
 * <h2>Por que o profile está ligado</h2>
 *
 * Desde EOA-4b, {@code /plans} é a única coisa que esta aplicação serve, e ele vive sob
 * {@code baseline-core}. Sem o profile o contexto sobe sem <b>nenhum</b> endpoint e sem nenhuma
 * composição de fitness, e "o contexto subiu" deixaria de significar "subiu com o que o serviço
 * precisa". Que ele também sobe sem o profile, e que {@code /plans} então responde {@code 404}, é o
 * que {@code baseline/BaselinePlanAbsentTest} cobre.
 */
@SpringBootTest
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("Aplicacao: contexto do Spring e artefato de implantacao")
class DynamicStudyPlannerApplicationTests {

    /**
     * Objetivos que existem como bean e <b>não</b> entram em composição nenhuma.
     *
     * <p>Vazia, e é para continuar vazia: um {@code FitnessObjective} que é bean e não é avaliado
     * por ninguém aparenta estar ativo e não está. {@code CognitiveLoadObjective} esteve aqui por
     * um instante durante EOA-4b e foi removido — ele lia {@code context.cognitiveLoad()}, que só o
     * caminho de concurso preenchia, e {@code DailyLoadBudgetObjective} o substituiu na escala que
     * a plataforma de fato envia.
     *
     * <p>Acrescentar um nome aqui exige escrever por que ele existe sem compor nada.
     */
    private static final List<String> OBJETIVOS_SEM_COMPOSICAO = List.of();

    @Autowired
    private ApplicationContext context;

    @Autowired
    @Qualifier("sinapseFitnessComposition")
    private FitnessComposition composicaoDeProducao;

    @Test
    @DisplayName("o contexto sobe com a composicao de producao inteira")
    void contextLoads() {
        assertThat(context).isNotNull();

        // Estes sao os beans do caminho de producao. Nomea-los transforma "o contexto subiu" em
        // "o contexto subiu com o que o endpoint precisa".
        assertThat(context.getBean(PlanController.class)).isNotNull();
        assertThat(context.getBean(PlanEngineSelector.class)).isNotNull();
        assertThat(context.getBean(GreedyBaselineEngine.class)).isNotNull();
        assertThat(context.getBean(GeneticPlanEngine.class)).isNotNull();
        assertThat(composicaoDeProducao.evaluator())
                .as("o construtor do FitnessEvaluator valida a soma dos pesos: se ele foi criado, "
                        + "a composicao de producao passou por essa validacao")
                .isNotNull();
    }

    /**
     * Trava a composição de fitness que o Spring monta em produção — tipos <b>e</b> pesos.
     *
     * <h2>Por que os tipos, e não só a soma</h2>
     *
     * Acrescentar um {@code FitnessObjective} novo custa uma anotação, e o Spring o cria sozinho.
     * Antes de EOA-4b o risco era que as cópias da composição feitas à mão nos benchmarks e nos
     * testes continuassem rodando sem ele; hoje o risco é o oposto e pior: a composição de produção
     * é uma lista explícita, então um objetivo novo <b>não</b> entra nela, e nada avisaria que ele
     * existe como bean sem ser avaliado por ninguém.
     *
     * <p>Verificar a soma dos pesos não pega esse caso — um objetivo fora da composição não soma
     * nada. Por isso este teste confere as duas listas: <b>quais</b> tipos compõem a fitness, e
     * quais objetivos existem no contexto sem compor nada. Acrescentar um objetivo falha aqui até
     * que alguém escreva em qual das duas listas ele entra.
     *
     * <p>Decisão registrada em {@code docs/adr/0003-composicao-de-producao-unica.md}.
     */
    @Test
    @DisplayName("a composicao de fitness fiada pelo Spring e exatamente a canonica")
    void aComposicaoDeFitnessFiadaPeloSpringEhACanonica() {
        assertThat(composicaoDeProducao.objectives())
                .as("""
                        A composicao de fitness de producao mudou.

                        Ela e declarada em sinapse/SinapseFitnessConfig e e a unica que roda:
                        o caminho de concurso, com a sua, saiu em EOA-4b.""")
                .extracting(FitnessObjective::name)
                .containsExactly("syllabusMastery", "retention", DailyLoadBudgetObjective.NAME);

        double soma = composicaoDeProducao.objectives().stream()
                .mapToDouble(FitnessObjective::getWeight).sum();
        assertThat(soma)
                .as("a fitness agregada so e comparavel entre releases se os pesos somarem 1,0")
                .isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("nenhum objetivo existe como bean sem entrar numa composicao ou na lista de excecoes")
    void nenhumObjetivoFicaForaSemRegistro() {
        List<String> naComposicao = composicaoDeProducao.objectives().stream()
                .map(FitnessObjective::name).toList();

        assertThat(context.getBeanProvider(FitnessObjective.class).stream()
                .map(FitnessObjective::name).toList())
                .as("""
                        Um FitnessObjective novo apareceu no contexto.

                        Ou ele entra na composicao de producao (sinapse/SinapseFitnessConfig), ou
                        ele entra em OBJETIVOS_SEM_COMPOSICAO com a razao escrita. Um objetivo que
                        e bean e nao e avaliado por ninguem aparenta estar ativo e nao esta.""")
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.Stream.concat(naComposicao.stream(),
                                OBJETIVOS_SEM_COMPOSICAO.stream()).toList());
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
