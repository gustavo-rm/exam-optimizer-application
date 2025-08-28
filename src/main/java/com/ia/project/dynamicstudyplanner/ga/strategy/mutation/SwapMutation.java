package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;

import java.util.*;

/**
 * Implements a "swap" mutation. It randomly picks two genes (subjects)
 * and moves a day from one to the other. This elegantly preserves the
 * constraint of the total number of study days.
 */
public class SwapMutation implements MutationStrategy {
    private final Random random = new Random();

    @Override
    public Individual mutate(Individual individual, double mutationRate, EvolutionContext context) {
        if (random.nextDouble() > mutationRate) {
            return individual; // No mutation
        }

        Map<Subject, Integer> mutatedGenes = new HashMap<>(individual.getPlan().daysPerSubject());
        List<Subject> subjects = new ArrayList<>(mutatedGenes.keySet());

        if (subjects.size() < 2) {
            return individual; // Cannot perform swap with less than 2 subjects
        }

        // Select two different, random subjects for the swap
        int index1 = random.nextInt(subjects.size());
        int index2;
        do {
            index2 = random.nextInt(subjects.size());
        } while (index1 == index2);

        Subject subject1 = subjects.get(index1);
        Subject subject2 = subjects.get(index2);

        int minimumDays = context.minimumDaysPerSubject().getOrDefault(subject1, 1);
        // Ensure we don't violate the minimum study day constraint
        if (mutatedGenes.get(subject1) > minimumDays) {
            // Perform the swap: move one day from subject1 to subject2
            mutatedGenes.computeIfPresent(subject1, (s, days) -> days - 1);
            mutatedGenes.computeIfPresent(subject2, (s, days) -> days + 1);
        }
        // If we can't take from subject1, we could try taking from subject2.
        // For simplicity, we'll just attempt one-way.

        return new Individual(new StudyPlan(mutatedGenes));
    }
}
