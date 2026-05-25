package com.ia.project.dynamicstudyplanner.ga.strategy.crossover;
import org.springframework.stereotype.Component;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import java.util.*;
/**
 * Implements a Weighted Average Crossover strategy.
 * This exploitative method creates a child whose genes are a weighted average of its parents'
 * genes, biased towards the fitter parent. It is excellent for refining existing solutions.
 */
@Component
public class WeightedAverageCrossover implements CrossoverStrategy {
    private final Random random = new Random();
    /**
     * Creates a new child by calculating the weighted average of two parents' genes and then
     * repairing the child's gene sum to ensure its validity.
     *
     * @param parent1 The first parent for recombination.
     * @param parent2 The second parent for recombination.
     * @param crossoverRate The probability that crossover will occur.
     * @param context The evolution context, containing minimum day constraints for the repair step.
     * @return A new Individual representing the child.
     */
    @Override
    public Individual crossover(Individual parent1, Individual parent2, double crossoverRate, EvolutionContext context) {
        if (random.nextDouble() > crossoverRate) {
            return parent1.getFitness() > parent2.getFitness() ? new Individual(parent1.getPlan()) : new Individual(parent2.getPlan());
        }
        // Step 1: Create the initial child genes using a weighted average.
        Map<Subject, Integer> childGenes = createWeightedAverageGenes(parent1, parent2);
        // Step 2: Repair the child's genes to ensure the total day sum is correct.
        repairChildGenes(childGenes, parent1.getPlan().getTotalDays(), context.minimumDaysPerSubject());
        return new Individual(new StudyPlan(childGenes));
    }
    /**
     * Creates a new set of genes by calculating the weighted average of the parents' genes.
     * The fitness of each parent is used as its weight, causing the child to be more
     * similar to the fitter parent.
     *
     * @param parent1 The first parent.
     * @param parent2 The second parent.
     * @return A map representing the newly created, unrepaired child genes.
     */
    private Map<Subject, Integer> createWeightedAverageGenes(Individual parent1, Individual parent2) {
        Map<Subject, Integer> parent1Genes = parent1.getPlan().getDaysPerSubject();
        Map<Subject, Integer> parent2Genes = parent2.getPlan().getDaysPerSubject();
        Map<Subject, Integer> childGenes = new HashMap<>();
        double weight1 = parent1.getFitness();
        double weight2 = parent2.getFitness();
        double totalFitness = weight1 + weight2;
        if (totalFitness == 0) { // Avoid division by zero if both fitnesses are 0
            totalFitness = 1;
        }
        for (Subject subject : parent1Genes.keySet()) {
            double p1Value = parent1Genes.getOrDefault(subject, 0);
            double p2Value = parent2Genes.getOrDefault(subject, 0);
            double childValue = (p1Value * weight1 + p2Value * weight2) / totalFitness;
            childGenes.put(subject, (int) Math.round(childValue));
        }
        return childGenes;
    }
    /**
     * Adjusts the child's genes by randomly adding or removing days until the sum of
     * days equals the target total. This ensures the study plan is valid.
     *
     * @param childGenes The mutable map of the child's genes to be repaired.
     * @param targetDaySum The required total number of days for the plan.
     * @param minimumDaysPerSubject A map of constraints to avoid violating minimums.
     */
    private void repairChildGenes(Map<Subject, Integer> childGenes, int targetDaySum, Map<Subject, Integer> minimumDaysPerSubject) {
        int currentDaySum = 0;
        for (Integer days : childGenes.values()) {
            currentDaySum += days;
        }
        int difference = targetDaySum - currentDaySum;
        List<Subject> subjects = new ArrayList<>(childGenes.keySet());
        while (difference != 0 && !subjects.isEmpty()) {
            int randomIndex = random.nextInt(subjects.size());
            Subject randomSubject = subjects.get(randomIndex);
            int currentDays = childGenes.get(randomSubject);
            if (difference > 0) {
                childGenes.put(randomSubject, currentDays + 1);
                difference--;
            } else {
                int minimumDays = minimumDaysPerSubject.getOrDefault(randomSubject, 1);
                if (currentDays > minimumDays) {
                    childGenes.put(randomSubject, currentDays - 1);
                    difference++;
                } else {
                    // Fast removal from ArrayList by swapping with last element
                    int lastIndex = subjects.size() - 1;
                    subjects.set(randomIndex, subjects.get(lastIndex));
                    subjects.remove(lastIndex);
                }
            }
        }
    }
}
