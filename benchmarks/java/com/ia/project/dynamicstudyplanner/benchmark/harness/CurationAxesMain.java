package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.DynamicStudyPlannerApplication;
import com.ia.project.dynamicstudyplanner.benchmark.instance.CurationInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.CurationLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.instance.EdgeStrengthMix;
import com.ia.project.dynamicstudyplanner.benchmark.metric.CurationMetrics;
import com.ia.project.dynamicstudyplanner.benchmark.metric.CurationRow;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategies;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A medição de curadoria (EOA-1): quais distinções curadas mudam o plano, e em que regime.
 *
 * <h2>O que roda</h2>
 *
 * As células de {@link CurationDesign}, cada uma sob os três motores de {@link Condition#ENGINES} com a
 * política de precedência padrão de cada um, nas cinco sementes de {@link MeasurementHarness#SEEDS}.
 * Toda linha passa por {@link MeasurementHarness#measured}: as mesmas duas chamadas, as mesmas
 * invariantes do grupo (a), a mesma repontuação a posteriori. Uma violação aborta, não vira linha.
 *
 * <h2>A proveniência fica em {@code all}</h2>
 *
 * Toda aresta destas instâncias é {@code CURATED}, como no catálogo de exemplo, então {@code curated}
 * e {@code all} admitem exatamente as mesmas arestas. A proveniência não é eixo desta medição; o eixo
 * de força é que é, e ele reclassifica em vez de remover — ver {@code EdgeStrengthMix}.
 *
 * <h2>Uma checagem a mais, só desta medição</h2>
 *
 * Sob {@code all-hard} nenhuma aresta pode estar invertida no plano nem ter o dependente agendado sem o
 * pré-requisito: a leitura forte de {@code HARD}, que {@code Invariants} já verifica, implica as duas.
 * Se a régua nova de {@link CurationMetrics} contar alguma coisa ali, a régua está errada, e a execução
 * aborta antes de gravar números medidos com ela.
 *
 * <h2>Como rodar</h2>
 *
 * <pre>
 *   ./mvnw -q test-compile
 *   ./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
 *   java -cp "target/classes:target/test-classes:$(cat target/cp.txt)" \
 *        com.ia.project.dynamicstudyplanner.benchmark.harness.CurationAxesMain
 * </pre>
 */
public final class CurationAxesMain {

    /** Onde a medição escreve. */
    public static final Path OUTPUT = Path.of("benchmarks", "results", "curation-axes.csv");

    private CurationAxesMain() {
    }

    /**
     * @param args ignorados; a matriz, as instâncias e as sementes estão fixadas no repositório
     */
    public static void main(String[] args) {
        // Invariante de ambiente, antes de qualquer medicao: ver Environment.
        Environment.requireExpectedJdk();

        SpringApplicationBuilder builder = new SpringApplicationBuilder(
                DynamicStudyPlannerApplication.class)
                .profiles(PlanProtocol.PROFILE)
                .properties("server.port=0", "spring.main.banner-mode=off");

        try (ConfigurableApplicationContext context = builder.run()) {
            MeasurementHarness harness = MeasurementMain.harnessIn(context);
            ImportanceStrategies importance = context.getBean(ImportanceStrategies.class);
            List<String> terms = MeasurementMain.termNamesIn(context);
            List<CurationDesign.Cell> cells = CurationDesign.cells();

            List<String> lines = new ArrayList<>();
            lines.add(String.join(",", CurationRow.header(terms)));
            long startedAt = System.nanoTime();
            for (CurationDesign.Cell cell : cells) {
                CurationInstance instance = CurationLibrary.build(cell.point());
                for (CurationRow row : measureCell(harness, importance, cell.role(), instance)) {
                    lines.add(String.join(",", row.cells(terms)));
                }
                System.out.printf("%s %s done (%d rows so far)%n", cell.role(), instance.point().id(),
                        lines.size() - 1);
            }
            long elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000L;

            MeasurementCsv.writeLines(OUTPUT, lines);
            Environment.writeBeside(OUTPUT);
            System.out.printf("cells %d | rows %d | elapsed %d s | written %s%n", cells.size(),
                    lines.size() - 1, elapsedSeconds, OUTPUT.toAbsolutePath());
        }
    }

    /**
     * Uma célula: três motores, cinco sementes.
     *
     * @param harness    o instrumento
     * @param importance o registro de estratégias de produção, para a dispersão efetiva
     * @param role       o papel da célula no desenho
     * @param instance   a instância
     * @return as linhas, motor por fora e semente por dentro
     */
    static List<CurationRow> measureCell(MeasurementHarness harness, ImportanceStrategies importance,
            String role, CurationInstance instance) {

        List<CurationRow> rows = new ArrayList<>();
        for (String engine : Condition.ENGINES) {
            Condition condition = new Condition(engine, EdgeProvenanceFilter.ALL);
            for (long seed : MeasurementHarness.SEEDS) {
                MeasurementHarness.Measured measured =
                        harness.measured(instance.benchmark(), condition, seed);
                Collection<Double> perTopic = importance.resolve(measured.request())
                        .importanceOf(measured.request()).values();
                CurationMetrics metrics =
                        CurationMetrics.of(instance, measured.response(), perTopic);
                checkAllHard(instance, condition, seed, metrics);
                rows.add(new CurationRow(role, instance, measured.row(), metrics));
            }
        }
        return rows;
    }

    /** A régua nova contra a leitura forte de {@code HARD}: ver o comentário da classe. */
    private static void checkAllHard(CurationInstance instance, Condition condition, long seed,
            CurationMetrics metrics) {

        if (instance.point().strength() != EdgeStrengthMix.ALL_HARD) {
            return;
        }
        if (metrics.orderInversions() != 0 || metrics.dependentsWithoutPrerequisite() != 0) {
            throw new MeasurementAbortedException(instance.benchmark(), condition, seed,
                    "under all-hard the plan-level order metric counted a violation the HARD "
                            + "invariant had already ruled out, so the metric is wrong",
                    List.of("order inversions " + metrics.orderInversions(),
                            "dependents without prerequisite "
                                    + metrics.dependentsWithoutPrerequisite()));
        }
    }
}
