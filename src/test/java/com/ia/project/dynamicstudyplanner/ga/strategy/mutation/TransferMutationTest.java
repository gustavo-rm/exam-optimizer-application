package com.ia.project.dynamicstudyplanner.ga.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransferMutationTest {

    private final TransferMutation mutation = new TransferMutation();

    @Test
    void shouldSwapDaysBetweenSubjectsMaintainingTotalDays() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        Subject history = new Subject("History", 10, 3);
        // LinkedHashMap e nao Map.of: a ordem de iteracao de Map.of e sorteada a cada execucao da
        // JVM, e desde a pendencia P18 essa ordem e a ordem dos genes do cromossomo. Com ela
        // sorteada, qual disciplina cede o dia mudaria de execucao para execucao.
        Map<Subject, Integer> dias = new LinkedHashMap<>();
        dias.put(math, 10);
        dias.put(history, 5);
        StudyPlan plan = new StudyPlan(dias);
        Individual individual = new Individual(plan);
        // A mutacao por transferencia so olha o plano e o orcamento: nao precisa de estado do
        // aluno, de fitness, de retencao nem de datas. Antes da etapa 03c isso era escrito como
        // cinco null consecutivos; agora os campos que nao importam sao simplesmente omitidos.
        //
        // As disciplinas, porem, passaram a ser necessarias: com o cromossomo indexado (P18) e o
        // contexto que declara a ordem dos genes, um contexto sem disciplinas descreve uma
        // evolucao sem genes.
        EvolutionContext context = EvolutionContext.builder()
                .importanceScores(Map.of())
                .subjects(List.of(math, history))
                .minimumDaysPerSubject(Map.of())
                .planningHorizonDays(180)
                .hoursPerStudyDay(4)
                .maxDailyCognitiveLoad(20)
                .build();

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
