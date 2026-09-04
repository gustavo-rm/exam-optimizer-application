package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;

import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneVectors;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.springframework.stereotype.Component;

/**
 * Mutação por deslocamento (<i>creep</i>).
 *
 * <p>Aplica um ajuste pequeno, positivo ou negativo, aos dias de uma disciplina, e o ajuste inverso
 * a outra — o total do plano não muda.
 *
 * <p>Comparada à mutação de troca, produz mudanças mais graduais no cromossomo, o que favorece a
 * busca local e o refinamento de soluções já boas.
 */
@Component("creepMutation")
public class CreepMutation extends AbstractMutationStrategy {

    /** Variação máxima, em módulo, aplicada pela mutação. */
    private final int maxCreepDistance;

    /** Cria a estratégia com a distância padrão. */
    public CreepMutation() {
        this(3);
    }

    /**
     * Cria a estratégia com uma distância própria.
     *
     * @param maxCreepDistance variação máxima da mutação
     */
    public CreepMutation(int maxCreepDistance) {
        this.maxCreepDistance = Math.max(1, maxCreepDistance);
    }

    /**
     * Desloca os dias de uma disciplina e compensa em outra.
     *
     * @param genes dias por posição, alteráveis
     * @param vetores os dados por disciplina projetados na ordem dos genes
     * @param context restrições da evolução
     * @return {@code true} se o deslocamento coube nos pisos das duas disciplinas
     */
    @Override
    protected boolean performMutation(
            int[] genes,
            GeneVectors vetores,
            EvolutionContext context
    ) {

        int mutada = randomGene(genes.length);
        int compensadora = randomGeneExcluding(genes.length, mutada);

        // RandomProvider e nao ThreadLocalRandom: o segundo nao aceita semente, o que tornava a
        // evolucao irreproduzivel mesmo com semente fixa.
        int creepValue =
                RandomProvider
                        .getInstance()
                        .nextInt(-maxCreepDistance,
                                maxCreepDistance + 1);

        if (creepValue == 0) {
            creepValue = 1;
        }

        return applyCreep(genes, vetores, mutada, compensadora, creepValue);
    }

    /**
     * Aplica o deslocamento, verificando antes os pisos de dias mínimos das duas disciplinas.
     *
     * @param genes dias por posição, alteráveis
     * @param vetores os dados por disciplina projetados na ordem dos genes
     * @param mutada posição que recebe o deslocamento
     * @param compensadora posição que o compensa
     * @param creepValue tamanho e sinal do deslocamento
     * @return {@code true} se as duas posições continuam acima do piso
     */
    private boolean applyCreep(
            int[] genes,
            GeneVectors vetores,
            int mutada,
            int compensadora,
            int creepValue
    ) {

        int mutatedDays = genes[mutada] + creepValue;
        int balancedDays = genes[compensadora] - creepValue;

        if (mutatedDays < vetores.minimumDays(mutada)
                || balancedDays < vetores.minimumDays(compensadora)) {

            return false;
        }

        genes[mutada] = mutatedDays;
        genes[compensadora] = balancedDays;

        return true;
    }
}
