package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.benchmark.metric.CorrelationAggregate;
import com.ia.project.dynamicstudyplanner.benchmark.metric.Spearman;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Re-derives the fitness/retention correlation from a set of harness runs and reports it as a
 * distribution rather than a point.
 *
 * <h2>Why this exists</h2>
 *
 * The etapa-03 "before" numbers were measured on a commit where the GA was <b>not reproducible</b>:
 * {@code AbstractMutationStrategy} drew from {@code Math.random()} and {@code ThreadLocalRandom},
 * neither seedable, and the fix only landed in etapa 04 (docs/revisao-ag/04-robustez.md §1, from 2/8
 * to 8/8 reproducible instances). Six replays of that commit showed {@code I3} moving between −0.941
 * and −0.899 and {@code I7} between −0.833 and −0.878, so a table of three-decimal point estimates
 * claimed a precision the measurement did not have
 * (docs/revisao-ag/06-verificacao-pos-rodada.md §5.3).
 * <p>
 * Seeding cannot repair that after the fact — the unseeded draws are in the code under measurement,
 * and patching them would no longer be the etapa-03 baseline. What can be done is to run it many
 * times and report mean ± standard deviation, which is what this class does. The result is the
 * official "before" baseline for every future comparison.
 *
 * <h2>Aggregation</h2>
 *
 * Per-run, per-instance Spearman via {@link Spearman}; across instances via
 * {@link CorrelationAggregate}, the single sanctioned method. The stacked pooling that etapas 03 and
 * 05 published is not computed here and must not be reintroduced.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 *   java -cp "target/classes:target/test-classes:$(cat cp.txt)" \
 *        com.ia...benchmark.robustness.BaselineReplayMain &lt;dir-com-csvs&gt; [rotulo]
 * </pre>
 *
 * where the directory holds one {@code resultados.csv} snapshot per run, any filename.
 */
public final class BaselineReplayMain {

    /** Planners correlated on each instance, and therefore the sample size behind each coefficient. */
    private static final int PLANNERS_PER_INSTANCE = 6;

