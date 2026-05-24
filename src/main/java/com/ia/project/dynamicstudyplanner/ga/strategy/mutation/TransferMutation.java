package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Implements a simple swap mutation strategy.
 *
 * <p>
 * This mutation transfers a single study day from one randomly selected
 * subject to another while respecting minimum study-day constraints.
 * </p>
 *
 * <p>
 * The strategy preserves the total number of study days and introduces
 * small variations into the chromosome, helping maintain genetic diversity.
 * </p>
 */
@Component("transferMutation")
public class TransferMutation extends AbstractMutationStrategy {

    /**
     * Performs the swap mutation.
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

        Subject subjectFrom = randomSubject(subjects);
        Subject subjectTo = randomSubjectExcluding(subjects, subjectFrom);

        int minimumDays =
                context.minimumDaysPerSubject()
                        .getOrDefault(subjectFrom, 1);

        if (genes.get(subjectFrom) <= minimumDays) {
            return false;
        }

        genes.computeIfPresent(subjectFrom, (s, d) -> d - 1);
        genes.computeIfPresent(subjectTo, (s, d) -> d + 1);

        return true;
    }
}
