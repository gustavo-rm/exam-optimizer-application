package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkHarness;
import com.ia.project.dynamicstudyplanner.benchmark.harness.StrategyOutcome;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.HighLoadInstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
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

    private static final int REPETITIONS = 5;

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
