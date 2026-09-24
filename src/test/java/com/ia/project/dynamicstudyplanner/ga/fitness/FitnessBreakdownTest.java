package com.ia.project.dynamicstudyplanner.ga.fitness;

import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FitnessPenalty;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import com.ia.project.dynamicstudyplanner.sinapse.DailyLoadBudgetObjective;
import com.ia.project.dynamicstudyplanner.support.TopicPlans;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A decomposição da fitness explica exatamente o número que o avaliador devolve (GAP-07).
 *
 * <h2>Por que este teste é obrigatório, e não uma cortesia</h2>
 *
 * {@code FitnessEvaluator.explain} <b>repete</b> a aritmética de {@code evaluate} em vez de delegar
 * a ela, por uma razão de custo registrada no Javadoc daquele método: delegar alocaria uma lista por
 * avaliação, dentro de um laço que roda até meio milhão de vezes. O preço dessa escolha é que a
 * igualdade entre as duas deixa de ser estrutural — duas cópias da mesma conta podem divergir na
 * primeira vez que alguém mexer numa delas. <b>Este teste é o que sustenta a escolha.</b>
 *
 * <p>E há uma segunda coisa sob teste, mais importante que a primeira: se a soma das contribuições
 * não reconstruísse o agregado, o achado não seria "a decomposição está errada" — seria <b>a
 * composição da fitness não é a que o código documenta</b>. Por isso a cadeia inteira é conferida,
 * e não só o total.
 *
 * <h2>A composição usada aqui</h2>
 *
 * Os três objetivos e as duas restrições são os do caminho SINAPSE, o único que ficou. Nenhuma
 * composição de produção declara penalidade — as duas que existiam saíram em EOA-4b com o estado
 * psicológico que liam —, e mesmo assim este teste compõe <b>duas penalidades de teste</b>, com
 * fatores fixos e distintos de 1.
 *
 * <p>É deliberado, e é o que a versão anterior não fazia: com as penalidades de produção, sobre um
 * plano macro as duas devolviam {@code 1.0} pelo {@code instanceof} que as guardava, e a asserção
 * "agregado = clamp(soma) × produto das penalidades" multiplicava por 1 — passava sem exercitar o
 * elo. Fatores fixos diferentes de 1 fazem o elo contar. O que está sob teste aqui é o
 * <b>maquinário</b> de {@link FitnessEvaluator}, que segue sendo ponto de extensão, e não um termo
 * de produção que não existe.
 *
 * <h2>Tolerância declarada</h2>
 *
 * {@code 1e-12} para as identidades reconstruídas passo a passo, porque somar os mesmos termos em
 * ordens diferentes não é associativo em ponto flutuante. Entre {@code explain(...).aggregate()} e
 * {@code evaluate(...)} a exigência é mais dura: <b>igualdade exata</b>, bit a bit, porque ali as
 * duas contas fazem as mesmas operações na mesma ordem e qualquer diferença significa que uma delas
 * mudou.
 */
@DisplayName("Decomposicao da fitness: os termos reconstroem o agregado")
class FitnessBreakdownTest {

    /** Folga para reconstruções passo a passo. Ver o Javadoc da classe. */
    private static final Offset<Double> TOLERANCIA = Offset.offset(1e-12);

    private static FitnessEvaluator avaliador() {
        return new FitnessEvaluator(
                List.of(new ScoreGainObjective(), new RetentionObjective(),
                        new DailyLoadBudgetObjective()),
                List.of(new PenalidadeFixa("PenalidadeA", 0.90),
                        new PenalidadeFixa("PenalidadeB", 0.75)),
                List.of(new MinimumDaysConstraint(),
                        new MandatoryReviewConstraint(new HybridRetentionEngine())));
    }

    /** Penalidade de fator constante: exercita o elo multiplicativo sem depender de dado ausente. */
    private record PenalidadeFixa(String nome, double fator) implements FitnessPenalty {

        @Override
        public double calculatePenaltyFactor(StudyPlan plan, EvolutionContext context) {
            return fator;
        }

        @Override
        public String name() {
            return nome;
        }
    }

    private static EvolutionContext contexto(int topicos) {
        return TopicPlans.context(topicos);
    }

    /**
     * Um conjunto de entradas, não uma.
     *
     * <p>Planos deliberadamente diferentes em forma: distribuído, concentrado num único tópico,
     * mínimo, e um desigual. O mínimo e o concentrado são os que violam o piso de cobertura e levam
     * a soma abaixo de zero, fazendo o {@code clamp} morder — que é justamente o passo onde a
     * igualdade ingênua "soma == agregado" deixaria de valer.
     */
    private static List<StudyPlan> planos(EvolutionContext contexto) {
        List<PlanningItem> itens = List.copyOf(contexto.importanceScores().keySet());
        List<StudyPlan> planos = new ArrayList<>();

        Map<PlanningItem, Integer> distribuido = new LinkedHashMap<>();
        itens.forEach(item -> distribuido.put(item, 30));
        planos.add(new StudyPlan(distribuido));

        Map<PlanningItem, Integer> concentrado = new LinkedHashMap<>();
        for (int i = 0; i < itens.size(); i++) {
            concentrado.put(itens.get(i), i == 0 ? 360 : 0);
        }
        planos.add(new StudyPlan(concentrado));

        Map<PlanningItem, Integer> minimo = new LinkedHashMap<>();
        itens.forEach(item -> minimo.put(item, 1));
        planos.add(new StudyPlan(minimo));

        Map<PlanningItem, Integer> desigual = new LinkedHashMap<>();
        for (int i = 0; i < itens.size(); i++) {
            desigual.put(itens.get(i), (i * 17) % 40);
        }
        planos.add(new StudyPlan(desigual));

        return planos;
    }

