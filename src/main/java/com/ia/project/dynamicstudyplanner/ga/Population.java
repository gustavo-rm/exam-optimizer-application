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
     * Tamanho a partir do qual avaliar em paralelo compensa.
     *
     * <p>Não é um palpite. A etapa 05b mediu os dois caminhos variando a população, com 200
     * gerações e 15 disciplinas (medianas de 15 execuções):
     *
     * <pre>
     *   população   paralela   sequencial   vencedor
     *          10      15 ms        10 ms   sequencial, por 50 %
     *          25      19 ms        16 ms   sequencial
     *          50      27 ms        26 ms   empate
     *         100      44 ms        47 ms   paralela
     *         200      85 ms        95 ms   paralela
     *         500     193 ms       219 ms   paralela, por 12 %
     * </pre>
     *
     * <p>Abaixo de ~50 indivíduos o custo de repartir o trabalho entre threads e recolher o
     * resultado supera a economia — a avaliação de um indivíduo é curta demais para pagar a
     * coordenação. O limiar fica em 64 por ser o primeiro valor redondo dentro da faixa em que a
     * paralela vence com folga.
     */
    private static final int TAMANHO_MINIMO_PARA_PARALELIZAR = 64;

    /**
     * Calcula e atribui a fitness de cada indivíduo da população.
     *
     * <h2>Por que a paralelização é condicional</h2>
     *
     * A avaliação de fitness é o trecho naturalmente paralelizável do algoritmo: a nota de um
     * indivíduo depende apenas dele e do contexto, nunca dos vizinhos. Por isso a ordem de execução
     * não altera o resultado — e é isso que permite paralelizar sem mudar o plano produzido.
     *
     * <p>Até a etapa 05b a paralelização era incondicional, e o achado F3 mostrou que isso custava
     * caro nas populações pequenas: com 10 indivíduos, a versão paralela era <b>50 % mais lenta</b>
     * que a sequencial. Como o DTO aceita populações de 10 a 500, boa parte da faixa admitida caía
     * no lado ruim. Ver {@link #TAMANHO_MINIMO_PARA_PARALELIZAR} para a tabela medida.
     *
     * <h2>O que isto não resolve</h2>
     *
     * O {@code parallelStream} usa o {@code ForkJoinPool} comum, que é compartilhado por todo o
     * processo. Sob concorrência de requisições, a paralelização de <b>uma</b> otimização disputa
     * CPU com as <b>outras</b> requisições — achado F6, medido: fitness sequencial dá 13 % mais
     * vazão com 8 requisições simultâneas, ao custo de 21 % de latência numa requisição isolada.
     * O limiar reduz essa disputa nas populações pequenas, mas não a elimina nas grandes. Fechar
     * isso de vez exigiria um pool próprio dimensionado junto com o executor de requisições —
     * registrado como pendência <b>P17</b>.
     *
     * @param context dados da evolução: importâncias, pisos de dias e a função de fitness
     */
    public void calculateFitness(EvolutionContext context) {
        if (individuals.size() >= TAMANHO_MINIMO_PARA_PARALELIZAR) {
            individuals.parallelStream().forEach(individual ->
                    individual.setFitness(individual.calculateFitness(context)));
            return;
        }
        for (Individual individual : individuals) {
            individual.setFitness(individual.calculateFitness(context));
        }
    }
}
