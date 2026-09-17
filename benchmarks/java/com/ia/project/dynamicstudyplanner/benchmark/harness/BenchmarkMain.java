package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entry point that runs the full comparison and writes the artefacts used by
 * {@code docs/revisao-ag/03-validacao.md}.
 * <p>
 * Run with:
 * <pre>
 *   ./mvnw -q test-compile exec:java \
 *       -Dexec.mainClass=com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkMain \
 *       -Dexec.classpathScope=test
 * </pre>
 * or from an IDE. It writes {@code benchmarks/results/resultados.csv} and prints the Markdown
 * tables to stdout.
 */
public final class BenchmarkMain {

    private static final int REPETITIONS = 7;

    public static void main(String[] args) throws IOException {
        List<BenchmarkInstance> instances = InstanceLibrary.all();
        BenchmarkHarness harness = new BenchmarkHarness();

        System.out.println("Executando " + instances.size() + " instancias x "
                + harness.strategies().size() + " estrategias, " + REPETITIONS
                + " repeticoes para as estocasticas...");
        long start = System.nanoTime();
        List<StrategyOutcome> outcomes = harness.run(instances, REPETITIONS);
        long totalSeconds = (System.nanoTime() - start) / 1_000_000_000L;
        System.out.println("Concluido em " + totalSeconds + "s\n");

        writeCsv(outcomes);
        printInstanceTable(instances);
        printFitnessTable(instances, outcomes);
        printBusinessTable(instances, outcomes);
        printRelativeTable(instances, outcomes);
        printNoiseSummary(outcomes);
    }

    // ------------------------------------------------------------------
    // Artefacts
    // ------------------------------------------------------------------

    private static void writeCsv(List<StrategyOutcome> outcomes) throws IOException {
        Path dir = Path.of("benchmarks", "results");
        Files.createDirectories(dir);
        Path csv = dir.resolve("resultados.csv");

        StringBuilder sb = new StringBuilder(
                "instancia,estrategia,repeticoes,fitness_media,fitness_desvio,fitness_min,fitness_max,"
                        + "tempo_ms_media,retencao_media,pct_janela_retencao,dias_sobrecarga,"
                        + "razao_horas_agendadas,share_top_disciplina,status\n");
        for (StrategyOutcome o : outcomes) {
            sb.append(String.format(Locale.ROOT, "%s,%s,%d,%.4f,%.4f,%.4f,%.4f,%.1f,%.4f,%.4f,%.2f,%.4f,%.4f,%s%n",
                    o.instanceId(), o.strategyId(), o.repetitions(),
                    o.meanFitness(), o.stdDevFitness(), o.minFitness(), o.maxFitness(),
                    o.meanElapsedMillis(), o.meanRetentionAtExam(), o.meanPctInRetentionWindow(),
                    o.meanCognitiveOverloadDays(), o.meanScheduledHoursRatio(),
                    o.meanTopSubjectShare(), o.scheduleStatus()));
        }
        Files.writeString(csv, sb.toString());
        System.out.println("CSV escrito em " + csv.toAbsolutePath() + "\n");
    }

    private static void printInstanceTable(List<BenchmarkInstance> instances) {
        System.out.println("### Instancias\n");
        System.out.println("| ID | Disciplinas | Horizonte (dias) | h/semana | Orcamento (dias) | Pop x Ger |");
        System.out.println("|---|---|---|---|---|---|");
        for (BenchmarkInstance i : instances) {
            System.out.printf(Locale.ROOT, "| `%s` | %d | %d | %d | %d | %d x %d |%n",
                    i.id(), i.subjectCount(), i.horizonDays(),
                    i.profile().getTotalWeeklyHours(), i.totalStudyDays(),
                    i.populationSize(), i.numGenerations());
        }
        System.out.println();
    }

    private static void printFitnessTable(List<BenchmarkInstance> instances, List<StrategyOutcome> outcomes) {
        System.out.println("### Fitness media (maior e melhor) e tempo\n");
        System.out.println("| Instancia | AG producao | Guloso prioridade | Otimo exato "
                + "| Melhor de N alea. | Uniforme | Aleatorio | t(AG) ms | t(otimo) ms |");
        System.out.println("|---|---|---|---|---|---|---|---|---|");
        for (BenchmarkInstance i : instances) {
            Map<String, StrategyOutcome> byStrategy = index(outcomes, i.id());
            System.out.printf(Locale.ROOT, "| `%s` | %.5f | %.5f | %.5f | %.5f | %.5f | %.5f | %.0f | %.0f |%n",
                    i.id(),
                    byStrategy.get("ag-producao").meanFitness(),
                    byStrategy.get("guloso-prioridade").meanFitness(),
                    byStrategy.get("otimo-exato").meanFitness(),
                    byStrategy.get("melhor-de-n-aleatorios").meanFitness(),
                    byStrategy.get("uniforme").meanFitness(),
                    byStrategy.get("aleatorio").meanFitness(),
                    byStrategy.get("ag-producao").meanElapsedMillis(),
                    byStrategy.get("otimo-exato").meanElapsedMillis());
        }
        System.out.println();
    }

