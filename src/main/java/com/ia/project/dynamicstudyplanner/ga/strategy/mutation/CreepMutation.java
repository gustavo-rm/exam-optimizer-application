package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Implements a creep mutation strategy.
 *
 * <p>
 * This mutation applies a small positive or negative adjustment to the
 * study days of one subject while applying the inverse adjustment to
 * another subject.
 * </p>
 *
 * <p>
 * Compared to swap mutations, creep mutation introduces smoother and
 * more gradual changes to the chromosome, improving local search
 * capabilities and solution refinement.
 * </p>
 */
@Component("creepMutation")
public class CreepMutation extends AbstractMutationStrategy {

    /**
     * Maximum absolute variation applied during mutation.
     */
    private final int maxCreepDistance;

    /**
     * Creates a creep mutation strategy with default distance.
     */
    public CreepMutation() {
        this(3);
    }

    /**
     * Creates a creep mutation strategy with custom distance.
     *
     * @param maxCreepDistance Maximum mutation variation.
     */
    public CreepMutation(int maxCreepDistance) {
        this.maxCreepDistance = Math.max(1, maxCreepDistance);
    }

    /**
     * Performs the creep mutation operation.
     *
     * @param genes Mutable genes map.
     * @param subjects Available subjects.
     * @param context Evolution constraints.
     * @return {@code true} if mutation succeeded, otherwise {@code false}.
     */
    @Override
    protected boolean performMutation(
            Map<Subject, Integer> genes,
            List<Subject> subjects,
            EvolutionContext context
    ) {

        Subject subjectToMutate =
                randomSubject(subjects);

        Subject subjectToBalance =
                randomSubjectExcluding(
                        subjects,
                        subjectToMutate
                );

        // RandomProvider rather than ThreadLocalRandom: the latter cannot be seeded, which made
        // the evolution irreproducible even with a fixed seed.
        int creepValue =
                com.ia.project.dynamicstudyplanner.util.RandomProvider
                        .getInstance()
                        .nextInt(-maxCreepDistance,
                                maxCreepDistance + 1);

        if (creepValue == 0) {
            creepValue = 1;
        }

        return applyCreep(
                genes,
                subjectToMutate,
                subjectToBalance,
                creepValue,
                context.minimumDaysPerSubject()
        );
    }

    /**
     * Applies the creep adjustment while validating minimum constraints.
     *
     * @param genes Mutable genes map.
     * @param subjectToMutate Subject receiving the mutation.
     * @param subjectToBalance Subject balancing the mutation.
     * @param creepValue Mutation adjustment value.
     * @param minimumDaysPerSubject Minimum study-day constraints.
     * @return {@code true} if mutation succeeded, otherwise {@code false}.
     */
    private boolean applyCreep(
            Map<Subject, Integer> genes,
            Subject subjectToMutate,
            Subject subjectToBalance,
            int creepValue,
            Map<Subject, Integer> minimumDaysPerSubject
    ) {

        int mutatedDays =
                genes.get(subjectToMutate) + creepValue;

        int balancedDays =
                genes.get(subjectToBalance) - creepValue;

        int minimumMutated =
                minimumDaysPerSubject
                        .getOrDefault(subjectToMutate, 1);

        int minimumBalanced =
                minimumDaysPerSubject
                        .getOrDefault(subjectToBalance, 1);

        if (mutatedDays < minimumMutated ||
                balancedDays < minimumBalanced) {

            return false;
        }

        genes.put(subjectToMutate, mutatedDays);
        genes.put(subjectToBalance, balancedDays);

        return true;
    }
}