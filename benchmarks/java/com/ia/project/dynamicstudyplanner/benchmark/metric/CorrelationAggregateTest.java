package com.ia.project.dynamicstudyplanner.benchmark.metric;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link CorrelationAggregate} as the only sanctioned way to summarise fitness/business-metric
 * correlation across instances, and proves that the method it replaces reaches the opposite
 * conclusion on data built to expose the difference.
 * <p>
 * Background in docs/revisao-ag/07-correcao-metrica-e-ausubel.md.
 */
@DisplayName("Agregacao de correlacoes entre instancias")
class CorrelationAggregateTest {

    private static final int PLANNERS_PER_INSTANCE = 6;

    @Nested
    @DisplayName("Empilhar observacoes de instancias diferentes inverte a conclusao")
    class StackingReversesTheConclusion {

        /**
         * A synthetic two-instance case in the shape of Simpson's paradox, built to be unambiguous.
         * <p>
         * Within each instance the fitness is <b>perfectly anti-correlated</b> with the business
         * metric: the plan the fitness ranks first delivers the worst retention. That is the finding
         * any honest summary has to report. But instance B lives on a higher fitness scale <em>and</em>
         * a higher retention scale, so pooling the two makes the between-instance offset dominate the
         * within-instance ordering and the pooled coefficient comes out strongly positive.
         */
        private static final double[] FITNESS_A = {0.10, 0.20, 0.30, 0.40, 0.50, 0.60};
        private static final double[] RETENTION_A = {0.60, 0.50, 0.40, 0.30, 0.20, 0.10};

        private static final double[] FITNESS_B = {0.70, 0.80, 0.90, 1.00, 1.10, 1.20};
        private static final double[] RETENTION_B = {1.20, 1.10, 1.00, 0.90, 0.80, 0.70};

        @Test
        @DisplayName("dentro de cada instancia a correlacao e -1,000")
        void withinEachInstanceTheCorrelationIsPerfectlyNegative() {
            assertThat(Spearman.correlation(box(FITNESS_A), box(RETENTION_A))).isEqualTo(-1.0);
            assertThat(Spearman.correlation(box(FITNESS_B), box(RETENTION_B))).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("o metodo invalido (empilhar) devolve correlacao POSITIVA forte")
        void poolingAcrossInstancesReportsAStrongPositiveCorrelation() {
            List<Double> pooledFitness = concat(FITNESS_A, FITNESS_B);
            List<Double> pooledRetention = concat(RETENTION_A, RETENTION_B);

            double pooled = Spearman.correlation(pooledFitness, pooledRetention);

            assertThat(pooled)
                    .as("Empilhar 12 observacoes de duas instancias com escalas diferentes mede qual "
                            + "instancia e facil, nao se a fitness serve a metrica de negocio. "
                            + "E o defeito de 03 §5 e 05 §6.4.")
                    .isGreaterThan(0.5);
        }

        @Test
        @DisplayName("o metodo correto (Fisher z por instancia) preserva a conclusao real")
        void fisherZAggregationPreservesTheWithinInstanceConclusion() {
            CorrelationAggregate.Result result = CorrelationAggregate.aggregate(
                    List.of(-1.0, -1.0), PLANNERS_PER_INSTANCE);

            assertThat(result.correlation())
                    .as("As duas instancias sao perfeitamente anti-correlacionadas, entao o agregado "
                            + "tem de ser -1,000. Conclusao oposta a do empilhamento sobre os mesmos dados.")
                    .isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(result.instancesUsed()).isEqualTo(2);
        }

        @Test
        @DisplayName("os dois metodos discordam ate no sinal sobre os mesmos dados")
        void theTwoMethodsDisagreeOnTheSign() {
            double pooled = Spearman.correlation(concat(FITNESS_A, FITNESS_B),
                    concat(RETENTION_A, RETENTION_B));
            double aggregated = CorrelationAggregate.aggregate(
                    List.of(-1.0, -1.0), PLANNERS_PER_INSTANCE).correlation();

            assertThat(Math.signum(pooled))
                    .as("pooled = %.3f, agregado = %.3f", pooled, aggregated)
                    .isNotEqualTo(Math.signum(aggregated));
        }
    }

    @Nested
    @DisplayName("Propriedades do agregado")
    class AggregateProperties {

