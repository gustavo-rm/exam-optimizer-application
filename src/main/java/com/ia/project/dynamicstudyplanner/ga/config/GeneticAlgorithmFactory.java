package com.ia.project.dynamicstudyplanner.ga.config;

import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithm;

/**
 * Interface for creating instances of GeneticAlgorithm.
 * Used to abstract the construction logic and decouple orchestrator classes
 * from concrete GA configurations.
 */
public interface GeneticAlgorithmFactory {

    /**
     * Creates and configures a new instance of GeneticAlgorithm.
     * @return A configured GeneticAlgorithm engine.
     */
    GeneticAlgorithm create();
}
