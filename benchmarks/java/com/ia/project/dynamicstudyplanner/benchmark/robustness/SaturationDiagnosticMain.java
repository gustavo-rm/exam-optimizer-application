package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkHarness;
import com.ia.project.dynamicstudyplanner.benchmark.harness.StrategyOutcome;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.HighLoadInstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.metric.MetricsCalculator;
import com.ia.project.dynamicstudyplanner.benchmark.metric.Spearman;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.PlanningStrategy;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.LearningModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Explains <b>why</b> the fitness/retention correlation is undefined on half the benchmark
 * instances, so that the exclusion can be justified rather than merely reported.
 *
 * <h2>The question</h2>
 *
 * The canonical aggregate rests on 4 of 8 instances; the other 4 are dropped because their
 * correlation is undefined. Two very different situations produce that:
 * <ul>
 *   <li><b>Saturation at the ceiling</b> — every planner keeps every subject above the forgetting
 *       threshold, so the business metric is constant at 100% and there is nothing to correlate.
 *       The fitness cannot do harm here, and the exclusion is benign.</li>
 *   <li><b>Saturation at the floor</b> — the budget is so far below demand that every planner fails
 *       every subject. The metric would be constant at 0%, the fitness would be equally unable to
 *       help, and the exclusion would be hiding a regime where the product is broken.</li>
 * </ul>
 * These look identical in an aggregate that only counts "excluded: undefined". They are opposite
 * problems, and limitation L4 (docs/revisao-ag/05-fitness-function.md §8) was originally attributed
 * to the second. This class measures which one actually occurs.
 *
 * <h2>What it reports</h2>
 *
 * Per instance: the demand ratio and importance dispersion (the two candidate drivers), the tail of
 * the allocation (minimum days any subject receives, over all planners — the quantity that
 * docs/revisao-ag/06-limite-troca-pesos.md §5 identified as what the threshold-count metric actually
 * reacts to), the observed range of the business metric, and a classification.
 * <p>
 * Correlation comes from {@link Spearman}, the same helper the reports use. This class computes no
 * aggregate: that belongs to {@code CorrelationAggregate} alone.
 */
public final class SaturationDiagnosticMain {

    private static final long SEED = BenchmarkHarness.BASE_SEED;

    /**
     * Repetitions per instance, matching {@code BenchmarkMain}.
     * <p>
     * Not optional. A single seed puts {@code I4} at {@code +0.339} while the canonical 7-repetition
     * measurement puts it at {@code -0.093} - opposite signs. A diagnostic printing a correlation
     * column next to the canonical numbers has to use the same sampling, or it invites exactly the
     * kind of quiet inconsistency this review keeps finding.
     */
    private static final int REPETITIONS = 7;

    /** Why an instance's correlation is or is not defined. */
    private enum Diagnosis {
        /** Business metric constant at its maximum: no planner loses any subject. */
        SATURADA_NO_TETO("SATURADA NO TETO"),
        /** Business metric constant at its minimum: every planner loses everything. */
        SATURADA_NO_PISO("SATURADA NO PISO"),
        /** Business metric constant somewhere in between - would be surprising. */
        CONSTANTE_INTERMEDIARIA("CONSTANTE (intermediaria)"),
        /** Business metric varies across planners: the instance carries information. */
        DISCRIMINA("DISCRIMINA");

        private final String label;

        Diagnosis(String label) {
            this.label = label;
        }
    }

    private SaturationDiagnosticMain() {
    }

    public static void main(String[] args) {
        BenchmarkHarness harness = new BenchmarkHarness();
        MetricsCalculator metrics = new MetricsCalculator(BenchmarkHarness.productionFitnessEvaluator());

        System.out.println("## Por que 4 de 8 instancias nao tem correlacao definida\n");
        System.out.println("`Dias min` = menor numero de dias que qualquer disciplina recebe, no pior "
                + "planejador da instancia.");
        System.out.println("`Cobertura min` = esse mesmo minimo dividido pelas sessoes que a "
                + "disciplina precisaria (`H / tau`). Abaixo de 1 a disciplina fica sub-coberta.\n");

        report(harness, metrics, InstanceLibrary.all(), "Instancias originais (base do agregado canonico)");
        report(harness, metrics, HighLoadInstanceLibrary.all(),
                "Familia R - varredura de razao demanda/orcamento (etapa 06)");
    }

