package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkHarness;
import com.ia.project.dynamicstudyplanner.benchmark.harness.StrategyOutcome;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.HighLoadInstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.metric.CorrelationAggregate;
import com.ia.project.dynamicstudyplanner.benchmark.metric.Spearman;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Locates the demand ratio at which the fitness stops helping the retention metric and starts
 * hurting it.
 * <p>
 * Answers the open question left as limitation L4 in {@code docs/revisao-ag/05-fitness-function.md}:
 * the fitness/retention correlation was neutralised on most instances but stayed at −0.88 on
 * {@code I8}. Because {@code I8} differs from the others in many ways at once, that single point
 * could not be attributed to anything. Running the same measurement across
 * {@link HighLoadInstanceLibrary}, where only the budget moves, turns it into a boundary.
 * <p>
 * Produces {@code docs/revisao-ag/06-regime-alta-carga.md}. Run with:
 * <pre>
 *   java -cp "target/classes:target/test-classes:$(cat cp.txt)" \
 *        com.ia.project.dynamicstudyplanner.benchmark.robustness.RegimeCorrelationMain
 * </pre>
 */
public final class RegimeCorrelationMain {

    /**
     * Repetitions per instance for the stochastic planners.
     * <p>
     * <b>Deliberately 5, while {@code BenchmarkMain} uses 7.</b> This class runs three sweeps over
     * ~30 instances and the extra repetitions bought nothing measurable, so the cheaper setting was
     * kept. The consequence is that the same instance can be quoted at 76.8% here and 76.6% in the
     * baselines report - sampling noise, not disagreement. Any document quoting both has to say
     * which is which (gap G10, docs/revisao-ag/07-correcao-metrica-e-ausubel.md §6).
     */
    private static final int REPETITIONS = 5;

    /** Planners correlated per instance, and therefore the sample size behind each coefficient. */
    private static final int STRATEGIES_PER_INSTANCE = 6;

    /** One measured point: an instance, its demand ratio, and how fitness relates to retention there. */
    private record RegimePoint(
            String instanceId,
            double demandRatio,
            double importanceDispersion,
            double correlation,
            double retentionSpreadPp,
            double bestRetention,
            double gaRetention
    ) {
    }

    public static void main(String[] args) throws IOException {
        BenchmarkHarness harness = new BenchmarkHarness();

        List<BenchmarkInstance> family = HighLoadInstanceLibrary.all();
        List<BenchmarkInstance> original = InstanceLibrary.all();

        System.out.println("### Familia controlada de alta carga\n");
        System.out.println("| Instancia | Orcamento (dias) | Razao demanda/orcamento | Observacao |");
        System.out.println("|---|---|---|---|");
        for (BenchmarkInstance i : family) {
            System.out.printf(Locale.ROOT, "| `%s` | %d | %.2f | %s |%n",
                    i.id(), i.totalStudyDays(), HighLoadInstanceLibrary.demandRatio(i),
                    i.description().split("\\.")[0] + ".");
        }
        System.out.println();

        List<BenchmarkInstance> byCount = HighLoadInstanceLibrary.bySubjectCount();

        List<RegimePoint> byRatio = new ArrayList<>(measure(harness, family));
        byRatio.addAll(measure(harness, original));
        byRatio.sort(Comparator.comparingDouble(RegimePoint::demandRatio));

        System.out.println("## Varredura 1 — razao demanda/orcamento (n = 20 fixo)\n");
        report(byRatio);

        List<RegimePoint> byCountPoints = measure(harness, byCount);
        System.out.println("\n## Varredura 2 — numero de disciplinas (razao ~2.0 fixa)\n");
        System.out.println("| Instancia | Disciplinas | Dispersao Imp. | Spearman | Amplitude da retencao (pp) | Melhor retencao | Retencao do AG |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (int i = 0; i < byCountPoints.size(); i++) {
            RegimePoint p = byCountPoints.get(i);
            String rho = Double.isNaN(p.correlation()) ? "n/d"
                    : String.format(Locale.ROOT, "%+.3f", p.correlation());
            System.out.printf(Locale.ROOT, "| `%s` | %d | %.0f:1 | %s | %.1f | %.1f%% | %.1f%% |%n",
                    p.instanceId(), byCount.get(i).subjectCount(), p.importanceDispersion(), rho,
                    p.retentionSpreadPp(), p.bestRetention(), p.gaRetention());
        }

        System.out.printf(Locale.ROOT, "%nAgregado da varredura 2: %s%n",
                aggregateOf(byCountPoints).format());

        List<BenchmarkInstance> byDispersion = HighLoadInstanceLibrary.byWeightDispersion();
        List<RegimePoint> byDispersionPoints = measure(harness, byDispersion);
        System.out.println("\n## Varredura 3 — dispersao dos pesos do edital (n = 20, razao ~2.0 fixas)\n");
        System.out.println("| Instancia | Dispersao eixo | Dispersao Imp. | Spearman | Amplitude da retencao (pp) | Melhor retencao | Retencao do AG |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (RegimePoint p : byDispersionPoints) {
            String rho = Double.isNaN(p.correlation()) ? "n/d"
                    : String.format(Locale.ROOT, "%+.3f", p.correlation());
            System.out.printf(Locale.ROOT, "| `%s` | %s:1 | %.0f:1 | %s | %.1f | %.1f%% | %.1f%% |%n",
                    p.instanceId(), p.instanceId().substring(1).replaceFirst("^0+", ""),
                    p.importanceDispersion(), rho,
                    p.retentionSpreadPp(), p.bestRetention(), p.gaRetention());
        }

        System.out.printf(Locale.ROOT, "%nAgregado da varredura 3: %s%n",
                aggregateOf(byDispersionPoints).format());

        List<RegimePoint> all = new ArrayList<>(byRatio);
        all.addAll(byCountPoints);
        all.addAll(byDispersionPoints);
        writeCsv(all);
    }