    private static void printBusinessTable(List<BenchmarkInstance> instances, List<StrategyOutcome> outcomes) {
        System.out.println("### Metricas de negocio\n");
        System.out.println("| Instancia | Estrategia | % na janela de retencao | Retencao media "
                + "| Dias c/ sobrecarga | Horas entregues / planejadas | Share top-1 |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (BenchmarkInstance i : instances) {
            for (StrategyOutcome o : outcomes) {
                if (!o.instanceId().equals(i.id())) {
                    continue;
                }
                System.out.printf(Locale.ROOT, "| `%s` | %s | %.1f%% | %.3f | %.1f | %.2f | %.1f%% |%n",
                        i.id(), o.strategyId(),
                        100 * o.meanPctInRetentionWindow(), o.meanRetentionAtExam(),
                        o.meanCognitiveOverloadDays(), o.meanScheduledHoursRatio(),
                        100 * o.meanTopSubjectShare());
            }
        }
        System.out.println();
    }

    private static void printRelativeTable(List<BenchmarkInstance> instances, List<StrategyOutcome> outcomes) {
        System.out.println("### AG relativo aos baselines (positivo = AG melhor)\n");
        System.out.println("| Instancia | vs guloso prioridade | vs melhor de N alea. "
                + "| vs uniforme | vs aleatorio | gap ate o otimo exato |");
        System.out.println("|---|---|---|---|---|---|");
        for (BenchmarkInstance i : instances) {
            Map<String, StrategyOutcome> byStrategy = index(outcomes, i.id());
            double ga = byStrategy.get("ag-producao").meanFitness();
            System.out.printf(Locale.ROOT, "| `%s` | %+.2f%% | %+.2f%% | %+.2f%% | %+.2f%% | %.2f%% |%n",
                    i.id(),
                    pctDelta(ga, byStrategy.get("guloso-prioridade").meanFitness()),
                    pctDelta(ga, byStrategy.get("melhor-de-n-aleatorios").meanFitness()),
                    pctDelta(ga, byStrategy.get("uniforme").meanFitness()),
                    pctDelta(ga, byStrategy.get("aleatorio").meanFitness()),
                    -pctDelta(ga, byStrategy.get("otimo-exato").meanFitness()));
        }
        System.out.println();
    }

    private static void printNoiseSummary(List<StrategyOutcome> outcomes) {
        System.out.println("### Ruido run-to-run do AG (calibra o limiar do teste)\n");
        System.out.println("| Instancia | media | desvio | min | max | CV% | amplitude% |");
        System.out.println("|---|---|---|---|---|---|---|");
        List<StrategyOutcome> gaRuns = outcomes.stream()
                .filter(o -> o.strategyId().equals("ag-producao"))
                .toList();
        double worstCv = 0.0;
        double worstRange = 0.0;
        for (StrategyOutcome o : gaRuns) {
            double rangePct = o.meanFitness() == 0 ? 0
                    : 100.0 * (o.maxFitness() - o.minFitness()) / o.meanFitness();
            worstCv = Math.max(worstCv, o.fitnessCoefficientOfVariationPct());
            worstRange = Math.max(worstRange, rangePct);
            System.out.printf(Locale.ROOT, "| `%s` | %.5f | %.6f | %.5f | %.5f | %.3f%% | %.3f%% |%n",
                    o.instanceId(), o.meanFitness(), o.stdDevFitness(),
                    o.minFitness(), o.maxFitness(), o.fitnessCoefficientOfVariationPct(), rangePct);
        }
        System.out.printf(Locale.ROOT, "%nPior CV observado: %.3f%% | pior amplitude observada: %.3f%%%n",
                worstCv, worstRange);
    }

    // ------------------------------------------------------------------

    private static Map<String, StrategyOutcome> index(List<StrategyOutcome> outcomes, String instanceId) {
        return outcomes.stream()
                .filter(o -> o.instanceId().equals(instanceId))
                .collect(Collectors.toMap(StrategyOutcome::strategyId, Function.identity(),
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    /** Percentage by which {@code value} exceeds {@code reference}. */
    private static double pctDelta(double value, double reference) {
        if (reference == 0.0) {
            return 0.0;
        }
        return 100.0 * (value - reference) / Math.abs(reference);
    }

    private BenchmarkMain() {
    }
}
