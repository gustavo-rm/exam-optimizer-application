package com.ia.project.dynamicstudyplanner.benchmark.strategy;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Control baseline — draw {@code populationSize} random feasible plans and keep the best.
 * <p>
 * This is not one of the three baselines the task asked for; it is a control that isolates a
 * question none of the others answer: <b>what does the evolution itself contribute?</b>
 * <p>
 * Drawing {@code populationSize} random plans and scoring them is exactly what
 * {@code DefaultPopulationGenerator} does before generation zero. So the gap between this baseline
 * and the production GA is precisely the value added by every generation of selection, crossover,
 * mutation, elitism and hypermutation that follows. A GA that barely improves on its own starting
 * population is paying for machinery it is not using.
 */
public final class BestOfRandomBaseline implements PlanningStrategy {

    private final PlanningStrategy singleDraw = new RandomBaseline();

    @Override
    public String id() {
        return "melhor-de-n-aleatorios";
    }

    @Override
    public String displayName() {
        return "Melhor de N aleatorios (N = populacao inicial do AG)";
    }

    @Override
    public boolean deterministic() {
        return false;
    }

    @Override
    public StudyPlan plan(BenchmarkInstance instance, EvolutionContext context, long seed) {
        Random seedSource = new Random(seed);
        List<Subject> subjects = Allocations.orderedSubjects(context);

        StudyPlan best = null;
        double bestFitness = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < instance.populationSize(); i++) {
            StudyPlan candidate = singleDraw.plan(instance, context, seedSource.nextLong());
            double fitness = context.fitnessEvaluator().evaluate(candidate, context);
            if (fitness > bestFitness) {
                bestFitness = fitness;
                best = candidate;
            }
        }

        if (best == null) {
            // Only reachable with a population size of zero, which the API forbids (@Min(10)).
            return new StudyPlan(Allocations.atMinimums(subjects, context));
        }
        return best;
    }
}
