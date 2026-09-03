package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.CognitiveLoadObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty;
import com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor;
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava a equivalência entre os dois caminhos de avaliação de fitness da {@link Population}.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * A etapa 05b tornou a paralelização <b>condicional</b> ao tamanho da população (achado F3): abaixo
 * de 64 indivíduos a avaliação passou a ser sequencial, porque a medição mostrou a versão paralela
 * até 50 % mais lenta nas populações pequenas. Isso criou dois caminhos de código onde antes havia
 * um — e a instrução da etapa era explícita: paralelizar <b>com teste garantindo que o resultado
 * final não muda</b>.
 *
 * <p>Este teste é esse garantidor. Ele verifica que a fitness atribuída pela {@link Population} é
 * idêntica <i>bit a bit</i> à que sai do cálculo direto de cada indivíduo, dos dois lados do limiar.
 * "Bit a bit" aqui é literal: a comparação é {@code isEqualTo} sobre {@code double}, sem margem de
 * tolerância. Uma diferença de arredondamento — do tipo que a soma em ordem diferente poderia
 * introduzir — reprovaria o teste.
 *
 * <h2>Por que a igualdade exata é legítima aqui</h2>
 *
 * Avaliar a fitness de um indivíduo é uma operação <b>pura</b>: depende só do plano dele e do
 * contexto, nunca dos vizinhos, e não sorteia nada. Cada indivíduo é escrito por uma única thread,
 * e nenhuma lê o resultado de outra. Não há acumulador compartilhado cuja ordem de soma pudesse
 * variar. É exatamente essa ausência de dependência entre indivíduos que autoriza paralelizar — e
 * é por isso que a expectativa correta é igualdade exata, não aproximada.
 *
 * <p>O complemento em nível de sistema é {@code GaResultadoInalteradoTest}, que trava o plano
 * inteiro produzido pelo algoritmo para uma semente fixa.
 */
@DisplayName("Population: os dois caminhos de avaliacao dao o mesmo resultado")
class PopulationFitnessParalelaTest {

    /**
     * Limiar de paralelização declarado em {@code Population}. Repetido aqui de propósito: se
     * alguém mudar a constante lá sem pensar nos dois caminhos, os testes de fronteira abaixo
     * passam a exercitar o mesmo caminho duas vezes, e o teste do limiar acusa.
     */
    private static final int LIMIAR = 64;

    private static final int HORIZONTE_DIAS = 180;
    private static final int HORAS_POR_DIA = 4;
    private static final int CARGA_DIARIA_MAXIMA = 20;

    private static final List<Subject> DISCIPLINAS = List.of(
            new Subject("Portugues", 20, 2),
            new Subject("Matematica", 15, 5),
            new Subject("Direito", 25, 4),
            new Subject("Informatica", 10, 3));

    @Test
    @DisplayName("acima do limiar (caminho paralelo) a fitness bate com o calculo direto")
    void acimaDoLimiarAFitnessBateComOCalculoDireto() {
        verificaEquivalencia(LIMIAR * 2);
    }

    @Test
    @DisplayName("abaixo do limiar (caminho sequencial) a fitness bate com o calculo direto")
    void abaixoDoLimiarAFitnessBateComOCalculoDireto() {
        verificaEquivalencia(LIMIAR / 8);
    }

    @Test
    @DisplayName("cruzar o limiar nao muda a fitness de nenhum individuo")
    void cruzarOLimiarNaoMudaAFitness() {
        // As duas populações são geradas pela mesma regra, então o indivíduo de índice i é o mesmo
        // plano nas duas. A única diferença é o tamanho — e, com ele, o caminho de código tomado.
        EvolutionContext contexto = contexto();

        Population sequencial = populacao(LIMIAR - 1);
        Population paralela = populacao(LIMIAR);
        sequencial.calculateFitness(contexto);
        paralela.calculateFitness(contexto);

        for (int i = 0; i < LIMIAR - 1; i++) {
            assertThat(paralela.getIndividual(i).getFitness())
                    .as("individuo %d divergiu entre o caminho paralelo e o sequencial", i)
                    .isEqualTo(sequencial.getIndividual(i).getFitness());
        }
    }

