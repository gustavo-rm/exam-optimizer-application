package com.ia.project.dynamicstudyplanner.ga.fitness;

import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.ConstraintValidator;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FitnessPenalty;

import java.util.List;

/**
 * Which terms a fitness function is made of, declared per path.
 *
 * <h2>Why the composition stopped being global</h2>
 *
 * There was one aggregate, assembled by Spring from every bean that implemented a term interface.
 * That works while every caller has the same data. It stops working the moment one caller has
 * <b>less</b>: the SINAPSE path receives no psychological state, and two of the terms are built on
 * it. Left in the aggregate they do not fail — they return their neutral value, {@code 1.0} for a
 * multiplicative penalty — and the published fitness then reports a plan judged on fatigue and
 * dropout risk by a function that considered neither.
 *
 * <p><b>An inert term that appears active is worse than an absent one.</b> An absent term is a
 * documented limitation; an inert one is a false claim, and it is false in the direction that
 * flatters the system. So the term list became a property of the path, and a path declares what it
 * can actually compute.
 *
 * <h2>What a composition guarantees</h2>
 *
 * The objective weights sum to 1, asserted by {@link FitnessEvaluator} at construction, so every
 * path's aggregate is in {@code [0,1]} and the two conditions of the experiment stay comparable.
 * A path that drops an objective renormalises the rest; a path that drops only multiplicative
 * penalties changes no weight, because those are not summands. {@code FitnessBreakdown} is built by
 * walking these same lists, so a term absent from a composition is absent from the published
 * decomposition — it does not appear with a zero.
 *
 * @param path       which path this composition belongs to, as it appears in the published fitness
 * @param objectives the weighted summands, in evaluation order
 * @param constraints the graded severities subtracted from the sum
 * @param penalties  the multiplicative factors applied after the clamp; may be empty
 */
public record FitnessComposition(
        String path,
        List<FitnessObjective> objectives,
        List<ConstraintValidator> constraints,
        List<FitnessPenalty> penalties) {

    /** Defensive copies: a composition is read once per evaluation and must not shift under one. */
    public FitnessComposition {
        objectives = List.copyOf(objectives);
        constraints = List.copyOf(constraints);
        penalties = List.copyOf(penalties);
    }

    /**
     * The evaluator for this composition.
     *
     * @return an evaluator over exactly these terms, in exactly this order
     */
    public FitnessEvaluator evaluator() {
        return new FitnessEvaluator(objectives, penalties, constraints);
    }
}
