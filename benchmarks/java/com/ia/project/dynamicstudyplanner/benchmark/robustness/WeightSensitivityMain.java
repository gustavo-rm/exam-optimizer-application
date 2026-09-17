package com.ia.project.dynamicstudyplanner.benchmark.robustness;

import com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkHarness;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.strategy.ProductionGeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.engagement.EngagementProfile;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Weight sensitivity study — the third leg of the Option A formulation chosen in
 * {@code docs/revisao-ag/02-formulacao.md}.
 * <p>
 * The weights in {@link FitnessWeights} are product judgments, not empirical fits. What makes that
 * defensible is not a claim of optimality but a measurement of <b>stability</b>: how far does the
 * plan actually move when a weight is perturbed by ±20%? A result showing small movement means the
 * chosen point sits in a flat region and the exact values are not load-bearing; a result showing
 * large movement means the opposite, and would have to be disclosed as such.
 * <p>
 * Movement is measured on the <b>plan</b>, not the fitness. Two plans can score almost identically
 * while allocating very different days, and it is the plan the student receives
 * (docs/revisao-ag/04-robustez.md §4).
 * <p>
 * Feeds {@code docs/revisao-ag/05-fitness-function.md}. Run with:
 * <pre>
 *   java -cp "target/classes:target/test-classes:$(cat cp.txt)" \
 *        com.ia.project.dynamicstudyplanner.benchmark.robustness.WeightSensitivityMain
 * </pre>
 */
public final class WeightSensitivityMain {

    private static final double PERTURBATION = 0.20;
    private static final long SEED = 20260830L;

    /** One production objective with its weight overridden, for the perturbed pipelines. */
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

    public static void main(String[] args) throws IOException {
        List<BenchmarkInstance> instances = InstanceLibrary.all();

        double[] base = {
                FitnessWeights.SYLLABUS_MASTERY,
                FitnessWeights.RETENTION,
                FitnessWeights.COGNITIVE_LOAD
        };
        String[] names = {"edital", "retencao", "carga"};

        StringBuilder csv = new StringBuilder(
                "instancia,peso_perturbado,direcao,w_edital,w_retencao,w_carga,"
                        + "dias_movidos,pct_orcamento\n");

        System.out.printf(Locale.ROOT,
                "### Sensibilidade aos pesos (+/-%.0f%%)%n%n", PERTURBATION * 100);
        System.out.println("Distancia entre o plano base e o plano gerado com o peso perturbado, "
                + "em dias realocados e como fracao do orcamento.\n");
        System.out.println("| Instancia | edital -20% | edital +20% | retencao -20% | retencao +20% "
                + "| carga -20% | carga +20% | pior caso |");
        System.out.println("|---|---|---|---|---|---|---|---|");

        double worstOverall = 0.0;
        for (BenchmarkInstance instance : instances) {
            StudyPlan basePlan = planWith(instance, base);

            double[] moved = new double[6];
            int col = 0;
            for (int i = 0; i < base.length; i++) {
                for (double direction : new double[]{-PERTURBATION, PERTURBATION}) {
                    double[] perturbed = perturb(base, i, direction);
                    StudyPlan plan = planWith(instance, perturbed);
                    double pct = 100.0 * PlanSignature.normalizedDistance(basePlan, plan);
                    moved[col++] = pct;

                    csv.append(String.format(Locale.ROOT, "%s,%s,%+.0f%%,%.4f,%.4f,%.4f,%.1f,%.2f%n",
                            instance.id(), names[i], direction * 100,
                            perturbed[0], perturbed[1], perturbed[2],
                            PlanSignature.daysMoved(basePlan, plan), pct));
                }
            }

            double worst = 0.0;
            for (double m : moved) {
                worst = Math.max(worst, m);
            }
            worstOverall = Math.max(worstOverall, worst);

            System.out.printf(Locale.ROOT,
                    "| `%s` | %.1f%% | %.1f%% | %.1f%% | %.1f%% | %.1f%% | %.1f%% | **%.1f%%** |%n",
                    instance.id(), moved[0], moved[1], moved[2], moved[3], moved[4], moved[5], worst);
        }

        System.out.printf(Locale.ROOT,
                "%nPior deslocamento observado sobre todas as instancias e perturbacoes: **%.1f%% "
                        + "do orcamento**.%n", worstOverall);

        Path out = Path.of("benchmarks", "results", "sensibilidade-pesos.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, csv.toString());
        System.out.println("\nCSV escrito em " + out.toAbsolutePath());
    }

    /**
     * Scales one weight by {@code direction} and renormalises the rest, so the perturbed set still
     * sums to 1. Without renormalising, the perturbation would also change the overall fitness
     * scale and the comparison would confound two effects.
     */
    private static double[] perturb(double[] base, int index, double direction) {
        double[] out = base.clone();
        out[index] = base[index] * (1 + direction);

        double othersBase = 0.0;
        for (int i = 0; i < base.length; i++) {
            if (i != index) {
                othersBase += base[i];
            }
        }
        double remaining = 1.0 - out[index];
        for (int i = 0; i < base.length; i++) {
            if (i != index) {
                out[i] = othersBase == 0 ? 0 : base[i] / othersBase * remaining;
            }
        }
        return out;
    }

    /** Runs the production GA with a pipeline whose objective weights are the given triple. */
    private static StudyPlan planWith(BenchmarkInstance instance, double[] weights) {
        List<FitnessObjective> objectives = List.of(
                new Reweighted(new ScoreGainObjective(), weights[0]),
                new Reweighted(new RetentionObjective(), weights[1]),
                new Reweighted(new CognitiveLoadObjective(), weights[2])
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
                .plan(instance, contextFor(instance, evaluator), SEED);
    }

    /** Same context StudyOptimizerService builds, with the evaluator swapped for the perturbed one. */
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

    private WeightSensitivityMain() {
    }
}
