package com.ia.project.dynamicstudyplanner.ga.fitness;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;

/**
 * An objective carrying a weight its own class did not choose.
 *
 * <h2>Why the weight has to be separable from the objective</h2>
 *
 * {@code FitnessObjective.getWeight()} returns a constant from {@link FitnessWeights}, which is
 * correct as long as there is <b>one</b> composition: the weights are a property of the aggregate,
 * and with a single aggregate the distinction is invisible. With one composition per path it stops
 * being invisible — the same {@code ScoreGainObjective} instance is a term in both, and the two
 * paths may weigh it differently, because a path that drops a term has to renormalise the rest so
 * the aggregate stays in {@code [0,1]} and stays comparable.
 *
 * <p>This decorator is the seam. It delegates the arithmetic and the name to the objective and
 * answers {@link #getWeight()} with the weight the <b>composition</b> declared. A composition whose
 * terms are unweighted uses the objectives directly and keeps their own constants, so the concurso
 * path is unchanged by the existence of this class.
 *
 * @param objective the term being weighed
 * @param weight    its share in this composition
 */
public record WeightedObjective(FitnessObjective objective, double weight)
        implements FitnessObjective {

    @Override
    public double calculateReward(StudyPlan plan, EvolutionContext context) {
        return objective.calculateReward(plan, context);
    }

    @Override
    public double getWeight() {
        return weight;
    }

    /** The wrapped term's name: the decoration is not part of the published decomposition. */
    @Override
    public String name() {
        return objective.name();
    }
}