        @Test
        @DisplayName("instancias com correlacao indefinida sao excluidas, nao contadas como zero")
        void undefinedInstancesAreExcludedRatherThanCountedAsZero() {
            // Four saturated instances (undefined) alongside two strongly negative ones. Treating
            // the undefined ones as 0.0 would drag the aggregate to about -0.30; excluding them
            // reports what was actually measured.
            CorrelationAggregate.Result result = CorrelationAggregate.aggregate(
                    List.of(-0.9, -0.9, Double.NaN, Double.NaN, Double.NaN, Double.NaN),
                    PLANNERS_PER_INSTANCE);

            assertThat(result.instancesUsed()).isEqualTo(2);
            assertThat(result.instancesSkipped()).isEqualTo(4);
            assertThat(result.correlation()).isCloseTo(-0.9, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("uma correlacao perfeita nao vira infinito")
        void aPerfectCorrelationDoesNotBecomeInfinite() {
            CorrelationAggregate.Result result =
                    CorrelationAggregate.aggregate(List.of(1.0, 1.0), PLANNERS_PER_INSTANCE);

            assertThat(result.correlation()).isFinite().isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("Fisher z pondera correlacoes fortes mais que a media aritmetica simples")
        void fisherZWeighsStrongCorrelationsMoreThanAPlainMean() {
            List<Double> mixed = List.of(-0.95, 0.05);

            double plainMean = mixed.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double fisher = CorrelationAggregate.aggregate(mixed, PLANNERS_PER_INSTANCE).correlation();

            assertThat(fisher)
                    .as("media simples = %.3f, Fisher = %.3f. A escala de r nao e aditiva: -0,95 "
                            + "carrega muito mais informacao que +0,05, e a transformada reflete isso.",
                            plainMean, fisher)
                    .isLessThan(plainMean);
        }

        @Test
        @DisplayName("instancias sem graus de liberdade (n <= 3) sao excluidas")
        void instancesWithoutDegreesOfFreedomAreExcluded() {
            CorrelationAggregate.Result result = CorrelationAggregate.aggregate(List.of(
                    new CorrelationAggregate.InstanceCorrelation("n3", -0.9, 3),
                    new CorrelationAggregate.InstanceCorrelation("n6", -0.5, 6)));

            assertThat(result.instancesUsed()).isEqualTo(1);
            assertThat(result.instancesSkipped()).isEqualTo(1);
            assertThat(result.correlation()).isCloseTo(-0.5, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("sem nenhuma instancia definida o resultado e n/d, nao zero")
        void withNoDefinedInstanceTheResultIsUndefined() {
            CorrelationAggregate.Result result = CorrelationAggregate.aggregate(
                    List.of(Double.NaN, Double.NaN), PLANNERS_PER_INSTANCE);

            assertThat(result.isUndefined()).isTrue();
            assertThat(result.correlation()).isNaN();
            assertThat(result.format()).contains("n/d");
        }
    }

    @Nested
    @DisplayName("Regressao sobre os dados reais das etapas 03, 05 e 06")
    class RealDataRegression {

        @Test
        @DisplayName("a trajetoria publicada e reproduzida pelo metodo canonico")
        void publishedTrajectoryIsReproducedByTheCanonicalMethod() {
            // Correlacoes por instancia medidas em cada estado; as quatro instancias saturadas
            // (I1, I2, I5, I6) sao indefinidas nos tres e entram como NaN. A etapa 03 usa a media
            // de 10 execucoes (BaselineReplayMain), porque naquele commit o AG ainda nao era
            // reproduzivel; 05 e 06 sao deterministicos e usam o valor unico.
            CorrelationAggregate.Result etapa03 = CorrelationAggregate.aggregate(
                    withSaturated(-0.937, -0.941, -0.847, -0.880), PLANNERS_PER_INSTANCE);
            CorrelationAggregate.Result etapa05 = CorrelationAggregate.aggregate(
                    withSaturated(-0.029, -0.525, 0.000, -0.880), PLANNERS_PER_INSTANCE);
            CorrelationAggregate.Result etapa06 = CorrelationAggregate.aggregate(
                    withSaturated(0.257, -0.093, 0.655, -0.880), PLANNERS_PER_INSTANCE);

            assertThat(etapa03.correlation()).isLessThan(etapa05.correlation());
            assertThat(etapa05.correlation()).isLessThan(etapa06.correlation());

            assertThat(etapa03.instancesUsed()).isEqualTo(4);
            assertThat(etapa03.instancesSkipped()).isEqualTo(4);

            // Os valores publicados em 07-correcao-metrica-e-ausubel.md. Se mudarem, o documento
            // tambem tem de mudar.
            org.assertj.core.data.Offset<Double> tol = org.assertj.core.data.Offset.offset(0.0005);
            assertThat(etapa03.correlation()).isCloseTo(-0.909, tol);
            assertThat(etapa05.correlation()).isCloseTo(-0.460, tol);
            assertThat(etapa06.correlation()).isCloseTo(-0.105, tol);
        }
    }

    // ------------------------------------------------------------------

    /** The four measurable instances plus the four that saturate the retention metric. */
    private static List<Double> withSaturated(double i3, double i4, double i7, double i8) {
        return List.of(Double.NaN, Double.NaN, i3, i4, Double.NaN, Double.NaN, i7, i8);
    }

    private static List<Double> box(double[] values) {
        List<Double> boxed = new ArrayList<>(values.length);
        for (double v : values) {
            boxed.add(v);
        }
        return boxed;
    }

    private static List<Double> concat(double[] first, double[] second) {
        List<Double> all = new ArrayList<>(box(first));
        all.addAll(box(second));
        return all;
    }
}
