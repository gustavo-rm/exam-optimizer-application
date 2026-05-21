package com.ia.project.dynamicstudyplanner.ga.generator;

import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Population;

/**
 * Interface for generating the initial population for the genetic algorithm.
 */
public interface PopulationGenerator {

    /**
     * Creates an initial randomized population and calculates its initial fitness.
     *
     * @param exam           The exam structure.
     * @param totalDays      Total days available.
     * @param populationSize Size of the population to generate.
     * @param context        The evolution context (constraints and importance scores).
     * @return The initial calculated Population.
     */
    Population generate(Exam exam, int totalDays, int populationSize, EvolutionContext context);
}
