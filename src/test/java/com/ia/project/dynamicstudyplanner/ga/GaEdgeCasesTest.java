package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import com.ia.project.dynamicstudyplanner.ga.factory.StudyPlanFactory;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.support.TopicPlans;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos de borda e garantias de robustez do otimizador, de
 * {@code docs/revisao-ag/04-robustez.md}.
 *
 * <p>Cada teste trava um comportamento que foi corrigido ou explicitamente mantido.
 *
 * <h2>O que mudou em EOA-4b</h2>
 *
 * As instâncias deixaram de ser {@code Exam} com {@code Subject} e passaram a ser
 * {@link PlanRequest} com tópicos, pelo montador de produção — ver {@code support/TopicPlans}. Três
 * casos saíram com o caminho de concurso porque mediam código que saiu junto, e não porque a
 * propriedade deixou de valer:
 *
 * <ul>
 *   <li>disponibilidade semanal negativa recusada na construção do perfil — era validação de
 *       {@code StudentProfile}; no caminho que ficou a recusa é de {@code PlanRequestGuard}, e
 *       {@code plan/PlanControllerErrorMappingTest} a cobre;</li>
 *   <li>disponibilidade zero e data da prova no passado devolvendo cronograma vazio — eram
 *       pendências P1 e P2 sobre {@code StudyScheduleGenerator}, que não existe mais; o caminho
 *       atual recusa com {@code 422} em vez de devolver plano vazio, o que é a decisão que as duas
 *       pendências pediam;</li>
 *   <li>o piso de dias mínimos crescendo com o número de disciplinas (pendência P5) — media
 *       {@code BaselineCalculator}, substituído por {@code SinapseMinimumDays}, cujo piso vem de
 *       {@code estimatedMinutes} sobre o dia de estudo medido e não escala com a contagem.</li>
 * </ul>
 */
@DisplayName("Casos de borda e robustez do AG")
class GaEdgeCasesTest {

    @AfterEach
    void restoreProductionRandomness() {
        // Os testes abaixo fixam a semente; deixar uma semente fixa instalada vazaria para outros
        // testes, porque RandomProvider guarda estado global.
        RandomProvider.setInstance(new SecureRandom());
    }

    // ------------------------------------------------------------------
    // Entradas inviáveis têm que falhar alto e dizer por quê
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Entradas inviaveis")
    class InfeasibleInputs {

        @Test
        @DisplayName("orcamento menor que o piso de dias minimos falha com mensagem acionavel")
        void budgetBelowMinimumDaysFloorFails() {
            EvolutionContext context = TopicPlans.context(10);

            // Passou a ser DomainException na etapa 03d: o pedido e bem formado e compreendido, e
            // sao os topicos que exigem mais dias do que o orcamento tem. O cliente recebe 422, nao
            // 400. Ver ADR-0005 e docs/qualidade/03d-correcao-contrato-de-erro.md.
            assertThatThrownBy(() -> TopicPlans.optimize(context, 5, 20, 10))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Total minimum study days required")
                    .hasMessageContaining("exceeds total available days");
        }

        @Test
        @DisplayName("instancia sem itens falha com mensagem de dominio, nao com erro interno do JDK")
        void instanceWithNoItemsFailsClearly() {
            // Antes da correcao isto aparecia como o "bound must be positive" do java.util.Random,
            // que nao diz nada sobre o problema real. Ver 04-robustez.md, correcao C2.
            StudyPlanFactory factory = new StudyPlanFactory();

            // Idem: regra de negocio sobre a instancia, nao argumento malformado (etapa 03d).
            assertThatThrownBy(() -> factory.createRandomPlan(null, List.of(), 10, Map.of()))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("no planning items")
                    .hasMessageNotContaining("bound must be positive");
        }

