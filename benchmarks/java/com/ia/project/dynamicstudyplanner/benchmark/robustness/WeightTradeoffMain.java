package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkHarness;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.metric.MetricsCalculator;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanMetrics;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.PlanningStrategy;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.ProductionGeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.engagement.EngagementProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.ConstraintValidator;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.CognitiveLoadObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.LearningModel;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FitnessPenalty;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.engagement.DropoutRiskPredictor;
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prices the trade-off between the exam-score objective and the retention objective, so that
 * choosing between them becomes a business decision with numbers attached instead of a preference.
 * <p>
 * The question it answers is the one left open in {@code docs/revisao-ag/06-limite-troca-pesos.md}:
 * on {@code I3} and {@code I4}, the uniform-split baseline keeps far more of the syllabus inside the
 * retention window than the GA does, while the GA wins on fitness. Whether that is acceptable
 * depends on how much exam-weighted coverage a percentage point of retention is worth — which this
 * class measures and does not decide.
 * <p>
 * Two measurements, both reported in the same two currencies:
 * <ol>
 *   <li><b>Decomposition.</b> Every planner's plan, broken into O1 / O3 / O4 and the business
 *       metrics, so the source of the fitness ranking is visible term by term.</li>
 *   <li><b>Weight frontier.</b> The GA re-run with weight progressively shifted from O1 to O3
 *       (O4 held at its production value), showing what each step buys and what it costs.</li>
 * </ol>
 * Both currencies are always evaluated with the <b>production</b> objectives, never with the
 * perturbed pipeline that produced the plan. Otherwise every row would be scored by its own
 * yardstick and the columns could not be compared.
 * <p>
 * This class only measures. It never writes to {@link FitnessWeights}; adjusting the shipped weights
 * is the human decision this data exists to inform.
 * <p>
 * Run with:
 * <pre>
 *   java -cp "target/classes:target/test-classes:$(cat cp.txt)" \
 *        com.ia.project.dynamicstudyplanner.benchmark.robustness.WeightTradeoffMain
 * </pre>
 */
public final class WeightTradeoffMain {

    /** The two instances where the uniform split beats the GA on the retention-window metric. */
    private static final List<String> FOCUS = List.of("I3-grande-apertado", "I4-pesos-extremos");

    private static final long SEED = BenchmarkHarness.BASE_SEED;

    /**
     * Repetitions per measured point, seeded {@code SEED + r}.
     * <p>
     * Not optional here. The retention-window metric is a count of subjects over a total, so on
     * {@code I3} one subject is worth 4 percentage points and on {@code I4} ten — larger than several
     * of the differences being compared. A single seed cannot distinguish a real shift from one
     * subject crossing the threshold, so every number in this report is a mean over seeds.
     */
    private static final int REPETITIONS = 7;

    /**
     * Coverage floor used for the library-wide comparison: the smallest value that took both
     * {@code I3} and {@code I4} to 100% of subjects inside the retention window in the per-instance
     * sweep. Chosen from the measurement, not proposed as a production value.
     */
    private static final int FLOOR_PROBE = 4;

    /**
     * Weight pairs {@code (w1, w3)} along the frontier, with {@code w4} pinned at its production
     * value so that exactly one degree of freedom moves. The first entry is production today.
     */
    private static final double[][] FRONTIER = {
            {0.50, 0.30},
            {0.45, 0.35},
            {0.40, 0.40},
            {0.35, 0.45},
            {0.30, 0.50},
            {0.20, 0.60},
            {0.10, 0.70}
    };

    /** One production objective with its weight overridden, for the frontier pipelines. */
    private record Reweighted(FitnessObjective delegate, double weight) implements FitnessObjective {
        @Override
        public double calculateReward(StudyPlan plan, EvolutionContext context) {
            return delegate.calculateReward(plan, context);
        }

        @Override
        public double getWeight() {
            return weight;
        }
    }

    /** Seed-averaged measurement of one planner configuration on one instance. */
    private record Averaged(
            double o1,
            double o3,
            double o4,
            double fitness,
            double retentionWindow,
            double meanRetention,
            double overloadDays,
            double topShare,
            List<StudyPlan> plans
    ) {
    }

