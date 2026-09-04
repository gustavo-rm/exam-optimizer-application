package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneVectors;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

/**
 * Base das estratégias de mutação do algoritmo genético.
 *
 * <p>Centraliza o roteiro comum a todas elas:
 *
 * <ol>
 *     <li>decide, pela taxa de mutação, se a mutação ocorre;</li>
 *     <li>copia os genes do indivíduo para um vetor alterável;</li>
 *     <li>verifica que a mutação é possível;</li>
 *     <li>delega o comportamento específico à subclasse;</li>
 *     <li>monta o indivíduo mutado.</li>
 * </ol>
 *
 * <p>A subclasse implementa só a mutação em si.
 *
 * <h2>Os genes são um vetor, não um mapa (pendência P18)</h2>
 *
 * A subclasse recebe {@code int[] genes} alinhado a {@link GeneVectors#index()} e trabalha por
 * <b>posição</b>: sortear uma disciplina virou sortear um índice, e ler ou escrever os dias dela
 * virou acessar uma posição de vetor. Antes cada mutação copiava o mapa inteiro do indivíduo —
 * alocando um nó por gene — para alterar dois números.
 */
public abstract class AbstractMutationStrategy implements MutationStrategy {

    /**
     * Aplica a mutação seguindo o roteiro padrão.
     *
     * @param individual o indivíduo sorteado para mutação
     * @param mutationRate probabilidade de a mutação ocorrer
     * @param context contexto da evolução, com os pisos de dias mínimos
     * @return um indivíduo novo e mutado, ou o original se a mutação não ocorreu ou não coube
     */
    @Override
    public final Individual mutate(
            Individual individual,
            double mutationRate,
            EvolutionContext context
    ) {

        // Passa pelo RandomProvider para que uma semente fixa torne a evolucao inteira reproduzivel.
        // Antes era Math.random(), cujo gerador interno nenhuma semente alcanca.
        if (RandomProvider.getInstance().nextDouble() > mutationRate) {
            return individual;
        }

        GeneVectors vetores = context.geneVectors();
        vetores.requireCovers(individual.getPlan());
        int[] mutatedGenes = individual.getPlan().genesAlignedTo(vetores.index());

        if (mutatedGenes.length < 2) {
            return individual;
        }

        if (!performMutation(mutatedGenes, vetores, context)) {
            return individual;
        }

        return new Individual(new StudyPlan(vetores.index(), mutatedGenes));
    }

    /**
     * Executa o comportamento específico da estratégia.
     *
     * <p>A implementação altera o vetor recebido diretamente.
     *
     * @param genes dias por posição, alteráveis
     * @param vetores os dados por disciplina já projetados na ordem dos genes
     * @param context restrições e configuração da evolução
     * @return {@code true} se a mutação foi aplicada, {@code false} se não coube
     */
    protected abstract boolean performMutation(
            int[] genes,
            GeneVectors vetores,
            EvolutionContext context
    );

    /**
     * Sorteia uma posição de gene.
     *
     * <p>Sorteia do {@link RandomProvider} e não de {@code ThreadLocalRandom}, que não aceita
     * semente e por isso tornava o otimizador inteiro irreproduzível. A mutação roda numa thread só,
     * dentro do laço de evolução, então o gerador compartilhado não custa disputa aqui.
     *
     * @param genes quantidade de genes disponíveis
     * @return uma posição entre {@code 0} e {@code genes - 1}
     */
    protected int randomGene(int genes) {
        return RandomProvider.getInstance().nextInt(genes);
    }

    /**
     * Sorteia uma posição de gene diferente da informada.
     *
     * @param genes quantidade de genes disponíveis; precisa ser ao menos 2
     * @param excluded posição que não pode sair
     * @return uma posição diferente de {@code excluded}
     * @throws IllegalArgumentException se houver menos de dois genes, caso em que o sorteio por
     *                                  rejeição abaixo giraria para sempre
     */
    protected int randomGeneExcluding(int genes, int excluded) {

        if (genes < 2) {
            throw new IllegalArgumentException(
                    "Cannot pick a subject other than position " + excluded
                            + ": the plan has fewer than two subjects.");
        }

        int selected;

        do {
            selected = randomGene(genes);
        } while (selected == excluded);

        return selected;
    }
}
