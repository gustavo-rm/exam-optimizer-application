package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * How much search the genetic engine is allowed: generations and population size.
 *
 * <h2>Why the two travel together</h2>
 *
 * Neither number means anything alone — the work done is their product, and a change to one is only
 * readable next to the other. They were two constructor parameters of {@code GeneticPlanEngine}
 * until the prerequisite stage needed a resolver of its own and the constructor reached the limit
 * this repository's Checkstyle sets at eight. Grouping the pair that was already one concept is the
 * change that keeps the limit doing its job rather than being argued down.
 *
 * <p>There is no code default for either: a deployment that cannot say how long its search ran
 * fails at startup rather than recording plans nobody can reproduce.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class GeneticSearchBudget {

    private final int generations;
    private final int populationSize;

    public GeneticSearchBudget(
            @Value("${plan.engine.ga.generations}") int generations,
            @Value("${plan.engine.ga.population-size}") int populationSize) {

        this.generations = generations;
        this.populationSize = populationSize;
    }

    /** @return how many generations to evolve; reported as {@code metadata.generations} */
    public int generations() {
        return generations;
    }

    /** @return how many individuals per generation */
    public int populationSize() {
        return populationSize;
    }
}
