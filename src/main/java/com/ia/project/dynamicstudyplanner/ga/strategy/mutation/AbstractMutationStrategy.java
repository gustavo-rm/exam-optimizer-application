package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * Base implementation for mutation strategies used in the genetic algorithm.
 *
 * <p>
 * This abstract class centralizes the common mutation workflow shared by all
 * mutation strategies:
 * </p>
 *
 * <ol>
 *     <li>Checks whether mutation should occur based on the mutation rate.</li>
 *     <li>Creates a mutable copy of the individual's genes.</li>
 *     <li>Validates that mutation is possible.</li>
 *     <li>Delegates the mutation behavior to subclasses.</li>
 *     <li>Creates and returns a new mutated {@link Individual}.</li>
 * </ol>
 *
 * <p>
 * Subclasses are responsible only for implementing the specific mutation logic.
 * This reduces duplicated orchestration code and improves maintainability.
 * </p>
 */
public abstract class AbstractMutationStrategy implements MutationStrategy {

    /**
     * Applies mutation to an individual following the standard mutation workflow.
     *
     * @param individual The individual selected for mutation.
     * @param mutationRate The probability of mutation occurring.
     * @param context The evolution context containing mutation constraints.
     * @return A new mutated individual, or the original individual if mutation
     *         did not occur or failed.
     */
    @Override
    public final Individual mutate(
            Individual individual,
            double mutationRate,
            EvolutionContext context
    ) {

        // Routed through RandomProvider so a fixed seed makes the whole evolution reproducible.
        // Previously Math.random(), whose internal generator no seed can reach.
        if (RandomProvider.getInstance().nextDouble() > mutationRate) {
            return individual;
        }

        Map<Subject, Integer> mutatedGenes =
                new HashMap<>(individual.getPlan().getDaysPerSubject());

        List<Subject> subjects =
                new ArrayList<>(mutatedGenes.keySet());

        if (subjects.size() < 2) {
            return individual;
        }

        boolean mutationSuccessful =
                performMutation(mutatedGenes, subjects, context);

        if (!mutationSuccessful) {
            return individual;
        }

        return new Individual(new StudyPlan(mutatedGenes));
    }

    /**
     * Performs the specific mutation behavior for a strategy.
     *
     * <p>
     * Implementations directly modify the provided mutable genes map.
     * </p>
     *
     * @param genes Mutable representation of the individual's genes.
     * @param subjects Available subjects participating in mutation.
     * @param context Evolution constraints and configuration.
     * @return {@code true} if mutation was successfully applied,
     *         otherwise {@code false}.
     */
    protected abstract boolean performMutation(
            Map<Subject, Integer> genes,
            List<Subject> subjects,
            EvolutionContext context
    );

    /**
     * Selects a random subject from the provided list.
     * <p>
     * Draws from {@link RandomProvider} rather than {@code ThreadLocalRandom}, which cannot be
     * seeded and therefore made the whole optimizer irreproducible. Mutation runs single-threaded
     * inside the evolution loop, so the shared generator carries no contention cost here.
     *
     * @param subjects Available subjects.
     * @return A randomly selected subject.
     */
    protected Subject randomSubject(List<Subject> subjects) {
        return subjects.get(RandomProvider.getInstance().nextInt(subjects.size()));
    }

    /**
     * Selects a random subject excluding a specific subject.
     *
     * @param subjects Available subjects. Must contain at least one subject other than
     *                 {@code excluded}.
     * @param excluded Subject that cannot be selected.
     * @return A randomly selected subject different from the excluded one.
     * @throws IllegalArgumentException if no other subject is available, which would otherwise spin
     *                                  forever in the rejection loop below.
     */
    protected Subject randomSubjectExcluding(
            List<Subject> subjects,
            Subject excluded
    ) {

        if (subjects.size() < 2) {
            throw new IllegalArgumentException(
                    "Cannot pick a subject other than '" + excluded.name()
                            + "': the plan has fewer than two subjects.");
        }

        Subject selected;

        do {
            selected = randomSubject(subjects);
        } while (selected.equals(excluded));

        return selected;
    }
}
