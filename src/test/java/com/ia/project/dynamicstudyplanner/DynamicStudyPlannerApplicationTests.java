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

    @Test
    @DisplayName("os objetivos de fitness fiados pelo Spring somam exatamente 1,0")
    void osPesosDosObjetivosFiadosPeloSpringSomamUm() {
        List<FitnessObjective> objetivos = context.getBeanProvider(FitnessObjective.class)
                .stream().toList();

        assertThat(objetivos)
                .as("os tres objetivos de producao precisam estar registrados como beans")
                .hasSize(3);

        double soma = objetivos.stream().mapToDouble(FitnessObjective::getWeight).sum();

        // Fecha a lacuna descrita em 01-diagnostico-testes.md §2.4: os testes de unidade remontam a
        // composicao a mao e afirmam por comentario que ela e igual a do Spring. Aqui a afirmacao e
        // feita sobre os beans reais.
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
