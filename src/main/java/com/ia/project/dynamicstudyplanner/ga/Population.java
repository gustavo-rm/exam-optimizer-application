package com.ia.project.dynamicstudyplanner.ga;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents a population of individuals (potential solutions).
 * This class manages the collection of individuals for a single generation.
 */
public class Population {
    private final List<Individual> individuals;

    /**
     * Constructs a new, empty population with a predefined initial capacity.
     * This is efficient for building a population by adding individuals one by one,
     * as it pre-allocates memory for the underlying list.
     *
     * @param populationSize The capacity of the population.
     */
    public Population(int populationSize) {
        // Initialize the list with a specific capacity to improve performance
        this.individuals = new ArrayList<>(populationSize);
    }

    /**
     * Constructs a population from an existing list of individuals.
     * This is useful for creating a new generation from a pre-built list of offspring.
     *
     * @param individuals The list of individuals to comprise the population.
     */
    public Population(List<Individual> individuals) {
        this.individuals = individuals;
    }

    /**
     * Adds a single individual to the population.
     *
     * @param individual The individual to be added.
     */
    public void addIndividual(Individual individual) {
        this.individuals.add(individual);
    }

    /**
     * Gets an individual at a specific index in the population.
     * @param index The index of the individual to retrieve.
     * @return The Individual at the specified index.
     */
    public Individual getIndividual(int index) {
        return this.individuals.get(index);
    }

    /**
     * Gets the fittest individual in the population.
     * This method assumes fitness has already been calculated for all individuals.
     * @return The individual with the highest fitness score.
     */
    public Individual getFittest() {
        if (individuals.isEmpty()) return null;
        Individual fittest = individuals.get(0);
        for (int i = 1; i < individuals.size(); i++) {
            Individual ind = individuals.get(i);
            if (ind.getFitness() > fittest.getFitness()) {
                fittest = ind;
            }
        }
        return fittest;
    }

    /**
     * Gets the least fit individual in the population.
     * @return The individual with the lowest fitness score.
     */
    public Individual getWorst() {
        if (individuals.isEmpty()) return null;
        Individual worst = individuals.get(0);
        for (int i = 1; i < individuals.size(); i++) {
            Individual ind = individuals.get(i);
            if (ind.getFitness() < worst.getFitness()) {
                worst = ind;
            }
        }
        return worst;
    }

    /**
     * Calculates the average fitness of the entire population.
     * This is a key indicator of the population's overall health and convergence.
     * @return The average fitness score as a double.
     */
    public double getAverageFitness() {
        if (individuals.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (Individual individual : individuals) {
            sum += individual.getFitness();
        }
        return sum / individuals.size();
    }

    /**
     * Gets the current number of individuals in the population.
     * @return The size of the population.
     */
    public int getSize() {
        return this.individuals.size();
    }

    /**
     * Calculates and sets the fitness for every individual in the population
     * using the provided importance scores.
     *
     * @param context An EvolutionContext object containing all contextual data required for
     * the evolution, such as:
     * - Importance scores for fitness calculation.
     * - Minimum day constraints for crossover repair logic.
     */
    public void calculateFitness(EvolutionContext context) {
        // Parallel streams have too much overhead for fast, simple inner loop math,
        // leading to excessive GC allocations (Spliterators, ForkJoinTasks) and thread contention
        // when already running inside an Async Executor.
        // Reverting to traditional fast iterative loop.
        for (int i = 0; i < individuals.size(); i++) {
            Individual individual = individuals.get(i);
            double score = individual.calculateFitness(context);
            individual.setFitness(score);
        }
    }
}
