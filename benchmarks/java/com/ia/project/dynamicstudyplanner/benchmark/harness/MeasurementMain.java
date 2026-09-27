package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.DynamicStudyPlannerApplication;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.metric.MeasurementRow;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategies;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs the measurement and writes the CSV.
 *
 * <h2>Run it like this</h2>
 *
 * <pre>
 *   ./mvnw -q test-compile
 *   ./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
 *   java --enable-preview -cp "target/classes:target/test-classes:$(cat target/cp.txt)" \
 *        com.ia.project.dynamicstudyplanner.benchmark.harness.MeasurementMain
 * </pre>
 *
 * <h2>Why a real Spring context and not a hand-wired one</h2>
 *
 * The engines are beans, their fitness composition is a bean, the provenance resolver reads a
 * property, and the search budget reads two more. A harness that constructed them itself would be
 * measuring an arrangement that only exists inside the harness — the exact failure the old
 * {@code BenchmarkHarness.productionFitnessEvaluator()} had, where a term added to production had to
 * be remembered in a second list or the comparison was silently wrong. Here nothing is listed twice:
 * the composition comes out of the context, and so does everything scored against it.
 *
 * <p>The {@code baseline-core} profile is mandatory rather than convenient — without it the
 * application registers no engine, no selector and no endpoint at all.
 */
public final class MeasurementMain {

    /** Where the run writes its rows. */
    public static final Path OUTPUT = Path.of("benchmarks", "results", "measurement.csv");

    private MeasurementMain() {
    }

    /**
     * @param args ignored; the matrix and the seeds are fixed in the repository so a run is
     *             repeatable without remembering which flags produced it
     */
    public static void main(String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(
                DynamicStudyPlannerApplication.class)
                .profiles(PlanProtocol.PROFILE)
                .properties("server.port=0", "spring.main.banner-mode=off");

        try (ConfigurableApplicationContext context = builder.run()) {
            List<BenchmarkInstance> instances = InstanceLibrary.all();
            MeasurementHarness harness = harnessIn(context);

            long startedAt = System.nanoTime();
            List<MeasurementRow> rows = harness.runAll(instances);
            long elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000L;

            List<String> termNames = termNamesIn(context);
            MeasurementCsv.write(OUTPUT, termNames, rows);
            report(instances, rows, termNames, elapsedSeconds);
        }
    }

    /** The harness, assembled entirely from beans of the running context. */
    private static MeasurementHarness harnessIn(ConfigurableApplicationContext context) {
        FitnessComposition composition =
                context.getBean("sinapseFitnessComposition", FitnessComposition.class);
        PlanScoring scoring =
                new PlanScoring(composition, context.getBean(RetentionAlgorithm.class));
        return new MeasurementHarness(context.getBean(PlanEngineSelector.class), scoring,
                context.getBean(ImportanceStrategies.class),
                context.getBean(GeneticSearchBudget.class));
    }

    /** The composition's terms, in composition order: the per-term columns of the CSV. */
    private static List<String> termNamesIn(ConfigurableApplicationContext context) {
        FitnessComposition composition =
                context.getBean("sinapseFitnessComposition", FitnessComposition.class);
        List<String> names = new java.util.ArrayList<>();
        composition.objectives().forEach(objective -> names.add(objective.name()));
        composition.constraints().forEach(constraint -> names.add(constraint.name()));
        return List.copyOf(names);
    }

    /** What the run did, on stdout. Every number in the report comes from the CSV, not from here. */
    private static void report(List<BenchmarkInstance> instances, List<MeasurementRow> rows,
            List<String> termNames, long elapsedSeconds) {

        System.out.println("instances   : " + instances.size());
        System.out.println("conditions  : " + Condition.matrix().size()
                + " " + Condition.matrix().stream().map(Condition::label).toList());
        System.out.println("seeds       : " + MeasurementHarness.SEEDS.length);
        System.out.println("rows        : " + rows.size());
        System.out.println("fitness terms: " + termNames);
        System.out.println("columns     : " + MeasurementRow.header(termNames).size());
        System.out.println("elapsed     : " + elapsedSeconds + " s (two engine calls per row)");
        System.out.println("written     : " + OUTPUT.toAbsolutePath());
    }
}