    private static List<RegimePoint> measure(BenchmarkHarness harness, List<BenchmarkInstance> instances) {
        List<RegimePoint> points = new ArrayList<>();
        for (BenchmarkInstance instance : instances) {
            List<StrategyOutcome> outcomes = harness.run(List.of(instance), REPETITIONS);

            List<Double> fitness = outcomes.stream().map(StrategyOutcome::meanFitness).toList();
            List<Double> retention = outcomes.stream()
                    .map(StrategyOutcome::meanPctInRetentionWindow).toList();

            double spread = 100 * (retention.stream().mapToDouble(Double::doubleValue).max().orElse(0)
                    - retention.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            double best = 100 * retention.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double ga = 100 * outcomes.stream()
                    .filter(o -> o.strategyId().equals("ag-producao"))
                    .mapToDouble(StrategyOutcome::meanPctInRetentionWindow)
                    .findFirst().orElse(0);

            points.add(new RegimePoint(instance.id(),
                    HighLoadInstanceLibrary.demandRatio(instance),
                    HighLoadInstanceLibrary.importanceDispersion(instance),
                    Spearman.correlation(fitness, retention),
                    spread, best, ga));
        }
        return points;
    }

    private static void report(List<RegimePoint> points) {
        System.out.println("### Correlacao fitness x retencao por razao de demanda\n");
        System.out.println("Negativo = maximizar a fitness PIORA a retencao. `n/d` = todos os "
                + "planejadores saturam a metrica, correlacao indefinida.\n");
        System.out.println("| Instancia | Razao | Dispersao Imp. | Spearman | Amplitude da retencao (pp) | Melhor retencao | Retencao do AG |");
        System.out.println("|---|---|---|---|---|---|---|");

        for (RegimePoint p : points) {
            String rho = Double.isNaN(p.correlation()) ? "n/d"
                    : String.format(Locale.ROOT, "%+.3f", p.correlation());
            System.out.printf(Locale.ROOT, "| `%s` | %.2f | %.0f:1 | %s | %.1f | %.1f%% | %.1f%% |%n",
                    p.instanceId(), p.demandRatio(), p.importanceDispersion(), rho,
                    p.retentionSpreadPp(), p.bestRetention(), p.gaRetention());
        }

        // The boundary: the lowest ratio from which every measurable point is negative.
        double lastPositive = Double.NaN;
        double firstPersistentlyNegative = Double.NaN;
        for (RegimePoint p : points) {
            if (Double.isNaN(p.correlation())) {
                continue;
            }
            if (p.correlation() >= 0) {
                lastPositive = p.demandRatio();
                firstPersistentlyNegative = Double.NaN;
            } else if (Double.isNaN(firstPersistentlyNegative)) {
                firstPersistentlyNegative = p.demandRatio();
            }
        }

        System.out.printf(Locale.ROOT,
                "%nUltima razao com correlacao nao-negativa: **%.2f**%n", lastPositive);
        System.out.printf(Locale.ROOT,
                "A partir de qual razao a correlacao permanece negativa: **%.2f**%n",
                firstPersistentlyNegative);

        System.out.printf(Locale.ROOT, "%nAgregado das instancias originais: %s%n",
                aggregateOf(points).format());
    }

    /**
     * Summarises a set of measured points into one coefficient, through the only sanctioned method.
     * <p>
     * Pooling the observations into a single Spearman - what etapas 03 and 05 published - is invalid
     * here and reverses the sign of the conclusion; see {@link CorrelationAggregate} and
     * docs/revisao-ag/07-correcao-metrica-e-ausubel.md.
     */
    private static CorrelationAggregate.Result aggregateOf(List<RegimePoint> points) {
        List<CorrelationAggregate.InstanceCorrelation> entries = new ArrayList<>(points.size());
        for (RegimePoint p : points) {
            entries.add(new CorrelationAggregate.InstanceCorrelation(
                    p.instanceId(), p.correlation(), STRATEGIES_PER_INSTANCE));
        }
        return CorrelationAggregate.aggregate(entries);
    }

    private static void writeCsv(List<RegimePoint> points) throws IOException {
        StringBuilder csv = new StringBuilder(
                "instancia,razao_demanda,dispersao_importancia,spearman,amplitude_retencao_pp,melhor_retencao,retencao_ag\n");
        for (RegimePoint p : points) {
            csv.append(String.format(Locale.ROOT, "%s,%.4f,%.2f,%s,%.2f,%.2f,%.2f%n",
                    p.instanceId(), p.demandRatio(), p.importanceDispersion(),
                    Double.isNaN(p.correlation()) ? "" : String.format(Locale.ROOT, "%.4f", p.correlation()),
                    p.retentionSpreadPp(), p.bestRetention(), p.gaRetention()));
        }
        Path out = Path.of("benchmarks", "results", "regime-alta-carga.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, csv.toString());
        System.out.println("\nCSV escrito em " + out.toAbsolutePath());
    }

    private RegimeCorrelationMain() {
    }
}