        @Test
        @DisplayName("orcamento negativo e rejeitado")
        void negativeBudgetIsRejected() {
            PlanningItem item = new PlanningItem("Math", "Math", 3);
            StudyPlanFactory factory = new StudyPlanFactory();

            assertThatThrownBy(() -> factory.createRandomPlan(null, List.of(item), -5, Map.of(item, 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be negative");
        }
    }

    // ------------------------------------------------------------------
    // Entradas degeneradas porém legais ainda têm que produzir plano válido
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Entradas degeneradas porem legais")
    class DegenerateInputs {

        @Test
        @DisplayName("um unico topico produz um plano valido")
        void singleItemProducesValidPlan() {
            EvolutionContext context = TopicPlans.context(1);

            StudyPlan plan = TopicPlans.optimize(context, 100, 30, 20);

            assertThat(plan.getTotalDays()).isEqualTo(100);
            assertThat(plan.getDaysPerItem()).hasSize(1);
            assertThat(context.fitnessEvaluator().evaluate(plan, context)).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("mutacao com um unico topico devolve o individuo intacto, sem laco infinito")
        void mutationWithSingleItemIsANoOp() {
            // AbstractMutationStrategy.mutate para curto abaixo de dois itens. Sem essa guarda,
            // randomItemExcluding giraria para sempre procurando um item diferente; a guarda desta
            // etapa agora tambem lanca, em vez de travar, se algum dia for alcancada.
            PlanningItem only = new PlanningItem("Only", "Only", 3);
            Individual individual = new Individual(new StudyPlan(Map.of(only, 40)));
            EvolutionContext context = EvolutionContext.builder()
                    .importanceScores(Map.of(only, 5.0))
                    .minimumDaysPerItem(Map.of(only, 1))
                    .planningHorizonDays(180)
                    .hoursPerStudyDay(4)
                    .maxDailyCognitiveLoad(20)
                    .build();

            Individual mutated = new CreepMutation().mutate(individual, 1.0, context);

            assertThat(mutated).isSameAs(individual);
        }
    }

    // ------------------------------------------------------------------
    // Reprodutibilidade
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Determinismo")
    class Determinism {

        @Test
        @DisplayName("mesma seed produz plano identico")
        void sameSeedProducesIdenticalPlan() {
            // E a garantia de que a etapa de robustez inteira tratava. Antes das correcoes o AG
            // sorteava de Math.random() e ThreadLocalRandom, e cada operador capturava
            // RandomProvider.getInstance() num campo na construcao, entao uma semente instalada
            // depois nao alcancava quase nada. Reprodutivel em 2 de 8 instancias; agora 8 de 8.
            EvolutionContext context = TopicPlans.context(12);

            RandomProvider.setInstance(new Random(20260830L));
            StudyPlan first = TopicPlans.optimize(context, 240, 60, 30);

            RandomProvider.setInstance(new Random(20260830L));
            StudyPlan second = TopicPlans.optimize(context, 240, 60, 30);

            assertThat(TopicPlans.signature(second))
                    .as("a mesma seed deve reproduzir exatamente a mesma alocacao")
                    .isEqualTo(TopicPlans.signature(first));
        }

        @Test
        @DisplayName("seeds diferentes produzem planos diferentes (o AG continua estocastico)")
        void differentSeedsProduceDifferentPlans() {
            // Complemento do anterior: a correcao nao pode ter colapsado a busca numa constante.
            // Um AG cuja saida nao depende da semente nao esta buscando.
            //
            // A instancia tem faixas de esforco e duracoes variadas de proposito: com topicos de
            // peso uniforme o otimo e uma divisao igual que toda semente alcanca, os planos
            // coincidiriam legitimamente e o teste nao diria nada sobre estocasticidade.
            EvolutionContext context = TopicPlans.context(12);

            RandomProvider.setInstance(new Random(1L));
            StudyPlan first = TopicPlans.optimize(context, 240, 60, 30);

            RandomProvider.setInstance(new Random(999L));
            StudyPlan second = TopicPlans.optimize(context, 240, 60, 30);

            assertThat(TopicPlans.signature(second)).isNotEqualTo(TopicPlans.signature(first));
        }
    }

    // ------------------------------------------------------------------
    // Escalabilidade
    // ------------------------------------------------------------------

    /**
     * <b>CONHECIDO-INSTÁVEL — asserção de tempo de parede.</b> Registrado como T8 em
     * {@code docs/qualidade/01-diagnostico-testes.md} e tratado em
     * {@code docs/qualidade/01b-correcao-testes.md}.
     *
     * <p><b>Causa suspeita:</b> a asserção final compara tempo de parede (<i>wall-clock</i>, o tempo
     * real decorrido, que inclui pausas do coletor de lixo e concorrência com outros processos)
     * contra um limite fixo. Numa máquina de integração contínua compartilhada, uma pausa longa ou
     * um vizinho barulhento pode estourar o limite sem que nada no código tenha mudado. A folga
     * medida é grande — centenas de milissegundos contra um teto de 10 s —, então o teste continua
     * ativo em vez de ser removido.
     *
     * <p><b>Por que não foi corrigido:</b> substituir tempo por uma medida insensível à máquina
     * exigiria contar operações (avaliações de fitness, por exemplo), o que significa instrumentar o
     * motor do algoritmo genético — mudança em código de produção, fora do escopo de uma etapa de
     * testes. Fica como pendência P2.
     *
     * <p>A etiqueta {@code lento-e-sensivel-a-maquina} permite excluir este teste de uma execução
     * com {@code -Dgroups='!lento-e-sensivel-a-maquina'} caso ele passe a falhar de forma
     * intermitente, sem precisar editar o código.
     */
    @Test
    @Tag("lento-e-sensivel-a-maquina")
    @DisplayName("escala para 200 topicos dentro de um orcamento de tempo razoavel")
    void scalesToManyItems() {
        EvolutionContext context = TopicPlans.context(200);

        // O piso vem do contexto, nao de um numero fixo: se SinapseMinimumDays mudar, o orcamento
        // acompanha em vez de a instancia virar inviavel.
        int floor = context.minimumDaysPerItem().values().stream().mapToInt(Integer::intValue).sum();
        int budget = (int) Math.ceil(floor * 1.25);

        long start = System.nanoTime();
        StudyPlan plan = TopicPlans.optimize(context, budget, 100, 50);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertThat(plan.getTotalDays()).isEqualTo(budget);
        assertThat(plan.getDaysPerItem()).hasSize(200);
        // Limite generoso: a assercao protege contra mudanca acidental de complexidade, nao contra
        // variacao de maquina para maquina.
        assertThat(elapsedMillis)
                .as("200 topicos levaram %d ms; esperado bem abaixo de 10 s", elapsedMillis)
                .isLessThan(10_000L);
    }

    @Test
    @DisplayName("o plano gerado sempre respeita orcamento e piso de dias minimos")
    void producedPlansAreAlwaysFeasible() {
        EvolutionContext context = TopicPlans.context(15);
        Map<PlanningItem, Integer> minimums = context.minimumDaysPerItem();
        int budget = (int) Math.ceil(minimums.values().stream()
                .mapToInt(Integer::intValue).sum() * 1.4);

        for (long seed : new long[]{1L, 2L, 3L, 4L, 5L}) {
            RandomProvider.setInstance(new Random(seed));
            StudyPlan plan = TopicPlans.optimize(context, budget, 50, 30);

            assertThat(plan.getTotalDays()).as("seed %d: orcamento", seed).isEqualTo(budget);
            assertThat(plan.meetsMinimumConstraints(minimums))
                    .as("seed %d: piso de dias minimos", seed).isTrue();
        }
    }

    /**
     * Reescrito na etapa 01b. A versão anterior era
     * {@code assertThatCode(...).doesNotThrowAnyException()} e não afirmava nada sobre o plano
     * produzido: um algoritmo que devolvesse um plano vazio, estourasse o orçamento ou reportasse
     * fitness fora de {@code [0,1]} passaria. A configuração mínima (1 geração, população 2) é o
     * caminho em que os laços do AG executam menos vezes, e é justamente onde um erro de contorno —
     * população que nunca evolui, elite não inicializada — apareceria primeiro.
     */
    @Test
    @DisplayName("configuracao minima do AG produz um resultado valido, nao apenas ausencia de excecao")
    void tinyGaConfigurationProducesAValidResult() {
        EvolutionContext context = TopicPlans.context(3);
        int budget = 60;

        StudyPlan plan = TopicPlans.optimize(context, budget, 1, 2);

        assertThat(plan).as("o otimizador deve devolver um plano").isNotNull();
        assertThat(plan.getDaysPerItem())
                .as("todo topico do pedido precisa aparecer no plano, mesmo com 1 geracao")
                .hasSize(3)
                .allSatisfy((item, days) -> assertThat(days)
                        .as("dias alocados para %s", item.name())
                        .isNotNegative());
        assertThat(plan.getTotalDays())
                .as("o orcamento deve ser respeitado exatamente, mesmo na configuracao minima")
                .isEqualTo(budget);
        assertThat(context.fitnessEvaluator().evaluate(plan, context))
                .as("a fitness agregada e normalizada em [0,1] (05-fitness-function.md)")
                .isBetween(0.0, 1.0);
    }
}
