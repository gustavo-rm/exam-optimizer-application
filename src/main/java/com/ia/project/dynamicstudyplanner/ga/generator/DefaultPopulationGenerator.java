package com.ia.project.dynamicstudyplanner.ga.generator;

import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.Population;
import com.ia.project.dynamicstudyplanner.ga.factory.StudyPlanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the PopulationGenerator.
 */
@Component
public class DefaultPopulationGenerator implements PopulationGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultPopulationGenerator.class);

    private final StudyPlanFactory planFactory;

    public DefaultPopulationGenerator() {
        this.planFactory = new StudyPlanFactory();
    }

    @Override
    public Population generate(Exam exam, int totalDays, int populationSize, EvolutionContext context) {
        Population population = new Population(populationSize);
        var allSubjects = exam.getAllSubjects();

        for (int i = 0; i < populationSize; i++) {
            population.addIndividual(new Individual(planFactory.createRandomPlan(
                    context.geneVectors().index(), allSubjects,
                    totalDays, context.minimumDaysPerSubject())));
        }

        population.calculateFitness(context);
        log.debug("Initial Population created. Best fitness: {}", population.getFittest().getFitness());
        return population;
    }
}
