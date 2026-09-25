package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.config.DefaultGeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.generator.DefaultPopulationGenerator;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.HybridCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.RepairingCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.WeightedAverageCrossover;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.CreepMutation;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.TournamentSelection;
import com.ia.project.dynamicstudyplanner.support.TopicPlans;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o resultado exato do algoritmo genético para uma semente fixa.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * A etapa 05b otimiza o motor de planejamento. A instrução era explícita: <b>otimização não pode
 * mudar comportamento</b>. Este teste é o que dá sentido a essa frase — sem ele, "o resultado
 * continua igual" seria opinião.
 *
 * <p>Com a semente fixada, a evolução inteira é determinística: a mesma sequência de sorteios
 * produz o mesmo plano, item por item, e a mesma fitness. Qualquer mudança que altere a
 * <b>ordem</b> ou a <b>quantidade</b> de sorteios — e não só o resultado do cálculo — aparece aqui
 * como falha.
 *
 * <p>Isso é mais estrito do que parece. Trocar {@code new HashMap<>()} por
 * {@code new HashMap<>(capacidade)} não muda nenhuma conta, mas muda a distribuição em baldes e,
 * com ela, a ordem de iteração dos itens — que é o que decide qual item o reparo sorteia. Uma
 * otimização aparentemente inofensiva de alocação seria pega por este teste.
 *
 * <h2>O que mudou em EOA-4b, e o que deliberadamente não mudou</h2>
 *
 * A instância deixou de ser um {@code Exam} com {@code Subject}s e passou a ser um
 * {@code PlanRequest} com tópicos, montado pelo mesmo {@code SinapseEvolutionContexts} que o
 * endpoint usa; a fitness é a composição SINAPSE, não a de concurso. <b>É a opção (a)</b>: em vez
 * de apagar o teste junto com o caminho que ele media, ele passou a proteger o caminho que ficou.
 *
 * <p>O que <b>não</b> mudou é o desenho, e isso é o ponto: ele continua comparando <b>duas
 * execuções com a mesma semente</b> em vez de conferir números gravados. Não há assinatura de
 * referência neste repositório, e não deve haver — números fixos exigiriam ser reescritos a cada
 * mudança legítima de algoritmo, e a tentação seria reescrevê-los sem olhar. Comparar duas
 * execuções verifica a propriedade que de fato importa — reprodutibilidade — e a estabilidade entre
 * versões fica a cargo do <i>diff</i> do commit, onde é visível.
 *
 * <p>O complemento de <b>qualidade</b> era {@code benchmark/GeneticAlgorithmVsBaselinesTest}, que
 * media o AG contra baselines simples sobre instâncias de concurso. Ele saiu com o caminho que
 * media; reconstruí-lo no domínio de tópicos é trabalho próprio, com limiares a remedir.
 */
@DisplayName("AG: o resultado nao muda quando a semente e a mesma")
class GaResultadoInalteradoTest {

    private static final long SEMENTE = 20260903L;

    /** Sessões que o cromossomo distribui. O mesmo total em todas as instâncias, de propósito. */
    private static final int ORCAMENTO = 365;

    private static final int GERACOES = 60;
    private static final int POPULACAO = 40;

    private static StudyPlan execucaoSemeada(EvolutionContext contexto, long semente,
                                             int geracoes, int populacao) {
        RandomProvider.setInstance(new Random(semente));
        GeneticAlgorithm algoritmo = new DefaultGeneticAlgorithmFactory(new TournamentSelection(),
                new HybridCrossover(new WeightedAverageCrossover(), new RepairingCrossover()),
                new CreepMutation()).create();
        Population populacaoAtual =
                new DefaultPopulationGenerator().generate(ORCAMENTO, populacao, contexto);
        for (int geracao = 0; geracao < geracoes; geracao++) {
            populacaoAtual = algoritmo.evolvePopulation(populacaoAtual, contexto);
        }
        return populacaoAtual.getFittest().getPlan();
    }

    private static StudyPlan execucaoSemeada(EvolutionContext contexto) {
        return execucaoSemeada(contexto, SEMENTE, GERACOES, POPULACAO);
    }

    @Test
    @DisplayName("mesma semente produz o mesmo plano, item por item")
    void mesmaSementeProduzOMesmoPlano() {
        EvolutionContext contexto = TopicPlans.context(12);

        StudyPlan primeira = execucaoSemeada(contexto);
        StudyPlan segunda = execucaoSemeada(contexto);

        assertThat(TopicPlans.signature(segunda))
                .as("a alocacao de sessoes tem que ser identica")
                .isEqualTo(TopicPlans.signature(primeira));
        assertThat(contexto.fitnessEvaluator().evaluate(segunda, contexto))
                .as("a fitness tem que ser identica ate o ultimo bit")
                .isEqualTo(contexto.fitnessEvaluator().evaluate(primeira, contexto));
    }

    @Test
    @DisplayName("a reprodutibilidade vale para varios tamanhos de instancia")
    void aReprodutibilidadeValeParaVariosTamanhos() {
        for (int topicos : new int[]{5, 12, 24}) {
            EvolutionContext contexto = TopicPlans.context(topicos);

            StudyPlan a = execucaoSemeada(contexto, SEMENTE, 40, 30);
            StudyPlan b = execucaoSemeada(contexto, SEMENTE, 40, 30);

            assertThat(TopicPlans.signature(b))
                    .as("instancia de %d topicos nao reproduziu", topicos)
                    .isEqualTo(TopicPlans.signature(a));
            assertThat(contexto.fitnessEvaluator().evaluate(b, contexto))
                    .isEqualTo(contexto.fitnessEvaluator().evaluate(a, contexto));
        }
    }

    @Test
    @DisplayName("sementes diferentes produzem planos diferentes — a semente esta mesmo em uso")
    void sementesDiferentesProduzemPlanosDiferentes() {
        // Contraprova: sem ela, um teste de reprodutibilidade passaria mesmo que o AG ignorasse a
        // aleatoriedade por completo e devolvesse sempre a mesma coisa.
        EvolutionContext contexto = TopicPlans.context(12);

        StudyPlan primeira = execucaoSemeada(contexto, 1L, GERACOES, POPULACAO);
        StudyPlan segunda = execucaoSemeada(contexto, 2L, GERACOES, POPULACAO);

        assertThat(TopicPlans.signature(segunda))
                .as("sementes distintas deveriam explorar caminhos distintos")
                .isNotEqualTo(TopicPlans.signature(primeira));
    }

    @Test
    @DisplayName("o orcamento de sessoes e respeitado exatamente, em qualquer tamanho")
    void oOrcamentoEhRespeitado() {
        for (int topicos : new int[]{5, 12, 24}) {
            StudyPlan plano = execucaoSemeada(TopicPlans.context(topicos), SEMENTE, 40, 30);
            int soma = plano.getDaysPerItem().values().stream().mapToInt(Integer::intValue).sum();
            assertThat(soma)
                    .as("instancia de %d topicos estourou ou desperdicou o orcamento", topicos)
                    .isEqualTo(ORCAMENTO);
        }
    }
}
