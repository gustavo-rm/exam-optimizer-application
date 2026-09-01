package com.ia.project.dynamicstudyplanner.ga.strategy.selection;
import org.springframework.stereotype.Component;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.Population;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
/**
 * Implements the Tournament Selection strategy, a robust method for choosing parent individuals.
 * <p>
 * This strategy simulates a "tournament" by randomly selecting a small subset of individuals
 * from the main population. The individual with the highest fitness within this subset is
 * declared the winner and is chosen for reproduction. This method is highly effective
 * and is generally preferred over other methods like Roulette Wheel selection because it
 * reduces the risk of premature convergence caused by a few "superstar" individuals dominating
 * the selection process.
 */
@Component
public class TournamentSelection implements SelectionStrategy {
    private final int tournamentSize;
    /**
     * Constructs a TournamentSelection strategy with a default tournament size.
     * The size is set to 3 to provide balanced selection pressure for standard configurations.
     */
    public TournamentSelection() {
        this.tournamentSize = 3;
    }

    /**
     * Constructs a TournamentSelection strategy with a specified tournament size.
     * <p>
     * The {@code tournamentSize} is a critical parameter that controls the **selection pressure**.
     * <ul>
     * <li>A **small** size (e.g., 2 or 3) results in lower selection pressure, giving less fit
     * individuals a better chance to be selected. This promotes genetic diversity but may
     * slow down convergence.</li>
     * <li>A **large** size (e.g., 5 or 7) results in higher selection pressure, strongly
     * favoring the fittest individuals. This speeds up convergence but increases the risk of
     * getting stuck in a local optimum.</li>
     * </ul>
     *
     * @param tournamentSize The number of individuals to compete in each tournament. A value between 3 and 5 is common.
     */
    public TournamentSelection(int tournamentSize) {
        if (tournamentSize < 1) {
            throw new IllegalArgumentException("Tournament size must be at least 1.");
        }
        this.tournamentSize = tournamentSize;
    }
    /**
     * Selects a single individual from the population using the tournament method.
     * <p>
     * This implementation is optimized to avoid creating a new Population object for each selection.
     * It directly compares the randomly chosen contenders to find the winner.
     *
     * @param population The population from which to select an individual.
     * @return The winning Individual from the tournament.
     */
    @Override
    public Individual select(Population population) {
        if (population.getSize() == 0) {
            return null; // Cannot select from an empty population.
        }
        // 1. Select the first contender as the initial "best" individual.
        Individual bestContender = population.getIndividual(RandomProvider.getInstance().nextInt(population.getSize()));
        // 2. Loop through the rest of the tournament contenders.
        // We start the loop from 1 since we already have the first contender.
        for (int i = 1; i < tournamentSize; i++) {
            Individual currentContender = population.getIndividual(RandomProvider.getInstance().nextInt(population.getSize()));
            // 3. If the current contender is fitter, it becomes the new best.
            if (currentContender.getFitness() > bestContender.getFitness()) {
                bestContender = currentContender;
            }
        }
        // 4. Return the winner of the tournament.
        return bestContender;
    }
}
