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
     * This method is a pure calculator; it computes the fitness score but does not
     * modify the individual's state. The result should be set using {@link #setFitness(double)}.
     *
     * @param context The {@link EvolutionContext} containing all data needed for the calculation,
     * such as importance scores and minimum day constraints.
     * @return The calculated fitness score.
     */
    public double calculateFitness(EvolutionContext context) {
        // Step 1: Calculate the base score from the plan's knowledge acquisition.
        double baseScore = calculateBaseScore(context.importanceScores());

        // Step 2: Check if the plan violates any minimum day constraints.
        // We now delegate to the domain object rather than calculating here.
        boolean meetsConstraints = plan.meetsMinimumConstraints(context.minimumDaysPerSubject());

        // Step 3: Apply a penalty to the score if constraints were violated.
        return applyPenalty(baseScore, !meetsConstraints);
    }

    /**
     * Calculates the base fitness score by summing the "knowledge" gained for each subject,
     * weighted by that subject's importance.
     *
     * @param importanceScores A map of subjects to their importance scores.
     * @return The raw, unpenalized fitness score.
     */
    private double calculateBaseScore(Map<Subject, Double> importanceScores) {
        double score = 0.0;
        for (Map.Entry<Subject, Integer> entry : plan.getDaysPerSubject().entrySet()) {
            Subject subject = entry.getKey();
            int days = entry.getValue();

            double importance = importanceScores.getOrDefault(subject, 0.0);
            if (importance == 0.0) {
                log.warn("The subject '{}' does not have an importance score.", subject.name());
            }

            // A logarithmic function models the diminishing returns of studying.
            double knowledge = Math.log(1.0 + days);
            score += knowledge * importance;
        }
        return score;
    }

    /**
     * Applies a penalty to the base score if constraints were violated.
     *
     * @param score The base fitness score.
     * @param isViolated A boolean indicating if a violation occurred.
     * @return The final, potentially penalized fitness score.
     */
    private double applyPenalty(double score, boolean isViolated) {
        if (isViolated) {
            // A multiplicative penalty is often effective. Here we reduce fitness by 50%.
            return score * 0.5;
        }
        return score;
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
