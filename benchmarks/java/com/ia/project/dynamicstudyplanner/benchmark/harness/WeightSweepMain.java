package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.DynamicStudyPlannerApplication;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.metric.MeasurementRow;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.SoftPrerequisiteOrderConstraint;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine;
import com.ia.project.dynamicstudyplanner.sinapse.TimelinePlanEngine;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Varre o peso das preferências de ordem e mede a v2 em cada ponto — a pendência <b>G16</b>.
 *
 * <h2>A pergunta, e por que só esta varredura a responde</h2>
 *
 * A etapa 10 mediu que a v2 perde da v1 em inversões {@code SOFT} e mostrou a causa: com
 * {@code SOFT_PREREQUISITE_ORDER = 0,10}, a busca da v2 troca ordem por cobertura <b>com lucro pelo
 * critério que recebeu</b>. A v1 não faz a troca porque não a avalia — o reparo dela é lexicográfico.
 *
 * <p>Logo aquela comparação mede <b>reparo contra preço</b>, e não representação contra
 * representação. A pergunta original — "a ordem como gene planeja melhor?" — fica sem resposta até
 * que se saiba o que a v2 faz quando o preço muda. É o que isto mede.
 *
 * <p>O valor 0,10 vem de um argumento de ordenação, não de medição, e o Javadoc de
 * {@code FitnessWeights.SOFT_PREREQUISITE_ORDER} diz exatamente o que fazer: "se [as inversões] forem
 * comuns e os planos piores por causa delas, o peso é o que se deve subir, <b>a partir de dados</b>".
 *
 * <h2>Só a v2 é remedida em cada ponto, e isso é verificado e não suposto</h2>
 *
 * O termo de preferências devolve {@code 0,0} num plano macro por construção, então durante a busca
 * da v1 ele vale {@code peso × 0} qualquer que seja o peso: <b>o plano da v1 não depende de λ</b>. O
 * guloso não avalia fitness nenhuma. Remedir os três em cada ponto seria gastar seis vezes o tempo
 * para reescrever as mesmas linhas.
 *
 * <p>Mas o argumento é uma leitura de código, e leitura de código já errou nesta linha de trabalho
 * (ver a nota de correção em §6 da etapa 10). Então a v1 é medida nos <b>dois extremos</b> da
 * varredura — {@value #INVARIANCE_LOW} e {@value #INVARIANCE_HIGH} — e a análise confere que as
 * colunas de resultado dela são idênticas. Se não forem, a premissa caiu e a varredura toda precisa
 * ser relida.
 *
 * <h2>O que é comparável entre pontos, e o que não é</h2>
 *
 * As métricas de <b>resultado</b> são propriedades do cronograma — inversões, tópicos agendados,
 * utilização — e não dependem de λ. São diretamente comparáveis, e é nelas que a conclusão se apoia.
 *
 * <p>A coluna de <b>objetivo</b> não é: cada ponto a calcula com o seu próprio λ, então
 * {@code ga_objective_aggregate} em λ=0,80 e em λ=0,05 são valores de funções diferentes. <b>Ela pode
 * ser trazida a um λ comum por aritmética exata sobre as colunas publicadas</b>, porque o CSV carrega
 * a severidade e o peso do termo separadamente:
 *
 * <pre>
 *   raw_canônico = raw + λ_varrido × severidade − 0,10 × severidade
 * </pre>
 *
 * É o que o relatório faz, declarando a fórmula. Nenhuma composição é reconstruída — a regra de que o
 * avaliador a posteriori é o injetado, nunca um recriado, continua valendo em cada contexto.
 */
public final class WeightSweepMain {

    /**
     * Os pontos da varredura.
     *
     * <p>{@code 0,00} é o controle: a ordem deixa de ter preço e a v2 só otimiza cobertura e
     * retenção. {@code 0,10} é o valor de produção. Acima dele os pontos dobram, porque o que se
     * procura é a <b>ordem de grandeza</b> em que o comportamento vira, não um ótimo fino — e dobrar
     * cobre duas décadas com seis pontos. {@code 0,80} já está acima de
     * {@code CONSTRAINT_VIOLATION} (0,50), ou seja, além do ponto em que uma preferência passaria a
     * custar mais que um requisito — que é justamente onde o argumento de ordenação diz que não se
     * deve ir, e portanto onde interessa ver o que acontece.
     */
    public static final double[] WEIGHTS = {0.00, 0.05, 0.10, 0.20, 0.40, 0.80};

    /** Extremo inferior em que a invariância da v1 é conferida. */
    public static final double INVARIANCE_LOW = 0.00;

    /** Extremo superior em que a invariância da v1 é conferida. */
    public static final double INVARIANCE_HIGH = 0.80;

    /** Onde a varredura escreve. */
    public static final Path OUTPUT = Path.of("benchmarks", "results", "soft-weight-sweep.csv");

    private WeightSweepMain() {
    }

    /**
     * @param args ignorados; os pontos e as sementes estão fixados no repositório
     */
    public static void main(String[] args) {
        // Invariante de ambiente, antes de qualquer medicao: ver Environment.
        Environment.requireExpectedJdk();

        List<String> lines = new ArrayList<>();
        List<String> header = null;

        long startedAt = System.nanoTime();
        for (double weight : WEIGHTS) {
            try (ConfigurableApplicationContext context = boot(weight)) {
                List<String> terms = MeasurementMain.termNamesIn(context);
                if (header == null) {
                    header = new ArrayList<>();
                    header.add("soft_weight");
                    header.addAll(MeasurementRow.header(terms));
                    lines.add(String.join(",", header));
                }
                sweepPoint(context, weight, terms, lines);
            }
            System.out.printf("weight %.2f done (%d lines so far)%n", weight, lines.size() - 1);
        }

        MeasurementCsv.writeLines(OUTPUT, lines);
        Environment.writeBeside(OUTPUT);
        System.out.printf("%npoints %d | rows %d | elapsed %d s | written %s%n",
                WEIGHTS.length, lines.size() - 1,
                (System.nanoTime() - startedAt) / 1_000_000_000L, OUTPUT.toAbsolutePath());
    }

    /** Um contexto com o profile do protocolo e este ponto da varredura instalado. */
    private static ConfigurableApplicationContext boot(double weight) {
        return new SpringApplicationBuilder(DynamicStudyPlannerApplication.class)
                .profiles(PlanProtocol.PROFILE)
                .properties("server.port=0", "spring.main.banner-mode=off",
                        SoftPrerequisiteOrderConstraint.WEIGHT_PROPERTY + "=" + weight)
                .run();
    }

    /** Toda a matriz da v2 neste ponto, mais a v1 nos dois extremos. */
    private static void sweepPoint(ConfigurableApplicationContext context, double weight,
            List<String> terms, List<String> lines) {

        MeasurementHarness harness = MeasurementMain.harnessIn(context);
        List<String> engines = enginesFor(weight);

        for (BenchmarkInstance instance : InstanceLibrary.all()) {
            for (String engine : engines) {
                for (EdgeProvenanceFilter provenance : Condition.PROVENANCES) {
                    Condition condition = new Condition(engine, provenance);
                    for (long seed : MeasurementHarness.SEEDS) {
                        MeasurementRow row = harness.measure(instance, condition, seed);
                        List<String> cells = new ArrayList<>();
                        cells.add(MeasurementRow.number(weight));
                        cells.addAll(row.cells(terms));
                        lines.add(String.join(",", cells));
                    }
                }
            }
        }
    }

    /**
     * Quais motores medir neste ponto.
     *
     * <p>A v2 sempre. A v1 só nos extremos, para a checagem de invariância — ver o comentário da
     * classe. O guloso nunca: ele não avalia fitness, então λ não o alcança por nenhum caminho, e a
     * etapa 10 já o mediu.
     */
    private static List<String> enginesFor(double weight) {
        boolean extreme = weight == INVARIANCE_LOW || weight == INVARIANCE_HIGH;
        return extreme
                ? List.of(TimelinePlanEngine.ID, GeneticPlanEngine.ID)
                : List.of(TimelinePlanEngine.ID);
    }
}
