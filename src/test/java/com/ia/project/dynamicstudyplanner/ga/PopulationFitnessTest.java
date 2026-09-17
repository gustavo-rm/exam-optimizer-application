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
 * Trava o resultado da avaliação de fitness da {@link Population}: cada indivíduo recebe exatamente
 * a nota que o cálculo direto dele devolve, qualquer que seja o tamanho da população.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * Ele nasceu na etapa 05b como garantidor de uma paralelização condicional: a avaliação usava
 * {@code parallelStream()} acima de 64 indivíduos e um laço sequencial abaixo, e o teste provava
 * que os dois caminhos davam o mesmo número. A pendência P17 fechou essa história removendo a
 * paralelização — a medição mostrou que, depois que o achado F8 tornou a fitness ~4,5× mais barata
 * por indivíduo, o ganho da versão paralela caiu para +1,4 % no <b>maior</b> tamanho que o contrato
 * aceita, e virou prejuízo nos menores. Hoje há um caminho só.
 *
 * <p>O teste continua valendo, com um alvo diferente: em vez de comparar dois caminhos entre si,
 * ele compara <b>o único caminho</b> com a definição de fitness. A propriedade que ele fixa é a
 * mesma que autorizava paralelizar e que autoriza voltar atrás — avaliar um indivíduo é uma
 * operação <b>pura</b>: depende só do plano dele e do contexto, nunca dos vizinhos, e não sorteia
 * nada. É isso que faz {@code calculateFitness} sobre a população ser indistinguível de chamar o
 * cálculo indivíduo a indivíduo, e é isso que qualquer tentativa futura de paralelizar de novo
 * precisará preservar.
 *
 * <p>A comparação é {@code isEqualTo} sobre {@code double}, sem margem de tolerância. "Idêntico"
 * aqui é literal: uma diferença de arredondamento — do tipo que somar em outra ordem introduziria —
 * reprovaria o teste.
 *
 * <p>O complemento em nível de sistema é {@code GaResultadoInalteradoTest}, que trava o plano
 * inteiro produzido pelo algoritmo para uma semente fixa.
 */
@DisplayName("Population: a fitness atribuida e a fitness calculada")
class PopulationFitnessTest {

    /**
     * Tamanhos usados nas verificações. 128 e 8 eram os dois lados do antigo limiar de 64; foram
     * mantidos de propósito, porque continuam sendo uma população grande e uma pequena — e porque,
     * se alguém reintroduzir paralelização condicional, é entre eles que o limiar tende a cair.
     */
    private static final int POPULACAO_GRANDE = 128;
    private static final int POPULACAO_PEQUENA = 8;

    private static final int HORIZONTE_DIAS = 180;
    private static final int HORAS_POR_DIA = 4;
    private static final int CARGA_DIARIA_MAXIMA = 20;

    private static final List<Subject> DISCIPLINAS = List.of(
            new Subject("Portugues", 20, 2),
            new Subject("Matematica", 15, 5),
            new Subject("Direito", 25, 4),
            new Subject("Informatica", 10, 3));

    @Test
    @DisplayName("populacao grande: a fitness bate com o calculo direto de cada individuo")
    void populacaoGrandeBateComOCalculoDireto() {
        verificaEquivalencia(POPULACAO_GRANDE);
    }

    @Test
    @DisplayName("populacao pequena: a fitness bate com o calculo direto de cada individuo")
    void populacaoPequenaBateComOCalculoDireto() {
        verificaEquivalencia(POPULACAO_PEQUENA);
    }

    @Test
    @DisplayName("o tamanho da populacao nao muda a fitness de nenhum individuo")
    void oTamanhoDaPopulacaoNaoMudaAFitness() {
        // As duas populações são geradas pela mesma regra, então o indivíduo de índice i é o mesmo
        // plano nas duas. A única diferença é quantos vizinhos ele tem. Se avaliar 128 indivíduos
        // desse um número diferente de avaliar 8, a avaliação estaria olhando para o conjunto — e
        // era exatamente essa independência que sustentava (e hoje dispensa) a paralelização.
        EvolutionContext contexto = contexto();

        Population pequena = populacao(POPULACAO_PEQUENA);
        Population grande = populacao(POPULACAO_GRANDE);
        pequena.calculateFitness(contexto);
        grande.calculateFitness(contexto);

        for (int i = 0; i < POPULACAO_PEQUENA; i++) {
            assertThat(grande.getIndividual(i).getFitness())
                    .as("individuo %d divergiu entre a populacao grande e a pequena", i)
                    .isEqualTo(pequena.getIndividual(i).getFitness());
        }
    }

    @Test
    @DisplayName("reavaliar a mesma populacao devolve os mesmos numeros")
    void reavaliarDevolveOsMesmosNumeros() {
        // A avaliação sobrescreve a fitness de cada indivíduo. Rodá-la duas vezes tem de ser
        // indistinguível de rodá-la uma: se houvesse estado acumulado entre chamadas — um
        // contador, um cache sujo, um somatório — a segunda passada divergiria da primeira.
        EvolutionContext contexto = contexto();
        Population populacao = populacao(POPULACAO_GRANDE);

        populacao.calculateFitness(contexto);
        double[] primeiraPassada = new double[POPULACAO_GRANDE];
        for (int i = 0; i < POPULACAO_GRANDE; i++) {
            primeiraPassada[i] = populacao.getIndividual(i).getFitness();
        }

        populacao.calculateFitness(contexto);

        for (int i = 0; i < POPULACAO_GRANDE; i++) {
            assertThat(populacao.getIndividual(i).getFitness())
                    .as("individuo %d mudou de nota na segunda avaliacao", i)
                    .isEqualTo(primeiraPassada[i]);
        }
    }

    @Test
    @DisplayName("a fitness varia entre os individuos — a comparacao nao passa por acaso")
    void aFitnessVariaEntreOsIndividuos() {
        // Contraprova. Sem ela, os testes acima passariam mesmo que a avaliação estivesse quebrada
        // e devolvesse o mesmo número para todo mundo.
        Population populacao = populacao(POPULACAO_GRANDE);
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
     * Avalia a população e confere cada valor atribuído contra o cálculo direto do indivíduo, feito
     * em seguida nesta mesma thread.
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
