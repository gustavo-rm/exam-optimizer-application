package com.ia.project.dynamicstudyplanner.ga.strategy.crossover;
import org.springframework.stereotype.Component;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import java.util.Random;
/**
 * Implements a hybrid crossover strategy that dynamically chooses between
 * an exploitative and an explorative crossover method. This helps maintain
 * a healthy balance between refining good solutions and exploring new parts
 * of the search space, effectively combating premature convergence.
 */
@Component
public class HybridCrossover implements CrossoverStrategy {
    private final CrossoverStrategy exploitationStrategy; // Para refinar (ex: Média Ponderada)
    private final CrossoverStrategy explorationStrategy;  // Para explorar (ex: Reparo)
    private final double exploitationChance;
    private final Random random = new Random();
    public HybridCrossover(WeightedAverageCrossover exploitationStrategy, RepairingCrossover explorationStrategy) {
        this.exploitationStrategy = exploitationStrategy;
        this.explorationStrategy = explorationStrategy;
        this.exploitationChance = 0.75;
    }
    @Override
    public Individual crossover(Individual parent1, Individual parent2, double crossoverRate, EvolutionContext context) {
        // Primeiro, decide se o crossover vai acontecer
        if (random.nextDouble() > crossoverRate) {
            return parent1.getFitness() > parent2.getFitness() ? new Individual(parent1.getPlan()) : new Individual(parent2.getPlan());
        }
        // Se acontecer, escolhe qual estratégia usar
        if (random.nextDouble() < exploitationChance) {
            // Usa a estratégia de refinamento
            return exploitationStrategy.crossover(parent1, parent2, 1.0, context); // Passa 1.0 pois já decidimos cruzar
        } else {
            // Usa a estratégia de exploração
            return explorationStrategy.crossover(parent1, parent2, 1.0, context);
        }
    }
}
