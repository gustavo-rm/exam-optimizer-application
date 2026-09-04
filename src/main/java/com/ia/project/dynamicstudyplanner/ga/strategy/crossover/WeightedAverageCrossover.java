package com.ia.project.dynamicstudyplanner.ga.strategy.crossover;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.SubjectIndex;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneVectors;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.springframework.stereotype.Component;

/**
 * Cruzamento por média ponderada.
 *
 * <p>Este operador é refinador: cada gene do filho é a média dos genes dos pais, pesada pela aptidão
 * de cada um, o que aproxima o filho do pai mais apto. Serve para lapidar soluções já boas, ao
 * contrário do {@link RepairingCrossover}, que explora combinações novas.
 */
@Component
public class WeightedAverageCrossover implements CrossoverStrategy {

    /**
     * Cria um filho pela média ponderada dos genes dos pais e repara a soma de dias.
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
            return parent1.getFitness() > parent2.getFitness()
                    ? new Individual(parent1.getPlan())
                    : new Individual(parent2.getPlan());
        }

        GeneVectors vetores = context.geneVectors();
        vetores.requireCovers(parent1.getPlan());
        int[] childGenes = createWeightedAverageGenes(parent1, parent2, vetores.index());
        ChildGeneRepair.repairToTargetSum(childGenes, vetores.minimumDaysVector(),
                parent1.getPlan().getTotalDays());
        return new Individual(new StudyPlan(vetores.index(), childGenes));
    }

    /**
     * Calcula a média ponderada dos genes dos pais, usando a aptidão de cada um como peso.
     *
     * <p>Sobre o caminho rápido e o de segurança, ver a nota em
     * {@code RepairingCrossover.performSinglePointCrossover}.
     *
     * @param parent1 primeiro pai
     * @param parent2 segundo pai
     * @param ordem a ordem canônica dos genes do filho
     * @return os genes do filho, ainda sem reparo
     */
    private int[] createWeightedAverageGenes(Individual parent1, Individual parent2, SubjectIndex ordem) {
        StudyPlan plano1 = parent1.getPlan();
        StudyPlan plano2 = parent2.getPlan();
        boolean alinhados = plano1.getIndex() == ordem && plano2.getIndex() == ordem;

        double weight1 = parent1.getFitness();
        double weight2 = parent2.getFitness();
        double totalFitness = weight1 + weight2;
        if (totalFitness == 0) { // Evita divisao por zero quando as duas aptidoes sao 0.
            totalFitness = 1;
        }

        int genes = ordem.size();
        int[] childGenes = new int[genes];
        for (int i = 0; i < genes; i++) {
            double p1Value = alinhados ? plano1.daysAt(i) : plano1.getDaysForSubject(ordem.subject(i));
            double p2Value = alinhados ? plano2.daysAt(i) : plano2.getDaysForSubject(ordem.subject(i));
            childGenes[i] = (int) Math.round((p1Value * weight1 + p2Value * weight2) / totalFitness);
        }
        return childGenes;
    }
}
