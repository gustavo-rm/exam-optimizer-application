package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.ga.strategy.crossover.CrossoverStrategy;
import com.ia.project.dynamicstudyplanner.ga.strategy.mutation.MutationStrategy;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main engine for the Genetic Algorithm, featuring adaptive logic to detect and react to population stagnation.
 * <p>
 * This class is responsible for taking a population of solutions and evolving it to the next
 * generation. It orchestrates the core evolutionary processes: selection, crossover, and mutation,
 * and intelligently adapts its strategy when it detects that the population is no longer improving.
 */
public final class GeneticAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(GeneticAlgorithm.class);

    // --- Core Strategy Dependencies ---
    private final SelectionStrategy selectionStrategy;
    private final CrossoverStrategy crossoverStrategy;
    private final MutationStrategy mutationStrategy;

    // --- Core GA Parameters ---
    private final double crossoverRate;
    private final double baseMutationRate;
    private final boolean elitism;

    // --- Adaptive Strategy Parameters ---
    private final int stagnationPatience;
    private final double hypermutationRate;

    // --- Internal Evolution State ---
    private int generationsWithoutImprovement = 0;
    private double bestFitnessSoFar = Double.NEGATIVE_INFINITY;
    private boolean inHypermutationMode = false;
    private int hypermutationCounter = 0;

    /**
     * Constructs the GeneticAlgorithm engine. (Package-private to be called by the GeneticAlgorithmBuilder).
     */
    GeneticAlgorithm(SelectionStrategy sel, CrossoverStrategy cross, MutationStrategy mut,
                     double crossRate, double mutRate, boolean elitism,
                     int stagnationPatience, double hypermutationRate) {
        this.selectionStrategy = sel;
        this.crossoverStrategy = cross;
        this.mutationStrategy = mut;
        this.crossoverRate = crossRate;
        this.baseMutationRate = mutRate;
        this.elitism = elitism;
        this.stagnationPatience = stagnationPatience;
        this.hypermutationRate = hypermutationRate;
    }

    /**
     * Evolves a given population to the next generation.
     * <p>
     * This is the primary public method of the class. It orchestrates the entire evolution
     * process for a single generation step.
     *
     * @param currentPopulation The population from the previous generation to be evolved.
     * @param context An EvolutionContext object containing all data required for the evolution.
     * @return The new, evolved population for the next generation.
     */
    public Population evolvePopulation(Population currentPopulation, EvolutionContext context) {
        trackStagnation(currentPopulation.getFittest().getFitness());
        double currentMutationRate = determineCurrentMutationRate();

        Population newPopulation = new Population(currentPopulation.getSize());

        // 1. Apply elitism: preserve the best individual from the current generation.
        if (elitism) {
            newPopulation.addIndividual(currentPopulation.getFittest());
        }

        // 2. Preenche o resto da nova populacao com descendentes.
        //
        // ESTE LACO E SEQUENCIAL DE PROPOSITO. Ele e o trecho mais caro do algoritmo — 817 ms de
        // 1592 ms no pior caso medido, 51 % do tempo de parede — e a tentacao obvia e paralelizar.
        // Foi tentado e medido na etapa 05b (achado F2), e nao pode ser feito assim:
        //
        //   - selecao, crossover e mutacao consomem UMA fonte de aleatoriedade compartilhada, e o
        //     numero de sorteios por descendente varia. Nao da para repartir a sequencia entre
        //     threads sem saber de antemao quantos numeros cada um vai consumir.
        //   - com o laco paralelo, DUAS EXECUCOES COM A MESMA SEMENTE deixam de coincidir: quem
        //     pega qual numero passa a depender do escalonamento. GaResultadoInalteradoTest reprova
        //     (2 dos 4 testes), e as 9 assinaturas de referencia mudam todas.
        //   - e o ganho nem seria consistente: 1244 -> 1088 ms no pior caso (12 %), mas 50 -> 83 ms
        //     em 15 disciplinas / 500 geracoes / populacao 50 — 66 % MAIS LENTO, pelo mesmo motivo
        //     que levou ao limiar de Population.calculateFitness.
        //
        // O custo real estava na representacao do cromossomo, e a pendencia P18 o resolveu: o plano
        // deixou de ser Map<Subject, Integer> e passou a ser um int[] alinhado a uma ordem canonica
        // compartilhada (ver StudyPlan e SubjectIndex). Sumiram o hash, o boxing e a alocacao de no
        // por gene — ~72 ns por gene sobre 474 mil recombinacoes. Medido ponta a ponta, com corpo de
        // populacao 200: a latencia mediana caiu de 94 para 35 ms e a vazao com 16 concorrentes
        // subiu de 61 para 146 req/s.
        //
        // O laco de reparo, o suspeito obvio, foi instrumentado e e gratuito (0,025 voltas por
        // chamada), e a pausa de GC e ~1 % do tempo — nao era por ali.
        //
        // A troca mudou o plano produzido uma vez, porque a ordem dos genes decide onde cai o ponto
        // de corte. A qualidade nao mudou: em 150 pares (5 configuracoes x 30 sementes), 62 vitorias
        // contra 52 derrotas nos 114 pares nao empatados, z = +0,94 no teste dos sinais, e toda a
        // dispersao dentro de +-0,13 %.
        int startIndex = elitism ? 1 : 0;
        for (int i = startIndex; i < currentPopulation.getSize(); i++) {
            Individual offspring = createOffspring(currentPopulation, context, currentMutationRate);
            newPopulation.addIndividual(offspring);
        }

        // 3. Calculate the fitness for all new individuals.
        newPopulation.calculateFitness(context);
        return newPopulation;
    }

    /**
     * Creates a single offspring by selecting parents, performing crossover, and applying mutation.
     * This method encapsulates the complete reproductive cycle.
     *
     * @param population The current population from which to select parents.
     * @param context The evolution context for constraints and data.
     * @param mutationRate The mutation rate to be applied for this generation.
     * @return A new Individual representing the created offspring.
     */
    private Individual createOffspring(Population population, EvolutionContext context, double mutationRate) {
        Individual parent1 = selectionStrategy.select(population);
        Individual parent2 = selectionStrategy.select(population);

        Individual child = crossoverStrategy.crossover(parent1, parent2, crossoverRate, context);

        return mutationStrategy.mutate(child, mutationRate, context);
    }

    /**
     * Monitors the evolution's progress and detects if the population has stagnated.
     * If the best fitness score has not improved for a set number of generations (patience),
     * it triggers the hypermutation mode.
     *
     * @param currentBestFitness The best fitness score in the current generation.
     */
    private void trackStagnation(double currentBestFitness) {
        if (currentBestFitness > bestFitnessSoFar) {
            bestFitnessSoFar = currentBestFitness;
            generationsWithoutImprovement = 0;
            if (inHypermutationMode) {
                inHypermutationMode = false;
                hypermutationCounter = 0;
                log.debug("Improvement found! Halting hypermutation.");
            }
        } else {
            generationsWithoutImprovement++;
        }

        if (generationsWithoutImprovement >= stagnationPatience && !inHypermutationMode) {
            log.debug("STAGNATION DETECTED! Triggering HYPERMUTATION for 5 generations.");
            inHypermutationMode = true;
            hypermutationCounter = 5;
            generationsWithoutImprovement = 0;
        }
    }

    /**
     * Determines which mutation rate to use for the current generation based on the evolution's state.
     * It returns the high {@code hypermutationRate} if in hypermutation mode, otherwise returns
     * the {@code baseMutationRate}.
     *
     * @return The effective mutation rate for the current generation.
     */
    private double determineCurrentMutationRate() {
        if (inHypermutationMode && hypermutationCounter > 0) {
            hypermutationCounter--;
            if (hypermutationCounter == 0) {
                log.debug("Hypermutation finished. Returning to normal mutation rate.");
                inHypermutationMode = false;
            }
            return hypermutationRate;
        }
        return baseMutationRate;
    }
}