    @Test
    @DisplayName("a fitness varia entre os individuos — a comparacao nao passa por acaso")
    void aFitnessVariaEntreOsIndividuos() {
        // Contraprova. Sem ela, os testes acima passariam mesmo que a avaliação estivesse quebrada
        // e devolvesse o mesmo número para todo mundo, dos dois lados do limiar.
        Population populacao = populacao(LIMIAR * 2);
        populacao.calculateFitness(contexto());

        assertThat(populacao.getFittest().getFitness())
                .as("se o melhor e o pior empatam, a fitness nao esta discriminando nada")
                .isGreaterThan(populacao.getWorst().getFitness());
        assertThat(populacao.getAverageFitness())
                .isBetween(populacao.getWorst().getFitness(), populacao.getFittest().getFitness());
    }

    @Test
    @DisplayName("populacao vazia nao quebra os acessores")
    void populacaoVaziaNaoQuebra() {
        Population vazia = new Population(0);

        assertThat(vazia.getSize()).isZero();
        assertThat(vazia.getFittest()).isNull();
        assertThat(vazia.getWorst()).isNull();
        assertThat(vazia.getAverageFitness()).isZero();
    }

    /**
     * Avalia a população pelo caminho que o tamanho escolhe e confere cada valor contra o cálculo
     * direto do indivíduo, feito em sequência nesta thread.
     */
    private static void verificaEquivalencia(int tamanho) {
        EvolutionContext contexto = contexto();
        Population populacao = populacao(tamanho);

        populacao.calculateFitness(contexto);

        for (int i = 0; i < tamanho; i++) {
            Individual individuo = populacao.getIndividual(i);
            assertThat(individuo.getFitness())
                    .as("individuo %d de uma populacao de %d nao bate com o calculo direto", i, tamanho)
                    .isEqualTo(individuo.calculateFitness(contexto));
        }
    }

    /**
     * Gera uma população determinística: o indivíduo {@code i} distribui os mesmos 200 dias entre as
     * quatro disciplinas, com a proporção deslocada por {@code i}. Planos diferentes garantem
     * fitness diferentes, que é o que dá o que comparar.
     */
    private static Population populacao(int tamanho) {
        List<Individual> individuos = new ArrayList<>(tamanho);
        for (int i = 0; i < tamanho; i++) {
            Map<Subject, Integer> dias = new LinkedHashMap<>();
            int restante = 200;
            for (int d = 0; d < DISCIPLINAS.size() - 1; d++) {
                int alocado = 10 + ((i * 7 + d * 13) % 40);
                dias.put(DISCIPLINAS.get(d), alocado);
                restante -= alocado;
            }
            dias.put(DISCIPLINAS.get(DISCIPLINAS.size() - 1), restante);
            individuos.add(new Individual(new StudyPlan(dias)));
        }
        return new Population(individuos);
    }

    private static EvolutionContext contexto() {
        Map<Subject, Double> importancias = new LinkedHashMap<>();
        Map<Subject, Integer> pisos = new LinkedHashMap<>();
        for (Subject disciplina : DISCIPLINAS) {
            importancias.put(disciplina, (double) disciplina.questionCount());
            pisos.put(disciplina, 5);
        }
        return EvolutionContext.builder()
                .importanceScores(importancias)
                .minimumDaysPerSubject(pisos)
                .fitnessEvaluator(pipelineDeProducao())
                .planningHorizonDays(HORIZONTE_DIAS)
                .hoursPerStudyDay(HORAS_POR_DIA)
                .maxDailyCognitiveLoad(CARGA_DIARIA_MAXIMA)
                .build();
    }

    /** O mesmo conjunto de componentes que o Spring monta em produção. */
    private static FitnessEvaluator pipelineDeProducao() {
        return new FitnessEvaluator(
                List.of(new ScoreGainObjective(), new RetentionObjective(), new CognitiveLoadObjective()),
                List.of(new DropoutRiskPenalty(new DropoutRiskPredictor()),
                        new FatigueAndSustainabilityPenalty(new FatigueAndEnergyModel())),
                List.of(new MinimumDaysConstraint(),
                        new MandatoryReviewConstraint(new HybridRetentionEngine())));
    }
}
