package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;

import java.util.*;

/**
 * Implements an "aggressive" swap mutation.
 * Instead of moving just one day, this strategy moves a random number of days
 * between two randomly selected subjects. This introduces more significant changes,
 * helping the algorithm escape local optima and explore the search space more effectively.
 */
public class AggressiveSwapMutation implements MutationStrategy {
    private final Random random = new Random();
    private final int maxDaysToMove;

    /**
     * Default constructor. Sets the maximum number of days to move to 5.
     */
    public AggressiveSwapMutation() {
        this.maxDaysToMove = 5;
    }

    /**
     * Constructor allowing customization of the mutation's aggressiveness.
     * @param maxDaysToMove The maximum number of days to be moved in a single mutation event.
     */
    public AggressiveSwapMutation(int maxDaysToMove) {
        this.maxDaysToMove = maxDaysToMove > 0 ? maxDaysToMove : 1;
    }

    @Override
    public Individual mutate(Individual individual, double mutationRate, EvolutionContext context) {
        // Decide if mutation should occur based on the rate
        if (random.nextDouble() > mutationRate) {
            return individual; // No mutation
        }

        // Create a mutable copy of the individual's genes
        Map<Subject, Integer> mutatedGenes = new HashMap<>(individual.getPlan().daysPerSubject());
        List<Subject> subjects = new ArrayList<>(mutatedGenes.keySet());

        if (subjects.size() < 2) {
            return individual; // Cannot perform a swap with less than 2 subjects
        }

        // 1. Select two different, random subjects for the swap
        int index1 = random.nextInt(subjects.size());
        int index2;
        do {
            index2 = random.nextInt(subjects.size());
        } while (index1 == index2);

        Subject subjectFrom = subjects.get(index1); // Subject that will "donate" days
        Subject subjectTo = subjects.get(index2);   // Subject that will "receive" days

        // 2. Determine a random number of days to move
        int daysToMove = random.nextInt(maxDaysToMove) + 1; // From 1 to maxDaysToMove

        // 3. CRITICAL CHECK: Ensure the mutation is valid
        // Check if the "donating" subject has enough days to give away without
        // violating its minimum study day constraint.
        int minimumDays = context.minimumDaysPerSubject().getOrDefault(subjectFrom, 1);
        int availableDaysToDonate = mutatedGenes.get(subjectFrom) - minimumDays;

        if (availableDaysToDonate > 0) {
            // We can't move more days than are available to donate.
            int finalDaysToMove = Math.min(daysToMove, availableDaysToDonate);

            // 4. Perform the aggressive swap
            mutatedGenes.computeIfPresent(subjectFrom, (s, days) -> days - finalDaysToMove);
            mutatedGenes.computeIfPresent(subjectTo, (s, days) -> days + finalDaysToMove);

            // Return a new individual with the mutated plan
            return new Individual(new StudyPlan(mutatedGenes));
        }

        // If the chosen subject had no days to donate, we return the original individual.
        // A more complex implementation could try picking another pair of subjects.
        return individual;
    }
}