    private BaselineReplayMain() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Uso: BaselineReplayMain <diretorio-com-csvs> [rotulo]");
            System.exit(2);
        }
        Path dir = Path.of(args[0]);
        String label = args.length > 1 ? args[1] : dir.getFileName().toString();

        List<Path> snapshots;
        try (var stream = Files.list(dir)) {
            snapshots = stream.filter(p -> p.toString().endsWith(".csv")).sorted().toList();
        }
        if (snapshots.isEmpty()) {
            System.err.println("Nenhum .csv em " + dir.toAbsolutePath());
            System.exit(2);
        }

        // instance -> one correlation per run
        Map<String, List<Double>> byInstance = new LinkedHashMap<>();
        List<Double> aggregatePerRun = new ArrayList<>();

        for (Path snapshot : snapshots) {
            Map<String, double[][]> series = readRun(snapshot);
            List<CorrelationAggregate.InstanceCorrelation> entries = new ArrayList<>();

            for (Map.Entry<String, double[][]> entry : series.entrySet()) {
                double rho = Spearman.correlation(box(entry.getValue()[0]), box(entry.getValue()[1]));
                byInstance.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(rho);
                entries.add(new CorrelationAggregate.InstanceCorrelation(
                        entry.getKey(), rho, entry.getValue()[0].length));
            }
            aggregatePerRun.add(CorrelationAggregate.aggregate(entries).correlation());
        }

        System.out.printf(Locale.ROOT, "## Baseline remedida: %s%n%n", label);
        System.out.printf(Locale.ROOT, "%d execucoes independentes, %d instancias, "
                + "%d planejadores por instancia.%n%n", snapshots.size(), byInstance.size(),
                PLANNERS_PER_INSTANCE);

        System.out.println("| Instancia | Spearman medio | Desvio-padrao | Min | Max | Execucoes definidas |");
        System.out.println("|---|---|---|---|---|---|");

        List<CorrelationAggregate.InstanceCorrelation> meanPerInstance = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : byInstance.entrySet()) {
            List<Double> defined = entry.getValue().stream().filter(v -> !Double.isNaN(v)).toList();
            if (defined.isEmpty()) {
                System.out.printf(Locale.ROOT, "| `%s` | n/d | - | - | - | 0 de %d |%n",
                        entry.getKey(), entry.getValue().size());
                meanPerInstance.add(new CorrelationAggregate.InstanceCorrelation(
                        entry.getKey(), Double.NaN, PLANNERS_PER_INSTANCE));
                continue;
            }

            double mean = defined.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double sd = standardDeviation(defined, mean);
            System.out.printf(Locale.ROOT, "| `%s` | **%+.3f** | %.3f | %+.3f | %+.3f | %d de %d |%n",
                    entry.getKey(), mean, sd,
                    defined.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),
                    defined.stream().mapToDouble(Double::doubleValue).max().orElseThrow(),
                    defined.size(), entry.getValue().size());

            meanPerInstance.add(new CorrelationAggregate.InstanceCorrelation(
                    entry.getKey(), mean, PLANNERS_PER_INSTANCE));
        }

        CorrelationAggregate.Result aggregate = CorrelationAggregate.aggregate(meanPerInstance);
        System.out.printf(Locale.ROOT, "%n**Agregado (Fisher z sobre as medias por instancia): %s**%n",
                aggregate.format());

        List<Double> definedRuns = aggregatePerRun.stream().filter(v -> !Double.isNaN(v)).toList();
        if (!definedRuns.isEmpty()) {
            double mean = definedRuns.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            System.out.printf(Locale.ROOT,
                    "Agregado calculado por execucao e depois promediado: %+.3f (desvio %.3f, "
                            + "min %+.3f, max %+.3f) - as duas ordens de agregacao conferem.%n",
                    mean, standardDeviation(definedRuns, mean),
                    definedRuns.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),
                    definedRuns.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
        }
    }

    /**
     * Reads one harness snapshot into {@code instance -> [fitness[], retentionWindow[]]}, one entry
     * per planner, preserving the order the harness wrote them so the two series stay aligned.
     */
    private static Map<String, double[][]> readRun(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        String[] header = lines.get(0).split(",");
        int instanceCol = indexOf(header, "instancia");
        int fitnessCol = indexOf(header, "fitness_media");
        int windowCol = indexOf(header, "pct_janela_retencao");

        Map<String, List<double[]>> rows = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split(",");
            rows.computeIfAbsent(cells[instanceCol], k -> new ArrayList<>())
                    .add(new double[]{Double.parseDouble(cells[fitnessCol]),
                            Double.parseDouble(cells[windowCol])});
        }

        Map<String, double[][]> series = new LinkedHashMap<>();
        for (String instance : new TreeSet<>(rows.keySet())) {
            List<double[]> pairs = rows.get(instance);
            double[] fitness = new double[pairs.size()];
            double[] window = new double[pairs.size()];
            for (int i = 0; i < pairs.size(); i++) {
                fitness[i] = pairs.get(i)[0];
                window[i] = pairs.get(i)[1];
            }
            series.put(instance, new double[][]{fitness, window});
        }
        return series;
    }

    private static int indexOf(String[] header, String column) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equals(column)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Coluna ausente no CSV: " + column);
    }

    /** Sample standard deviation (n-1); zero for a single observation. */
    private static double standardDeviation(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0.0;
        }
        double acc = 0.0;
        for (double v : values) {
            acc += (v - mean) * (v - mean);
        }
        return Math.sqrt(acc / (values.size() - 1));
    }

    private static List<Double> box(double[] values) {
        List<Double> boxed = new ArrayList<>(values.length);
        for (double v : values) {
            boxed.add(v);
        }
        return boxed;
    }
}
