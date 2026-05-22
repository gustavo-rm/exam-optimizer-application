package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;
import org.springframework.stereotype.Component;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import java.util.*;
/**
 * Implements a "creep" mutation, a more advanced and robust strategy than a simple swap.
 * This method randomly selects a subject and slightly increases or decreases its allocated
 * days, then balances this change by making an opposite adjustment to another random
 - * subject. This provides a more nuanced way to explore the search space.
 */
@Component
public class CreepMutation implements MutationStrategy {
    private final Random random = new Random();
    private final int maxCreepDistance;
    /**
     * Default constructor. The creep distance will be a maximum of 3 days.
     */
    public CreepMutation() {
        this.maxCreepDistance = 3;
    }
    /**
     * Creates a creep mutation with a configurable maximum distance.
     * @param maxCreepDistance The maximum number of days to add or subtract in one mutation.
     */
    public CreepMutation(int maxCreepDistance) {
        this.maxCreepDistance = maxCreepDistance > 0 ? maxCreepDistance : 1;
    }
    /**
     * Applies the creep mutation to an individual's study plan.
     * It attempts to modify the plan and returns the original if the mutation fails
     * to produce a valid new plan.
     *
     * @param individual The individual to mutate.
     * @param mutationRate The probability that mutation will occur.
     * @param context The evolution context, containing minimum day constraints.
     * @return A new, mutated Individual, or the original if no mutation occurred or was possible.
     */
    @Override
    public Individual mutate(Individual individual, double mutationRate, EvolutionContext context) {
        if (random.nextDouble() > mutationRate) {
            return individual; // No mutation occurs.
        }
        Map<Subject, Integer> mutatedGenes = new HashMap<>(individual.getPlan().getDaysPerSubject());
        List<Subject> subjects = new ArrayList<>(mutatedGenes.keySet());
        if (subjects.size() < 2) {
            return individual; // Cannot perform mutation with fewer than 2 subjects.
        }
        // 1. Select two distinct subjects for the operation.
        Subject subjectToMutate = selectRandomSubject(subjects, null);
        Subject subjectToBalance = selectRandomSubject(subjects, subjectToMutate);
        // 2. Determine a small, random change (positive or negative).
        int creepValue = (random.nextInt(maxCreepDistance * 2) + 1) - maxCreepDistance;
        if (creepValue == 0) creepValue = 1; // Ensure there's always a change.
        // 3. Attempt to apply the mutation and balance the change.
        boolean mutationSuccessful = applyCreep(
                mutatedGenes,
                subjectToMutate,
                subjectToBalance,
                creepValue,
                context.minimumDaysPerSubject()
        );
        if (mutationSuccessful) {
            return new Individual(new StudyPlan(mutatedGenes));
        }
        // If the mutation was not successful (e.g., violated constraints), return the original individual.
        return individual;
    }
    /**
     * Selects a random subject from a list, ensuring it is not the same as the excluded subject.
     *
     * @param subjects The list of subjects to choose from.
     * @param excludedSubject A subject to exclude from the selection, can be null.
     * @return A randomly selected subject.
     */
    private Subject selectRandomSubject(List<Subject> subjects, Subject excludedSubject) {
        Subject selected;
        do {
            selected = subjects.get(random.nextInt(subjects.size()));
        } while (selected.equals(excludedSubject));
        return selected;
    }
    /**
     * Applies the creep value to one subject and the inverse to another, ensuring all
     * minimum day constraints are respected.
     *
     * @param genes The mutable map of genes to modify.
     * @param subjectToMutate The subject whose days will be changed by the creep value.
     * @param subjectToBalance The subject that will receive the inverse change.
     * @param creepValue The amount to add (can be negative) to the first subject.
     * @param minimumDaysPerSubject The map of minimum day constraints.
     * @return True if the mutation was applied successfully, false otherwise.
     */
    private boolean applyCreep(Map<Subject, Integer> genes, Subject subjectToMutate, Subject subjectToBalance, int creepValue, Map<Subject, Integer> minimumDaysPerSubject) {
        int originalDaysMutate = genes.get(subjectToMutate);
        int originalDaysBalance = genes.get(subjectToBalance);
        int newDaysMutate = originalDaysMutate + creepValue;
        int newDaysBalance = originalDaysBalance - creepValue;
        // Check if the new values are valid for BOTH subjects.
        int minDaysMutate = minimumDaysPerSubject.getOrDefault(subjectToMutate, 1);
        int minDaysBalance = minimumDaysPerSubject.getOrDefault(subjectToBalance, 1);
        if (newDaysMutate >= minDaysMutate && newDaysBalance >= minDaysBalance) {
            // If both are valid, apply the changes.
            genes.put(subjectToMutate, newDaysMutate);
            genes.put(subjectToBalance, newDaysBalance);
            return true;
        }
        // If the change is not valid, do nothing and report failure.
        return false;
    }
}