    public static void main(String[] args) throws IOException {
        BenchmarkHarness harness = new BenchmarkHarness();
        MetricsCalculator metrics = new MetricsCalculator(BenchmarkHarness.productionFitnessEvaluator());

        StringBuilder csv = new StringBuilder(
                "instancia,linha,w_edital,w_retencao,o1,o3,o4,fitness,"
                        + "pct_janela_retencao,retencao_media,dias_sobrecarga,share_top,dist_plano_pct\n");

        for (BenchmarkInstance instance : InstanceLibrary.all()) {
            if (!FOCUS.contains(instance.id())) {
                continue;
            }
            EvolutionContext context = harness.contextFor(instance);

            System.out.printf(Locale.ROOT, "## %s%n%n", instance.id());

            // ---- 1. Where the fitness ranking comes from, term by term ----
            System.out.printf(Locale.ROOT,
                    "### Decomposicao por planejador (media de %d sementes)%n%n", REPETITIONS);
            System.out.println("| Planejador | O1 edital | O3 retencao | O4 carga | Fitness "
                    + "| % janela retencao | Retencao media | Dias sobrecarga | Share top |");
            System.out.println("|---|---|---|---|---|---|---|---|---|");

            for (PlanningStrategy strategy : harness.strategies()) {
                int reps = strategy.deterministic() ? 1 : REPETITIONS;
                Averaged a = average(metrics, instance, context, reps,
                        seed -> strategy.plan(instance, context, seed));

                System.out.printf(Locale.ROOT,
                        "| %s | %.4f | %.4f | %.4f | %.4f | %.1f%% | %.4f | %.0f | %.1f%% |%n",
                        strategy.displayName(), a.o1(), a.o3(), a.o4(), a.fitness(),
                        100 * a.retentionWindow(), a.meanRetention(), a.overloadDays(),
                        100 * a.topShare());

                csv.append(row(instance.id(), strategy.id(),
                        FitnessWeights.SYLLABUS_MASTERY, FitnessWeights.RETENTION, a, Double.NaN));
            }

            // ---- 1b. The shape of the allocation, which is what the window metric reacts to ----
            System.out.println("\n### Formato da alocacao (semente base)\n");
            System.out.println("A metrica de janela e uma contagem com limiar, entao reage a cauda "
                    + "da distribuicao, nao a media ponderada que O3 mede.\n");
            System.out.println("| Planejador | Dias min | Mediana | Max | Disciplinas com cobertura < 1 "
                    + "| Disciplinas fora da janela |");
            System.out.println("|---|---|---|---|---|---|");

            for (PlanningStrategy strategy : harness.strategies()) {
                StudyPlan plan = strategy.plan(instance, context, SEED);
                int[] days = plan.getDaysPerSubject().values().stream()
                        .mapToInt(Integer::intValue).sorted().toArray();
                long underCovered = plan.getDaysPerSubject().entrySet().stream()
                        .filter(e -> e.getValue() < LearningModel.requiredSessions(
                                e.getKey(), context.planningHorizonDays()))
                        .count();
                PlanMetrics m = metrics.measure(instance, context, plan, 0L);

                System.out.printf(Locale.ROOT, "| %s | %d | %d | %d | %d de %d | %d de %d |%n",
                        strategy.displayName(), days[0], days[days.length / 2], days[days.length - 1],
                        underCovered, days.length,
                        Math.round((1 - m.pctInRetentionWindow()) * days.length), days.length);
            }

            // ---- 2. What each step along the frontier buys and costs ----
            System.out.println("\n### Fronteira de pesos (w4 = 0,20 fixo)\n");
            System.out.println("O1 e o % de janela sao sempre medidos com os objetivos de producao, "
                    + "nao com o pipeline perturbado que gerou o plano.\n");
            System.out.println("| w1 edital | w3 retencao | O1 edital | Delta O1 vs. producao "
                    + "| % janela retencao | Delta janela | Dias sobrecarga | Distancia do plano |");
            System.out.println("|---|---|---|---|---|---|---|---|");

            Averaged production = null;
            for (double[] pair : FRONTIER) {
                Averaged a = average(metrics, instance, context, REPETITIONS,
                        seed -> planWith(instance, pair[0], pair[1], seed));
                if (production == null) {
                    production = a;
                }

                // Distance is paired seed by seed: comparing seed 0's plan against seed 3's would
                // report GA run-to-run spread as if it were the weight change's doing.
                double distance = 100 * meanPairedDistance(production.plans(), a.plans());

                System.out.printf(Locale.ROOT,
                        "| %.2f | %.2f | %.4f | %+.2f%% | %.1f%% | %+.1f pp | %.0f | %.1f%% |%n",
                        pair[0], pair[1], a.o1(),
                        production.o1() == 0 ? 0 : 100 * (a.o1() / production.o1() - 1),
                        100 * a.retentionWindow(),
                        100 * (a.retentionWindow() - production.retentionWindow()),
                        a.overloadDays(), distance);

                csv.append(row(instance.id(), "fronteira", pair[0], pair[1], a, distance));
            }

            // ---- 3. The other lever: the minimum-days floor ----
            System.out.println("\n### Piso de dias minimos (pesos de producao inalterados)\n");
            System.out.println("Medido, nao implementado. O piso vive em `BaselineCalculator`, "
                    + "fora da fitness.\n");
            System.out.println("| Piso | O1 edital | Delta O1 vs. producao | % janela retencao "
                    + "| Delta janela | Dias min | Dias sobrecarga |");
            System.out.println("|---|---|---|---|---|---|---|");

            Averaged floorBase = null;
            for (int floor = 1; floor <= 5; floor++) {
                final int f = floor;
                if (f * instance.subjectCount() > instance.totalStudyDays()) {
                    System.out.printf(Locale.ROOT,
                            "| %d | inviavel: %d disciplinas x %d dias > orcamento de %d dias |||||%n",
                            f, instance.subjectCount(), f, instance.totalStudyDays());
                    continue;
                }

                ProductionGeneticAlgorithm ga = new ProductionGeneticAlgorithm(
                        BenchmarkHarness.productionFitnessEvaluator(), new FlooredBaseline(f));
                Averaged a = average(metrics, instance, context, REPETITIONS,
                        seed -> ga.plan(instance, context, seed));
                if (floorBase == null) {
                    floorBase = a;
                }

                int minDays = a.plans().get(0).getDaysPerSubject().values().stream()
                        .mapToInt(Integer::intValue).min().orElse(0);

                System.out.printf(Locale.ROOT,
                        "| %d | %.4f | %+.2f%% | %.1f%% | %+.1f pp | %d | %.0f |%n",
                        f, a.o1(),
                        floorBase.o1() == 0 ? 0 : 100 * (a.o1() / floorBase.o1() - 1),
                        100 * a.retentionWindow(),
                        100 * (a.retentionWindow() - floorBase.retentionWindow()),
                        minDays, a.overloadDays());

                csv.append(row(instance.id(), "piso-" + f,
                        FitnessWeights.SYLLABUS_MASTERY, FitnessWeights.RETENTION, a, Double.NaN));
            }
            System.out.println();
        }

        // ---- 3. The whole library at the extremes, to check for collateral damage ----
        System.out.println("## Efeito colateral em todas as instancias\n");
        System.out.println("Nenhuma das duas alavancas age so em I3 e I4. Comparacao entre a "
                + "producao atual, um deslocamento de peso moderado (0,40 / 0,40) e um piso de "
                + "4 dias com os pesos de producao intactos.\n");
        System.out.println("| Instancia | Disciplinas | Orcamento | O1 base | % janela base "
                + "| Delta O1 em 0,40/0,40 | Delta janela em 0,40/0,40 "
                + "| Delta O1 com piso 4 | Delta janela com piso 4 |");
        System.out.println("|---|---|---|---|---|---|---|---|---|");

        for (BenchmarkInstance instance : InstanceLibrary.all()) {
            EvolutionContext context = harness.contextFor(instance);

            Averaged base = average(metrics, instance, context, REPETITIONS,
                    seed -> planWith(instance, 0.50, 0.30, seed));
            Averaged shifted = average(metrics, instance, context, REPETITIONS,
                    seed -> planWith(instance, 0.40, 0.40, seed));

            String floorO1 = "inviavel";
            String floorWindow = "inviavel";
            if (FLOOR_PROBE * instance.subjectCount() <= instance.totalStudyDays()) {
                ProductionGeneticAlgorithm ga = new ProductionGeneticAlgorithm(
                        BenchmarkHarness.productionFitnessEvaluator(),
                        new FlooredBaseline(FLOOR_PROBE));
                Averaged floored = average(metrics, instance, context, REPETITIONS,
                        seed -> ga.plan(instance, context, seed));

                floorO1 = String.format(Locale.ROOT, "%+.2f%%",
                        base.o1() == 0 ? 0 : 100 * (floored.o1() / base.o1() - 1));
                floorWindow = String.format(Locale.ROOT, "%+.1f pp",
                        100 * (floored.retentionWindow() - base.retentionWindow()));
                csv.append(row(instance.id(), "colateral-piso-" + FLOOR_PROBE,
                        FitnessWeights.SYLLABUS_MASTERY, FitnessWeights.RETENTION,
                        floored, Double.NaN));
            }

            System.out.printf(Locale.ROOT,
                    "| `%s` | %d | %d | %.4f | %.1f%% | %+.2f%% | %+.1f pp | %s | %s |%n",
                    instance.id(), instance.subjectCount(), instance.totalStudyDays(),
                    base.o1(), 100 * base.retentionWindow(),
                    base.o1() == 0 ? 0 : 100 * (shifted.o1() / base.o1() - 1),
                    100 * (shifted.retentionWindow() - base.retentionWindow()),
                    floorO1, floorWindow);

            csv.append(row(instance.id(), "colateral-producao", 0.50, 0.30, base, Double.NaN));
            csv.append(row(instance.id(), "colateral-deslocado", 0.40, 0.40, shifted, Double.NaN));
        }

        Path out = Path.of("benchmarks", "results", "limite-troca-pesos.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, csv.toString());
        System.out.println("\nCSV escrito em " + out.toAbsolutePath());
    }

    /** Produces one plan for a given seed. */
    @FunctionalInterface
    private interface SeededPlanner {
        StudyPlan plan(long seed);
    }

    /**
     * Runs a planner over {@code reps} seeds and averages every reported quantity, keeping the
     * individual plans so that plan distance can be compared seed against matching seed.
     */
    private static Averaged average(MetricsCalculator metrics, BenchmarkInstance instance,
                                    EvolutionContext context, int reps, SeededPlanner planner) {
        ScoreGainObjective o1 = new ScoreGainObjective();
        RetentionObjective o3 = new RetentionObjective();
        CognitiveLoadObjective o4 = new CognitiveLoadObjective();

        List<StudyPlan> plans = new ArrayList<>(reps);
        double sumO1 = 0, sumO3 = 0, sumO4 = 0, sumFitness = 0;
        double sumWindow = 0, sumRetention = 0, sumOverload = 0, sumTopShare = 0;

        for (int r = 0; r < reps; r++) {
            StudyPlan plan = planner.plan(SEED + r);
            PlanMetrics m = metrics.measure(instance, context, plan, 0L);
            plans.add(plan);

            sumO1 += o1.calculateReward(plan, context);
            sumO3 += o3.calculateReward(plan, context);
            sumO4 += o4.calculateReward(plan, context);
            sumFitness += m.fitness();
            sumWindow += m.pctInRetentionWindow();
            sumRetention += m.meanRetentionAtExam();
            sumOverload += m.cognitiveOverloadDays();
            sumTopShare += m.topSubjectShare();
        }

        return new Averaged(sumO1 / reps, sumO3 / reps, sumO4 / reps, sumFitness / reps,
                sumWindow / reps, sumRetention / reps, sumOverload / reps, sumTopShare / reps,
                List.copyOf(plans));
    }

    /**
     * Mean plan distance between two runs, paired by seed. Falls back to comparing against the
     * first plan when one side is deterministic and therefore has a single entry.
     */
    private static double meanPairedDistance(List<StudyPlan> reference, List<StudyPlan> candidate) {
        double sum = 0;
        for (int i = 0; i < candidate.size(); i++) {
            StudyPlan ref = reference.get(Math.min(i, reference.size() - 1));
            sum += PlanSignature.normalizedDistance(ref, candidate.get(i));
        }
        return candidate.isEmpty() ? 0 : sum / candidate.size();
    }

    /**
     * The production minimum-days calculator with every floor lifted to at least {@code floor}.
     * <p>
     * Simulates a change to {@code BaselineCalculator.MIN_REQUIRED_DAYS} without making one. The
     * floor cannot be injected through {@link EvolutionContext}, because
     * {@code StudyOptimizerService} builds its own context and computes the floor itself — which is
     * also why {@code ProductionGeneticAlgorithm} has to take the calculator by constructor.
     * Subjects whose production floor already exceeds the target are left alone, so this only lifts
     * the tail that was being reduced to one or two days.
     */
    private static final class FlooredBaseline extends BaselineCalculator {

        private final int floor;

        private FlooredBaseline(int floor) {
            super(new ImportanceCalculator());
            this.floor = floor;
        }

        @Override
        public Map<Subject, Integer> calculateMinimumDays(Exam exam, StudentProfile profile) {
            Map<Subject, Integer> production = new java.util.HashMap<>(
                    super.calculateMinimumDays(exam, profile));
            production.replaceAll((subject, days) -> Math.max(days, floor));
            return Map.copyOf(production);
        }
    }

    private static String row(String instanceId, String label, double w1, double w3,
                              Averaged a, double distancePct) {
        return String.format(Locale.ROOT,
                "%s,%s,%.2f,%.2f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f,%.4f,%s%n",
                instanceId, label, w1, w3, a.o1(), a.o3(), a.o4(), a.fitness(),
                a.retentionWindow(), a.meanRetention(), a.overloadDays(), a.topShare(),
                Double.isNaN(distancePct) ? "" : String.format(Locale.ROOT, "%.2f", distancePct));
    }

    /**
     * Runs the production GA with the objective weights set to {@code (w1, w3, 1 - w1 - w3)}.
     * The third weight is derived rather than passed so the triple always sums to 1, which is what
     * {@code FitnessEvaluator} asserts on construction.
     */
    private static StudyPlan planWith(BenchmarkInstance instance, double w1, double w3, long seed) {
        List<FitnessObjective> objectives = List.of(
                new Reweighted(new ScoreGainObjective(), w1),
                new Reweighted(new RetentionObjective(), w3),
                new Reweighted(new CognitiveLoadObjective(), 1.0 - w1 - w3)
        );
        List<FitnessPenalty> penalties = List.of(
                new DropoutRiskPenalty(new DropoutRiskPredictor()),
                new FatigueAndSustainabilityPenalty(new FatigueAndEnergyModel())
        );
        List<ConstraintValidator> constraints = List.of(
                new MinimumDaysConstraint(),
                new MandatoryReviewConstraint(new HybridRetentionEngine())
        );

        FitnessEvaluator evaluator = new FitnessEvaluator(objectives, penalties, constraints);
        return new ProductionGeneticAlgorithm(evaluator)
                .plan(instance, contextFor(instance, evaluator), seed);
    }

    /** Same context the harness builds, with the evaluator swapped for the reweighted one. */
    private static EvolutionContext contextFor(BenchmarkInstance instance, FitnessEvaluator evaluator) {
        ImportanceCalculator importanceCalculator = new ImportanceCalculator();
        Map<Subject, Double> importance =
                importanceCalculator.calculatePersonalizedImportance(instance.exam(), instance.profile());
        Map<Subject, Integer> minimumDays = new BaselineCalculator(importanceCalculator)
                .calculateMinimumDays(instance.exam(), instance.profile());

        return EvolutionContext.builder()
                .importanceScores(importance)
                .minimumDaysPerSubject(minimumDays)
                .studentState(instance.profile().getState())
                .fitnessEvaluator(evaluator)
                .retentionProfile(new RetentionProfile(Map.of()))
                .planStartDate(instance.planStartDate())
                .engagementProfile(EngagementProfile.baseline())
                .planningHorizonDays(Math.max(1, (int) instance.horizonDays()))
                .hoursPerStudyDay(Math.max(1, (int) Math.ceil(instance.profile().getTotalWeeklyHours() / 7.0)))
                .maxDailyCognitiveLoad(new CognitiveLoadCalculator().calculate(instance.profile(), instance.exam()))
                .build();
    }

    private WeightTradeoffMain() {
    }
}
