package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkHarness;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.PlanningStrategy;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.ProductionGeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Robustness study: seed variance, determinism and hyperparameter sensitivity.
 * <p>
 * Produces the tables in {@code docs/revisao-ag/04-robustez.md}. Run with:
 * <pre>
 *   java -cp "target/classes:target/test-classes:$(cat cp.txt)" \
 *        com.ia.project.dynamicstudyplanner.benchmark.robustness.RobustnessMain
 * </pre>
 */
public final class RobustnessMain {

    /** Seeds for the variance study. Twelve, above the ten the review asked for. */
    private static final long[] SEEDS = {
            101L, 202L, 303L, 404L, 505L, 606L, 707L, 808L, 909L, 1010L, 1111L, 1212L
    };

    public static void main(String[] args) throws IOException {
        List<BenchmarkInstance> instances = InstanceLibrary.all();
        BenchmarkHarness harness = new BenchmarkHarness();

        StringBuilder csv = new StringBuilder();

        determinismProbe(instances, harness);
        seedVariance(instances, harness, csv);
        hyperparameterSweep(harness, csv);

        Path out = Path.of("benchmarks", "results", "robustez.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, csv.toString());
        System.out.println("\nCSV escrito em " + out.toAbsolutePath());
    }

    // ------------------------------------------------------------------
    // 1. Determinism
    // ------------------------------------------------------------------

    /**
     * Runs the production GA twice with the identical seed and compares the resulting plans exactly.
     * <p>
     * A reproducible optimizer must return the same allocation for the same seed and input. Anything
     * else means some source of randomness or ordering escapes the seed.
     */
    private static void determinismProbe(List<BenchmarkInstance> instances, BenchmarkHarness harness) {
        System.out.println("### 1. Determinismo: mesma seed, duas execucoes\n");
        System.out.println(
                "| Instancia | plano identico? | dias movidos entre as duas execucoes | fitness A | fitness B |");
        System.out.println("|---|---|---|---|---|");

        PlanningStrategy ga = new ProductionGeneticAlgorithm(BenchmarkHarness.productionFitnessEvaluator());
        int identical = 0;

        for (BenchmarkInstance instance : instances) {
            EvolutionContext context = harness.contextFor(instance);
            StudyPlan first = ga.plan(instance, context, 4242L);
            StudyPlan second = ga.plan(instance, context, 4242L);

            boolean same = PlanSignature.of(first).equals(PlanSignature.of(second));
            if (same) {
                identical++;
            }
            System.out.printf(Locale.ROOT, "| `%s` | %s | %.1f | %.4f | %.4f |%n",
                    instance.id(), same ? "**sim**" : "**NAO**",
                    PlanSignature.daysMoved(first, second),
                    context.fitnessEvaluator().evaluate(first, context),
                    context.fitnessEvaluator().evaluate(second, context));
        }
        System.out.printf(Locale.ROOT, "%nReprodutivel em %d de %d instancias.%n%n", identical, instances.size());
    }

    // ------------------------------------------------------------------
    // 2. Seed variance
    // ------------------------------------------------------------------

    /**
     * Runs the production GA over {@link #SEEDS} and reports how much the outcome moves — both in
     * fitness and in the plan itself.
     * <p>
     * Fitness spread alone understates the problem: two plans can score almost identically while
     * allocating very different days to different subjects, and it is the plan, not the score, that
     * the student receives. Hence the plan-level columns.
     */
    private static void seedVariance(List<BenchmarkInstance> instances, BenchmarkHarness harness,
                                     StringBuilder csv) {
        System.out.println("### 2. Variancia entre " + SEEDS.length + " seeds\n");
        System.out.println("| Instancia | fitness media | desvio | CV% | amplitude% "
                + "| planos distintos | dias movidos (media) | dias movidos (max) "
                + "| % do orcamento (max) |");
        System.out.println("|---|---|---|---|---|---|---|---|---|");

        csv.append("secao,instancia,fitness_media,fitness_desvio,cv_pct,amplitude_pct,")
                .append("planos_distintos,seeds,dias_movidos_medio,dias_movidos_max,pct_orcamento_max\n");

        PlanningStrategy ga = new ProductionGeneticAlgorithm(BenchmarkHarness.productionFitnessEvaluator());

        for (BenchmarkInstance instance : instances) {
            EvolutionContext context = harness.contextFor(instance);

            List<StudyPlan> plans = new ArrayList<>();
            List<Double> fitnesses = new ArrayList<>();
            Set<String> distinct = new LinkedHashSet<>();

            for (long seed : SEEDS) {
                StudyPlan plan = ga.plan(instance, context, seed);
                plans.add(plan);
                fitnesses.add(context.fitnessEvaluator().evaluate(plan, context));
                distinct.add(PlanSignature.of(plan));
            }

            double mean = fitnesses.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double sd = stdDev(fitnesses, mean);
            double min = fitnesses.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = fitnesses.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double cv = mean == 0 ? 0 : 100 * sd / Math.abs(mean);
            double amplitude = mean == 0 ? 0 : 100 * (max - min) / Math.abs(mean);

            // Pairwise plan distances across all seed pairs.
            double sumMoved = 0;
            double maxMoved = 0;
            int pairs = 0;
            for (int i = 0; i < plans.size(); i++) {
                for (int j = i + 1; j < plans.size(); j++) {
                    double moved = PlanSignature.daysMoved(plans.get(i), plans.get(j));
                    sumMoved += moved;
                    maxMoved = Math.max(maxMoved, moved);
                    pairs++;
                }
            }
            double avgMoved = pairs == 0 ? 0 : sumMoved / pairs;
            double maxPct = 100.0 * maxMoved / instance.totalStudyDays();

            System.out.printf(Locale.ROOT,
                    "| `%s` | %.5f | %.6f | %.3f%% | %.3f%% | %d/%d | %.1f | %.1f | %.1f%% |%n",
                    instance.id(), mean, sd, cv, amplitude, distinct.size(), SEEDS.length,
                    avgMoved, maxMoved, maxPct);

            csv.append(String.format(Locale.ROOT, "variancia_seeds,%s,%.4f,%.4f,%.4f,%.4f,%d,%d,%.2f,%.2f,%.2f%n",
                    instance.id(), mean, sd, cv, amplitude, distinct.size(), SEEDS.length,
                    avgMoved, maxMoved, maxPct));
        }
        System.out.println();
    }

    // ------------------------------------------------------------------
    // 3. Hyperparameter sweep
    // ------------------------------------------------------------------

    /**
     * Small grid over population size, generation count and mutation rate.
     * <p>
     * Deliberately not exhaustive: each axis is swept around the production value while the others
     * stay at it, which is enough to see whether quality is sensitive to the parameter and what it
     * costs. Reported as a percentage of the exactly optimal fitness, so the numbers are comparable
     * across instances (raw fitness is not — auditoria §3.1).
     */
    private static void hyperparameterSweep(BenchmarkHarness harness, StringBuilder csv) {
        List<BenchmarkInstance> sweepInstances = InstanceLibrary.all().stream()
                .filter(i -> Set.of("I2-medio-tipico", "I4-pesos-extremos", "I8-escala").contains(i.id()))
                .toList();

        int[] populations = {10, 25, 50, 100, 200};
        int[] generations = {10, 50, 100, 250, 500};
        double[] mutationRates = {0.01, 0.05, 0.15, 0.30, 0.50};

        ConfigurableGaRunner runner = new ConfigurableGaRunner();
        com.ia.project.dynamicstudyplanner.benchmark.strategy.MarginalGainOptimum optimum =
                new com.ia.project.dynamicstudyplanner.benchmark.strategy.MarginalGainOptimum();

        System.out.println("### 3. Sensibilidade a hiperparametros\n");
        System.out.println("Qualidade = fitness / fitness do otimo exato. Tempo = media de 3 seeds.\n");

        csv.append("\nsecao,instancia,eixo,valor,qualidade_pct,tempo_ms\n");

        for (BenchmarkInstance instance : sweepInstances) {
            EvolutionContext context = harness.contextFor(instance);
            double best = context.fitnessEvaluator()
                    .evaluate(optimum.plan(instance, context, 0L), context);

            System.out.printf("#### `%s` (otimo exato = %.5f)%n%n", instance.id(), best);
            System.out.println("| Eixo | Valor | Qualidade (% do otimo) | Tempo medio (ms) |");
            System.out.println("|---|---|---|---|");

            for (int pop : populations) {
                sweepRow(csv, runner, instance, context, best, "populacao", pop,
                        pop, 100, ConfigurableGaRunner.PROD_MUTATION_RATE);
            }
            for (int gen : generations) {
                sweepRow(csv, runner, instance, context, best, "geracoes", gen,
                        50, gen, ConfigurableGaRunner.PROD_MUTATION_RATE);
            }
            for (double rate : mutationRates) {
                sweepRow(csv, runner, instance, context, best, "taxa_mutacao", rate,
                        50, 100, rate);
            }
            System.out.println();
        }
    }

    private static void sweepRow(StringBuilder csv, ConfigurableGaRunner runner,
                                 BenchmarkInstance instance, EvolutionContext context, double best,
                                 String axis, Number axisValue,
                                 int pop, int gen, double mutationRate) {
        double qualitySum = 0;
        double timeSum = 0;
        int reps = 3;
        for (int r = 0; r < reps; r++) {
            ConfigurableGaRunner.Result result = runner.run(instance, context, pop, gen, mutationRate, 7000L + r);
            qualitySum += 100.0 * result.fitness() / best;
            timeSum += result.elapsedMillis();
        }
        double quality = qualitySum / reps;
        double time = timeSum / reps;

        System.out.printf(Locale.ROOT, "| %s | %s | %.3f%% | %.0f |%n", axis, axisValue, quality, time);
        csv.append(String.format(Locale.ROOT, "sweep,%s,%s,%s,%.4f,%.1f%n",
                instance.id(), axis, axisValue, quality, time));
    }

    private static double stdDev(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0.0;
        }
        double acc = 0.0;
        for (double v : values) {
            acc += (v - mean) * (v - mean);
        }
        return Math.sqrt(acc / (values.size() - 1));
    }

    private RobustnessMain() {
    }
}
