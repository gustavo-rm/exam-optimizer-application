package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Represents a single individual in the population.
 * <p>
 * An Individual acts as a wrapper for a potential solution (the {@link StudyPlan}, or chromosome)
 * and its evaluated quality score (the fitness). This class is the core object upon which
 * all genetic operators (selection, crossover, mutation) act. It implements {@link Comparable}
 * to allow for easy sorting and selection based on fitness.
 */
@Getter
public final class Individual implements Comparable<Individual> {

    private static final Logger log = LoggerFactory.getLogger(Individual.class);

    private final StudyPlan plan;
    @Setter
    private double fitness = -1.0; // Cached fitness score. -1.0 indicates it has not been calculated yet.

    /**
     * Constructs a new Individual with a given study plan.
     * The fitness is initialized to a non-calculated state.
     *
     * @param plan The {@link StudyPlan} (chromosome) that this individual represents.
     */
    public Individual(StudyPlan plan) {
        this.plan = plan;
    }

    /**
     * Calculates the fitness of this individual based on a given context.
     * <p>
     * This method delegates the calculation to the injected {@link com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator}
     * provided in the context, allowing for a fully modular Multi-Objective Optimization pipeline.
     *
     * @param context The {@link EvolutionContext} containing all data needed for the calculation,
     * including the configured fitness pipeline.
     * @return The calculated fitness score.
     */
    public double calculateFitness(EvolutionContext context) {
        if (context.fitnessEvaluator() == null) {
            throw new IllegalStateException("FitnessEvaluator not provided in EvolutionContext");
        }
        return context.fitnessEvaluator().evaluate(this.plan, context);
    }

    /**
     * Compares this individual with another based on their fitness score for sorting purposes.
     * The comparison is for **descending order** (higher fitness is considered "less than"
     * for sorting, making it appear first).
     *
     * @param other The other Individual to be compared.
     * @return A negative integer, zero, or a positive integer as this individual is greater than,
     * equal to, or less than the specified individual.
     */
    @Override
    public int compareTo(Individual other) {
        return Double.compare(other.getFitness(), this.getFitness());
    }

    // --- Standard Getters and Setters ---

}
