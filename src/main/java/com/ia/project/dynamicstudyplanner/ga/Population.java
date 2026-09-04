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
        if (individuals.isEmpty()) {
            return null;
        }
        return individuals.stream()
                .max(Comparator.comparingDouble(Individual::getFitness))
                .orElse(null);
    }

    /**
     * Gets the least fit individual in the population.
     * @return The individual with the lowest fitness score.
     */
    public Individual getWorst() {
        if (individuals.isEmpty()) {
            return null;
        }
        return individuals.stream()
                .min(Comparator.comparingDouble(Individual::getFitness))
                .orElse(null);
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

        return individuals.stream()
                .mapToDouble(Individual::getFitness)
                .average()
                .orElse(0.0);
    }

    /**
     * Gets the current number of individuals in the population.
     * @return The size of the population.
     */
    public int getSize() {
        return this.individuals.size();
    }

    /**
     * Calcula e atribui a fitness de cada indivíduo da população.
     *
     * <h2>Por que isto é sequencial (pendência P17, resolvida)</h2>
     *
     * A avaliação de fitness é o trecho naturalmente paralelizável do algoritmo: a nota de um
     * indivíduo depende apenas dele e do contexto, nunca dos vizinhos. Por muito tempo ela usou
     * {@code parallelStream()}, e a etapa 05b ainda a manteve, condicionada a um limiar de 64
     * indivíduos (achado F3).
     *
     * <p>Isso deixou uma pendência aberta: o {@code parallelStream} usa o {@code ForkJoinPool}
     * comum, compartilhado por todo o processo, então a paralelização de <b>uma</b> otimização
     * disputava CPU com as <b>outras</b> requisições — o achado F6 mediu que a fitness sequencial
     * dava 13 % mais vazão com 8 requisições simultâneas. A pendência P17 propunha resolver isso
     * com um pool próprio, dimensionado junto com o executor de requisições.
     *
     * <h2>A medição respondeu outra coisa: não há o que dimensionar</h2>
     *
     * Antes de escolher o tamanho de um pool, foi medido quanto a paralelização ainda compra. Em
     * processo, 5 baterias pareadas de 21 execuções cada, com <b>população 500 — o máximo que o
     * contrato aceita</b>:
     *
     * <pre>
     *   bateria    sequencial   paralela
     *         1        140 ms     145 ms
     *         2        146 ms     142 ms
     *         3        143 ms     144 ms
     *         4        148 ms     144 ms
     *         5        148 ms     139 ms
     *   mediana        146 ms     144 ms    (+1,4 %, vencendo 3 de 5)
     * </pre>
     *
     * <p>E abaixo do máximo o quadro é o mesmo ou pior: em população 150 a paralela chegou a ser
     * <b>11 % mais lenta</b>. Não existe tamanho de população, dentro do que a API aceita, em que
     * a paralelização pague de forma consistente.
     *
     * <h2>Por que o ganho de 12 % da etapa 05b evaporou</h2>
     *
     * Não é contradição de medida: <b>uma correção anterior removeu a justificativa desta</b>. O
     * achado F8 trocou a iteração dos genes de {@code entrySet()} para {@code forEach}, e isso
     * tornou a avaliação de um indivíduo cerca de 4,5× mais barata (61 ns contra 272 ns por
     * percurso). Com muito menos trabalho por indivíduo, o custo de repartir entre threads e
     * recolher o resultado passou a consumir o que a paralelização economizava.
     *
     * <p>Remover é melhor que dimensionar: some o limiar, some a disputa com as requisições — o
     * achado F6 deixa de ser parcialmente mitigado e passa a não existir — e some um segundo botão
     * de configuração que alguém teria de manter coerente com o primeiro.
     *
     * @param context dados da evolução: importâncias, pisos de dias e a função de fitness
     */
    public void calculateFitness(EvolutionContext context) {
        for (Individual individual : individuals) {
            individual.setFitness(individual.calculateFitness(context));
        }
    }
}