    @Nested
    @DisplayName("Sobre um conjunto de entradas")
    class SobreVariasEntradas {

        @Test
        @DisplayName("o agregado da decomposicao e, bit a bit, o double que evaluate devolve")
        void oAgregadoEhExatamenteODeEvaluate() {
            FitnessEvaluator avaliador = avaliador();
            EvolutionContext context = contexto(12);

            for (StudyPlan plano : planos(context)) {
                double historico = avaliador.evaluate(plano, context);
                FitnessBreakdown decomposto = avaliador.explain(plano, context);

                assertThat(decomposto.aggregate())
                        .as("explain repete a aritmetica de evaluate; se divergirem, uma das duas "
                                + "copias mudou sem a outra")
                        .isEqualTo(historico);
            }
        }

        @Test
        @DisplayName("a soma das contribuicoes ponderadas reconstroi o rawScore")
        void aSomaDasContribuicoesReconstroiORawScore() {
            FitnessEvaluator avaliador = avaliador();
            EvolutionContext context = contexto(12);

            for (StudyPlan plano : planos(context)) {
                FitnessBreakdown decomposto = avaliador.explain(plano, context);
                double somado = decomposto.terms().stream()
                        .mapToDouble(FitnessBreakdown.Term::weightedContribution)
                        .sum();

                assertThat(somado)
                        .as("se a soma dos termos nao da o rawScore, a composicao da fitness nao e "
                                + "a que o codigo documenta — e isso e achado, nao ajuste de teste")
                        .isCloseTo(decomposto.rawScore(), TOLERANCIA);
            }
        }

        @Test
        @DisplayName("a cadeia completa fecha: clamp do rawScore, vezes o produto das penalidades")
        void aCadeiaCompletaFecha() {
            FitnessEvaluator avaliador = avaliador();
            EvolutionContext context = contexto(12);

            for (StudyPlan plano : planos(context)) {
                FitnessBreakdown d = avaliador.explain(plano, context);

                assertThat(d.boundedScore())
                        .as("boundedScore e o rawScore limitado a [0,1]")
                        .isEqualTo(Math.clamp(d.rawScore(), 0.0, 1.0));

                double produto = d.penalties().stream()
                        .mapToDouble(FitnessBreakdown.Penalty::factor)
                        .reduce(1.0, (a, b) -> a * b);
                assertThat(d.penaltyFactor()).isCloseTo(produto, TOLERANCIA);

                assertThat(d.aggregate())
                        .as("agregado = clamp(soma) * produto das penalidades")
                        .isCloseTo(d.boundedScore() * d.penaltyFactor(), TOLERANCIA);
            }
        }

        @Test
        @DisplayName("cada contribuicao e o valor vezes o peso, com o sinal do tipo do termo")
        void cadaContribuicaoEhValorVezesPeso() {
            FitnessEvaluator avaliador = avaliador();
            EvolutionContext context = contexto(12);

            for (StudyPlan plano : planos(context)) {
                for (FitnessBreakdown.Term termo : avaliador.explain(plano, context).terms()) {
                    double esperado = termo.kind() == FitnessBreakdown.TermKind.OBJECTIVE
                            ? termo.value() * termo.weight()
                            : -(termo.value() * termo.weight());
                    assertThat(termo.weightedContribution())
                            .as("termo %s", termo.name())
                            .isCloseTo(esperado, TOLERANCIA);
                }
            }
        }
    }

    @Test
    @DisplayName("os nomes dos termos sao os estaveis, que viram chave de API")
    void osNomesSaoOsEstaveis() {
        EvolutionContext context = contexto(6);
        FitnessBreakdown d = avaliador().explain(new StudyPlan(Map.of()), context);

        assertThat(d.terms()).extracting(FitnessBreakdown.Term::name)
                .as("renomear qualquer um destes e quebra de contrato, nao refatoracao")
                .containsExactly("syllabusMastery", "retention", DailyLoadBudgetObjective.NAME,
                        "MinimumDaysConstraint", "MandatoryReviewConstraint");
        assertThat(d.penalties()).extracting(FitnessBreakdown.Penalty::name)
                .as("o nome vem do proprio termo, e nao do tipo dele")
                .containsExactly("PenalidadeA", "PenalidadeB");
        assertThat(d.penaltyFactor())
                .as("o produto das penalidades e diferente de 1: o elo multiplicativo conta")
                .isEqualTo(0.90 * 0.75, TOLERANCIA);
    }

    @Test
    @DisplayName("os pesos publicados sao os declarados em FitnessWeights")
    void osPesosSaoOsDeclarados() {
        EvolutionContext context = contexto(6);
        Map<String, Double> pesos = new LinkedHashMap<>();
        avaliador().explain(new StudyPlan(Map.of()), context).terms()
                .forEach(t -> pesos.put(t.name(), t.weight()));

        assertThat(pesos).containsEntry("syllabusMastery", FitnessWeights.SYLLABUS_MASTERY)
                .containsEntry("retention", FitnessWeights.RETENTION)
                .containsEntry(DailyLoadBudgetObjective.NAME, FitnessWeights.COGNITIVE_LOAD)
                .containsEntry("MinimumDaysConstraint", FitnessWeights.CONSTRAINT_VIOLATION);
    }
}
