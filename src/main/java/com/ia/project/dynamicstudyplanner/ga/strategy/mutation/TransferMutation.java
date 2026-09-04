package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;

import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneVectors;
import org.springframework.stereotype.Component;

/**
 * Mutação de transferência.
 *
 * <p>Move um único dia de estudo de uma disciplina sorteada para outra, respeitando o piso de dias
 * mínimos da doadora.
 *
 * <p>Preserva o total de dias do plano e introduz variações pequenas no cromossomo, o que ajuda a
 * manter diversidade genética sem estragar soluções já boas.
 */
@Component("transferMutation")
public class TransferMutation extends AbstractMutationStrategy {

    /**
     * Transfere um dia entre duas disciplinas sorteadas.
     *
     * @param genes dias por posição, alteráveis
     * @param vetores os dados por disciplina projetados na ordem dos genes
     * @param context restrições da evolução
     * @return {@code true} se a transferência coube, {@code false} se a doadora já está no piso
     */
    @Override
    protected boolean performMutation(
            int[] genes,
            GeneVectors vetores,
            EvolutionContext context
    ) {

        int origem = randomGene(genes.length);
        int destino = randomGeneExcluding(genes.length, origem);

        if (genes[origem] <= vetores.minimumDays(origem)) {
            return false;
        }

        genes[origem]--;
        genes[destino]++;

        return true;
    }
}
