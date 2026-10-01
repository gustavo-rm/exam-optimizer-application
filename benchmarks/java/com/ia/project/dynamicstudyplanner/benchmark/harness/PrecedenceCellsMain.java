package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.DynamicStudyPlannerApplication;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.metric.MeasurementRow;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.plan.PrecedencePolicy;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine;
import com.ia.project.dynamicstudyplanner.sinapse.TimelinePlanEngine;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * O 2×2 que desconfunde representação e política de precedência — a pendência <b>EOA-9, itens 1–3</b>.
 *
 * <h2>O confundimento que esta execução desfaz</h2>
 *
 * As etapas 9 e 10 compararam a v1 com a v2 e acharam duas diferenças ao mesmo tempo: a v1 representa
 * a ordem fora do cromossomo e a <b>repara</b> lexicograficamente; a v2 representa a ordem no
 * cromossomo e a <b>precifica</b> na fitness. Dois fatores mudaram juntos, então nenhuma das duas
 * diferenças de resultado — a v1 ganha em inversões, a v2 ganha em cobertura — podia ser atribuída a
 * um deles.
 *
 * <p>A etapa 11 varreu o preço e fechou um dos lados: nenhum λ admissível leva a v2 às 420 inversões
 * da v1. Mas isso ainda compara <i>reparo contra preço</i>. O que falta é a célula que falta:
 * <b>v2′ = linha do tempo + reparo lexicográfico</b>. Com as quatro células o efeito de cada fator é
 * separável, porque o desenho é fatorial completo nas mesmas instâncias e nas mesmas sementes.
 *
 * <pre>
 *                     | LEXICOGRAPHIC      | WEIGHTED
 *   ga (macro)        | v1  (publicada)    | v1″
 *   ga-timeline       | v2′ (a que decide) | v2  (publicada)
 * </pre>
 *
 * <h2>Duas células já foram publicadas, e isso é a verificação e não redundância</h2>
 *
 * {@code ga/LEXICOGRAPHIC} é a v1 da etapa 9 e {@code ga-timeline/WEIGHTED} é a v2 da etapa 10:
 * nomear a política explicitamente resolve para o padrão que cada motor já tinha. Logo essas duas
 * células <b>têm de reproduzir número por número</b> o que está publicado. Se não reproduzirem, o
 * eixo de política contaminou a condição de controle e nada mais nesta execução pode ser lido — é a
 * regra de parada que esta classe existe para poder exercer.
 *
 * <h2>Nenhum reparador novo foi escrito</h2>
 *
 * A v2′ aplica {@code PrerequisiteOrderRepairer} — o mesmo da v1, byte por byte — à ordem que a linha
 * do tempo produziu, e recoloca os blocos por {@code TimelineRepairer}. Um segundo reparador faria o
 * fator "política" carregar também uma diferença de implementação, que é o confundimento que esta
 * execução veio desfazer.
 *
 * <h2>λ fica em 0,10</h2>
 *
 * O padrão de produção, sem sobrescrita de propriedade. A varredura da etapa 11 já mediu o eixo do
 * peso e concluiu que 0,10 está dentro do ótimo plano; varrê-lo outra vez aqui cruzaria dois eixos
 * para responder a uma terceira pergunta. Nas células {@code LEXICOGRAPHIC} o peso não alcança o
 * resultado de ordem de todo modo, porque o reparo o fixa antes de a fitness o precificar.
 *
 * <h2>Como rodar</h2>
 *
 * <pre>
 *   ./mvnw -q test-compile
 *   ./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
 *   java -cp "target/classes:target/test-classes:$(cat target/cp.txt)" \
 *        com.ia.project.dynamicstudyplanner.benchmark.harness.PrecedenceCellsMain
 * </pre>
 */
public final class PrecedenceCellsMain {

    /** Onde o 2×2 escreve. */
    public static final Path OUTPUT = Path.of("benchmarks", "results", "precedence-cells.csv");

    /** Os dois motores que participam do eixo. O guloso não avalia precedência e está fora. */
    public static final List<String> ENGINES =
            List.of(GeneticPlanEngine.ID, TimelinePlanEngine.ID);

    /** As duas políticas. A ordem é a do desenho: reparo primeiro, preço depois. */
    public static final List<PrecedencePolicy> POLICIES =
            List.of(PrecedencePolicy.LEXICOGRAPHIC, PrecedencePolicy.WEIGHTED);

    private PrecedenceCellsMain() {
    }

    /**
     * @param args ignorados; as células, as instâncias e as sementes estão fixadas no repositório
     */
    public static void main(String[] args) {
        // Invariante de ambiente, antes de qualquer medicao: ver Environment.
        Environment.requireExpectedJdk();

        SpringApplicationBuilder builder = new SpringApplicationBuilder(
                DynamicStudyPlannerApplication.class)
                .profiles(PlanProtocol.PROFILE)
                .properties("server.port=0", "spring.main.banner-mode=off");

        try (ConfigurableApplicationContext context = builder.run()) {
            List<BenchmarkInstance> instances = InstanceLibrary.all();
            List<Condition> cells = cells();
            MeasurementHarness harness = MeasurementMain.harnessIn(context);

            long startedAt = System.nanoTime();
            List<MeasurementRow> rows = run(harness, instances, cells);
            long elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000L;

            List<String> terms = MeasurementMain.termNamesIn(context);
            MeasurementCsv.write(OUTPUT, terms, rows);
            Environment.writeBeside(OUTPUT);
            report(instances, cells, rows, elapsedSeconds);
        }
    }

    /** @return as quatro células, cada uma cruzada com as três proveniências */
    public static List<Condition> cells() {
        List<Condition> cells = new ArrayList<>(
                ENGINES.size() * POLICIES.size() * Condition.PROVENANCES.size());
        for (String engine : ENGINES) {
            for (PrecedencePolicy policy : POLICIES) {
                for (EdgeProvenanceFilter provenance : Condition.PROVENANCES) {
                    cells.add(new Condition(engine, provenance, policy));
                }
            }
        }
        return List.copyOf(cells);
    }

    /**
     * Toda a matriz, instância por instância.
     *
     * <p>A ordem do laço põe a instância por fora: as quatro células de uma instância rodam juntas, o
     * que mantém o pareamento legível no CSV sem que a análise dependa da ordem das linhas.
     */
    private static List<MeasurementRow> run(MeasurementHarness harness,
            List<BenchmarkInstance> instances, List<Condition> cells) {

        List<MeasurementRow> rows = new ArrayList<>(
                instances.size() * cells.size() * MeasurementHarness.SEEDS.length);
        for (BenchmarkInstance instance : instances) {
            for (Condition cell : cells) {
                for (long seed : MeasurementHarness.SEEDS) {
                    rows.add(harness.measure(instance, cell, seed));
                }
            }
            System.out.printf("%s done (%d rows so far)%n", instance.id(), rows.size());
        }
        return List.copyOf(rows);
    }

    /** O que a execução fez, no stdout. Nenhum número do relatório sai daqui — todos saem do CSV. */
    private static void report(List<BenchmarkInstance> instances, List<Condition> cells,
            List<MeasurementRow> rows, long elapsedSeconds) {

        System.out.println("instances : " + instances.size());
        System.out.println("cells     : " + cells.size()
                + " " + cells.stream().map(Condition::label).toList());
        System.out.println("seeds     : " + MeasurementHarness.SEEDS.length);
        System.out.println("rows      : " + rows.size());
        System.out.println("elapsed   : " + elapsedSeconds + " s");
        System.out.println("written   : " + OUTPUT.toAbsolutePath());
    }
}
