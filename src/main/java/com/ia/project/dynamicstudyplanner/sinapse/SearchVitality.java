package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.Population;

import java.util.HashSet;
import java.util.Set;

/**
 * Sinais de que a busca <b>buscou</b>, publicados na resposta para que um medidor possa cobrá-los.
 *
 * <h2>Por que isto é invariante e não métrica</h2>
 *
 * Uma busca genética pode rodar o orçamento inteiro sem buscar nada. Foi o que G14 e G15 encontraram:
 * com a fitness presa em zero para toda a população, o torneio compara zeros e <b>escolhe ao acaso</b>
 * — o algoritmo deixa de buscar e passa a vagar. O pior de tudo é que nada nos números denuncia:
 * o plano sai válido, as métricas de resultado saem plausíveis, e um relatório conclui "sem
 * diferença" por um motivo que não tem relação com o que estava sob teste.
 *
 * <p>Isso é <b>validade</b>, não qualidade: uma execução em que a busca não buscou não é uma execução
 * pior, é uma execução que não mede o que diz medir. Por isso vive ao lado de zero inversões
 * {@code HARD} e da reprodutibilidade, e não ao lado de cobertura e utilização.
 *
 * @param initialDistinctFitness quantos valores de aptidão <b>distintos</b> a população inicial tinha.
 *                               Um só significa que a geração zero não tinha nada sobre o que
 *                               selecionar: é exatamente a assinatura do defeito de G14/G15
 * @param initialBest            a melhor aptidão da população inicial
 * @param finalBest              a melhor aptidão da última geração. Com elitismo não pode ser pior
 *                               que a inicial, e se for, o elitismo quebrou
 */
public record SearchVitality(int initialDistinctFitness, double initialBest, double finalBest) {

    /** Chave sob a qual {@link #initialDistinctFitness} é publicado. */
    public static final String DISTINCT_KEY = "search-initial-distinct-fitness";

    /** Chave sob a qual {@link #initialBest} é publicado. */
    public static final String INITIAL_KEY = "search-initial-best";

    /** Chave sob a qual {@link #finalBest} é publicado. */
    public static final String FINAL_KEY = "search-final-best";

    /**
     * Lê os sinais de uma busca.
     *
     * <p>A aptidão vem de {@code Individual.getFitness()}, que está em cache: ler aqui não reavalia
     * nada e não consome sorteio nenhum, então acrescentar esta leitura <b>não move</b> o plano de
     * nenhum motor. É o que permite introduzir a invariante sem contaminar um resultado publicado.
     *
     * @param initial a população inicial, já com a aptidão calculada
     * @param last    a população da última geração
     * @return os sinais
     */
    public static SearchVitality of(Population initial, Population last) {
        Set<Double> distinct = new HashSet<>();
        for (int index = 0; index < initial.getSize(); index++) {
            distinct.add(initial.getIndividual(index).getFitness());
        }
        return new SearchVitality(distinct.size(),
                best(initial), best(last));
    }

    private static double best(Population population) {
        Individual fittest = population.getFittest();
        return fittest == null ? 0.0 : fittest.getFitness();
    }

    /**
     * Se a busca melhorou estritamente sobre a população inicial.
     *
     * <p><b>Não</b> faz parte da invariante, e é importante dizer por quê: numa instância pequena a
     * população inicial pode já conter o ótimo alcançável, e aí não melhorar é convergência e não
     * inércia. A invariante é {@link #initialDistinctFitness} maior que um — que diz que havia sobre
     * o que selecionar — mais {@link #finalBest} não pior que {@link #initialBest}. A melhora estrita
     * é relatada como secundária.
     *
     * @return se houve melhora estrita
     */
    public boolean improved() {
        return finalBest > initialBest;
    }
}
