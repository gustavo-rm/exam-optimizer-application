package com.ia.project.dynamicstudyplanner.ga.strategy.crossover;
import org.springframework.stereotype.Component;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
/**
 * Implements a single-point crossover strategy followed by a repair mechanism.
 * This explorative crossover method is effective at creating new genetic combinations.
 * The repair step is crucial to ensure the child's study plan remains valid by
 * respecting the total number of available days.
 */
@Component
public class RepairingCrossover implements CrossoverStrategy {
    /**
     * Creates a new child by performing a single-point crossover on two parents and then
     * repairing the child's gene sum to ensure its validity.
     *
     * @param parent1 The first parent for recombination.
     * @param parent2 The second parent for recombination.
     * @param crossoverRate The probability that crossover will occur.
     * @param context The evolution context, containing minimum day constraints for the repair step.
     * @return A new Individual representing the child.
     */
    @Override
    public Individual crossover(Individual parent1, Individual parent2, double crossoverRate,
            EvolutionContext context) {
        if (RandomProvider.getInstance().nextDouble() > crossoverRate) {
            // If no crossover occurs, return a clone of the fitter parent to maintain population quality.
            return parent1.getFitness() > parent2.getFitness()
                    ? new Individual(parent1.getPlan())
                    : new Individual(parent2.getPlan());
        }
        // Step 1: Perform the single-point crossover to create the initial child genes.
        Map<Subject, Integer> childGenes = performSinglePointCrossover(parent1, parent2);
        // Step 2: Repair the child's genes to ensure the total day sum is correct.
        ChildGeneRepair.repairToTargetSum(childGenes, parent1.getPlan().getTotalDays(),
                context.minimumDaysPerSubject());
        return new Individual(new StudyPlan(childGenes));
    }
    /**
     * Performs a classic single-point crossover. It selects a random point in the chromosome
     * and combines the first part of parent1's genes with the second part of parent2's genes.
     *
     * @param parent1 The first parent.
     * @param parent2 The second parent.
     * @return A map representing the newly created, unrepaired child genes.
     */
    private Map<Subject, Integer> performSinglePointCrossover(Individual parent1, Individual parent2) {
        Map<Subject, Integer> parent1Genes = parent1.getPlan().getDaysPerSubject();
        Map<Subject, Integer> parent2Genes = parent2.getPlan().getDaysPerSubject();
        List<Subject> subjects = new ArrayList<>(parent1Genes.keySet());
        Map<Subject, Integer> childGenes = new HashMap<>();
        int crossoverPoint = subjects.size() > 1 ? RandomProvider.getInstance().nextInt(subjects.size() - 1) + 1 : 1;
        for (int i = 0; i < subjects.size(); i++) {
            Subject subject = subjects.get(i);
            if (i < crossoverPoint) {
                childGenes.put(subject, parent1Genes.get(subject));
            } else {
                childGenes.put(subject, parent2Genes.get(subject));
            }
        }
        return childGenes;
    }
}
