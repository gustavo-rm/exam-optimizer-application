package com.ia.project.dynamicstudyplanner.ga.fitness;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.CognitiveLoadObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty;
import com.ia.project.dynamicstudyplanner.service.EvolutionContextAssembler;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor;
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
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

    private static final LocalDate HOJE = LocalDate.now();

    private static FitnessEvaluator avaliador() {
        return new FitnessEvaluator(
                List.of(new ScoreGainObjective(), new RetentionObjective(), new CognitiveLoadObjective()),
                List.of(new DropoutRiskPenalty(new DropoutRiskPredictor()),
                        new FatigueAndSustainabilityPenalty(new FatigueAndEnergyModel())),
                List.of(new MinimumDaysConstraint(),
                        new MandatoryReviewConstraint(new HybridRetentionEngine())));
    }

    private static Exam exame(int disciplinas) {
        List<Subject> lista = new ArrayList<>();
        for (int i = 0; i < disciplinas; i++) {
            lista.add(new Subject("D" + i, 5 + (i * 3) % 25, 1 + (i % 5)));
        }
        return new Exam("Concurso", HOJE.plusDays(400), 100.0, lista, List.of());
    }

    private static StudentProfile perfil(Exam exame) {
        Map<Subject, Double> lacunas = new HashMap<>();
        for (Subject s : exame.getGeneralKnowledgeSubjects()) {
            lacunas.put(s, 3.0);
        }
        Map<DayOfWeek, Integer> disponibilidade = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            disponibilidade.put(d, 5);
        }
        return new StudentProfile("Aluno", lacunas, disponibilidade,
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE));
    }

    private static EvolutionContext contexto(Exam exame, StudentProfile perfil) {
        ImportanceCalculator importancia = new ImportanceCalculator();
        return new EvolutionContextAssembler(new BaselineCalculator(importancia), importancia,
                new CognitiveLoadCalculator(), avaliador()).assemble(exame, perfil);
    }

    /**
     * Um conjunto de entradas, não uma.
     *
     * <p>Planos deliberadamente diferentes em forma: distribuído, concentrado num único tópico,
     * mínimo, e um desigual. O mínimo e o concentrado são os que violam o piso de cobertura e levam
     * a soma abaixo de zero, fazendo o {@code clamp} morder — que é justamente o passo onde a
     * igualdade ingênua "soma == agregado" deixaria de valer.
     */
    private static List<StudyPlan> planos(Exam exame) {
        List<Subject> disciplinas = exame.getAllSubjects();
        List<StudyPlan> planos = new ArrayList<>();

        Map<Subject, Integer> distribuido = new LinkedHashMap<>();
        disciplinas.forEach(s -> distribuido.put(s, 30));
        planos.add(new StudyPlan(distribuido));

        Map<Subject, Integer> concentrado = new LinkedHashMap<>();
        for (int i = 0; i < disciplinas.size(); i++) {
            concentrado.put(disciplinas.get(i), i == 0 ? 360 : 0);
        }
        planos.add(new StudyPlan(concentrado));

        Map<Subject, Integer> minimo = new LinkedHashMap<>();
        disciplinas.forEach(s -> minimo.put(s, 1));
        planos.add(new StudyPlan(minimo));

        Map<Subject, Integer> desigual = new LinkedHashMap<>();
        for (int i = 0; i < disciplinas.size(); i++) {
            desigual.put(disciplinas.get(i), (i * 17) % 40);
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
            Exam exame = exame(12);
            EvolutionContext context = contexto(exame, perfil(exame));

            for (StudyPlan plano : planos(exame)) {
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
            Exam exame = exame(12);
            EvolutionContext context = contexto(exame, perfil(exame));

            for (StudyPlan plano : planos(exame)) {
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
            Exam exame = exame(12);
            EvolutionContext context = contexto(exame, perfil(exame));

            for (StudyPlan plano : planos(exame)) {
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
            Exam exame = exame(12);
            EvolutionContext context = contexto(exame, perfil(exame));

            for (StudyPlan plano : planos(exame)) {
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
        Exam exame = exame(6);
        EvolutionContext context = contexto(exame, perfil(exame));
        FitnessBreakdown d = avaliador().explain(new StudyPlan(Map.of()), context);

        assertThat(d.terms()).extracting(FitnessBreakdown.Term::name)
                .as("renomear qualquer um destes e quebra de contrato, nao refatoracao")
                .containsExactly("syllabusMastery", "retention", "cognitiveLoad",
                        "MinimumDaysConstraint", "MandatoryReviewConstraint");
        assertThat(d.penalties()).extracting(FitnessBreakdown.Penalty::name)
                .containsExactly("DropoutRiskPenalty", "FatigueAndSustainabilityPenalty");
    }

    @Test
    @DisplayName("os pesos publicados sao os declarados em FitnessWeights")
    void osPesosSaoOsDeclarados() {
        Exam exame = exame(6);
        EvolutionContext context = contexto(exame, perfil(exame));
        Map<String, Double> pesos = new LinkedHashMap<>();
        avaliador().explain(new StudyPlan(Map.of()), context).terms()
                .forEach(t -> pesos.put(t.name(), t.weight()));

        assertThat(pesos).containsEntry("syllabusMastery", FitnessWeights.SYLLABUS_MASTERY)
                .containsEntry("retention", FitnessWeights.RETENTION)
                .containsEntry("cognitiveLoad", FitnessWeights.COGNITIVE_LOAD)
                .containsEntry("MinimumDaysConstraint", FitnessWeights.CONSTRAINT_VIOLATION);
    }
}