    private static void report(BenchmarkHarness harness, MetricsCalculator metrics,
                               List<BenchmarkInstance> instances, String title) {
        // One harness pass over the whole family, exactly as BenchmarkMain does it, so the
        // correlation column is byte-identical to the canonical report. Measuring instance by
        // instance and interleaving other planner calls between them does not reproduce it.
        List<StrategyOutcome> allOutcomes = harness.run(instances, REPETITIONS);

        System.out.printf(Locale.ROOT, "### %s%n%n", title);
        System.out.println("| Instancia | Disc. | Orcam. | Horiz. | Razao | Dispersao "
                + "| Dias min | Cobertura min | Janela min..max | Spearman | Diagnostico |");
        System.out.println("|---|---|---|---|---|---|---|---|---|---|---|");

        for (BenchmarkInstance instance : instances) {
            EvolutionContext context = harness.contextFor(instance);

            List<StrategyOutcome> outcomes = allOutcomes.stream()
                    .filter(o -> o.instanceId().equals(instance.id()))
                    .toList();
            List<Double> fitness = outcomes.stream().map(StrategyOutcome::meanFitness).toList();
            List<Double> window = outcomes.stream()
                    .map(StrategyOutcome::meanPctInRetentionWindow).toList();

            double min = window.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = window.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double rho = Spearman.correlation(fitness, window);

            Tail tail = measureTail(harness, instance, context);

            System.out.printf(Locale.ROOT,
                    "| `%s` | %d | %d | %d | %.2f | %.0f:1 | %d | %.2f | %.1f%% .. %.1f%% | %s | **%s** |%n",
                    instance.id(), instance.subjectCount(), instance.totalStudyDays(),
                    context.planningHorizonDays(), HighLoadInstanceLibrary.demandRatio(instance),
                    HighLoadInstanceLibrary.importanceDispersion(instance),
                    tail.minDays(), tail.minCoverage(), 100 * min, 100 * max,
                    Double.isNaN(rho) ? "n/d" : String.format(Locale.ROOT, "%+.3f", rho),
                    diagnose(min, max).label);
        }
        System.out.println();
    }

    /** The thinnest allocation any planner produces, and how it compares to what O3 asks for. */
    private record Tail(int minDays, double minCoverage) {
    }

    /**
     * Measures the tail of the allocation across planners.
     * <p>
     * Deliberately runs after every correlation in the family has been measured: these extra planner
     * invocations are structural probes, and letting them interleave with the harness pass would mean
     * the two columns describe different runs.
     */
    private static Tail measureTail(BenchmarkHarness harness, BenchmarkInstance instance,
                                    EvolutionContext context) {
        int minDays = Integer.MAX_VALUE;
        double minCoverage = Double.MAX_VALUE;

        for (PlanningStrategy strategy : harness.strategies()) {
            int reps = strategy.deterministic() ? 1 : REPETITIONS;
            for (int r = 0; r < reps; r++) {
                StudyPlan plan = strategy.plan(instance, context, SEED + r);
                for (Map.Entry<Subject, Integer> entry : plan.getDaysPerSubject().entrySet()) {
                    minDays = Math.min(minDays, entry.getValue());
                    double required = LearningModel.requiredSessions(
                            entry.getKey(), context.planningHorizonDays());
                    minCoverage = Math.min(minCoverage, entry.getValue() / required);
                }
            }
        }
        return new Tail(minDays, minCoverage);
    }

    /**
     * Classifies an instance from the observed range of the business metric alone.
     * <p>
     * The ceiling is 100% and the floor is 0%; a constant value anywhere else would mean the metric
     * is blind for some other reason and would deserve investigation on its own.
     */
    private static Diagnosis diagnose(double min, double max) {
        if (max > min) {
            return Diagnosis.DISCRIMINA;
        }
        if (max >= 1.0) {
            return Diagnosis.SATURADA_NO_TETO;
        }
        return max <= 0.0 ? Diagnosis.SATURADA_NO_PISO : Diagnosis.CONSTANTE_INTERMEDIARIA;
    }
}
