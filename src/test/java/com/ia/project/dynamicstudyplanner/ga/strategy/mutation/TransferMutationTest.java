package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransferMutationTest {

    private final TransferMutation mutation = new TransferMutation();

    @Test
    void shouldSwapDaysBetweenSubjectsMaintainingTotalDays() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        Subject history = new Subject("History", 10, 3);
        StudyPlan plan = new StudyPlan(Map.of(math, 10, history, 5));
        Individual individual = new Individual(plan);
        EvolutionContext context = new EvolutionContext(Map.of(), Map.of(), null, null, null, null, null);

        // Act - Force mutation with 1.0 rate
        Individual mutated = mutation.mutate(individual, 1.0, context);

        // Assert
        int totalDaysOriginal = plan.getTotalDays();
        int totalDaysMutated = mutated.getPlan().getTotalDays();

        assertThat(totalDaysMutated).isEqualTo(totalDaysOriginal); // Invariant check

        // One subject gained a day, the other lost a day.
        // It could be math=9, history=6 OR math=11, history=4 depending on random selection
        int mathDays = mutated.getPlan().getDaysPerSubject().get(math);
        int historyDays = mutated.getPlan().getDaysPerSubject().get(history);

        assertThat(mathDays + historyDays).isEqualTo(15);
        assertThat(mathDays).isIn(9, 11);
        assertThat(historyDays).isIn(4, 6);
    }
}
