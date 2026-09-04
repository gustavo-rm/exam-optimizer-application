package com.ia.project.dynamicstudyplanner.ga.strategy.crossover;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.SubjectIndex;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneVectors;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.springframework.stereotype.Component;

/**
 * Cruzamento de ponto único seguido de reparo.
 *
 * <p>Este operador é explorador: sorteia um ponto de corte, toma os genes anteriores a ele do
 * primeiro pai e os posteriores do segundo. O reparo é indispensável — a soma resultante não é o
 * orçamento de nenhum dos dois pais, e sem ele a evolução produziria planos que a API não pode
 * entregar.
 */
@Component
public class RepairingCrossover implements CrossoverStrategy {

    /**
     * Cria um filho por cruzamento de ponto único e repara a soma de dias.
     *
     * @param parent1 primeiro pai
     * @param parent2 segundo pai
     * @param crossoverRate probabilidade de o cruzamento ocorrer
     * @param context contexto da evolução, com os pisos de dias mínimos usados no reparo
     * @return o filho
     */
    @Override
    public Individual crossover(Individual parent1, Individual parent2, double crossoverRate,
            EvolutionContext context) {
        if (RandomProvider.getInstance().nextDouble() > crossoverRate) {
            // Sem cruzamento, devolve uma copia do pai mais apto, para nao perder qualidade.
            return parent1.getFitness() > parent2.getFitness()
                    ? new Individual(parent1.getPlan())
                    : new Individual(parent2.getPlan());
        }

        GeneVectors vetores = context.geneVectors();
        vetores.requireCovers(parent1.getPlan());
        int[] childGenes = performSinglePointCrossover(parent1, parent2, vetores.index());
        ChildGeneRepair.repairToTargetSum(childGenes, vetores.minimumDaysVector(),
                parent1.getPlan().getTotalDays());
        return new Individual(new StudyPlan(vetores.index(), childGenes));
    }

    /**
     * Cruzamento de ponto único clássico: sorteia uma posição e junta o começo do primeiro pai com o
     * fim do segundo.
     *
     * <h2>O caminho rápido e o de segurança (pendência P18)</h2>
     *
     * Quando os dois pais compartilham a ordem de genes do contexto — o caso de todo indivíduo
     * produzido pela evolução, que herda o índice do contexto de ponta a ponta — cada gene é uma
     * leitura de posição de vetor. Quando não compartilham (um plano vindo da fronteira, montado a
     * partir de um mapa), a leitura passa pela disciplina. A escolha é feita <b>uma vez</b>, fora do
     * laço, e não por gene.
     *
     * @param parent1 primeiro pai
     * @param parent2 segundo pai
     * @param ordem a ordem canônica dos genes do filho
     * @return os genes do filho, ainda sem reparo
     */
    private int[] performSinglePointCrossover(Individual parent1, Individual parent2, SubjectIndex ordem) {
        StudyPlan plano1 = parent1.getPlan();
        StudyPlan plano2 = parent2.getPlan();
        boolean alinhados = plano1.getIndex() == ordem && plano2.getIndex() == ordem;

        int genes = ordem.size();
        int[] childGenes = new int[genes];
        int crossoverPoint = genes > 1 ? RandomProvider.getInstance().nextInt(genes - 1) + 1 : 1;

        for (int i = 0; i < genes; i++) {
            StudyPlan fonte = i < crossoverPoint ? plano1 : plano2;
            childGenes[i] = alinhados ? fonte.daysAt(i) : fonte.getDaysForSubject(ordem.subject(i));
        }
        return childGenes;
    }
}
