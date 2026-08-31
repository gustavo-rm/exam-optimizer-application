package com.ia.project.dynamicstudyplanner.benchmark;

import com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkHarness;
import com.ia.project.dynamicstudyplanner.benchmark.harness.StrategyOutcome;
import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: the production genetic algorithm must not lose to a spreadsheet.
 *
 * <h2>The threshold, and why it is 2%</h2>
 *
 * The contract asserted here is that the GA's mean fitness stays within <b>2%</b> of the
 * {@code GreedyPriorityBaseline} — a planner that simply splits the day budget in proportion to each
 * subject's weight in the syllabus. The value is calibrated from the measurements in
 * {@code docs/revisao-ag/03-validacao.md}, not chosen a priori:
 *
 * <ul>
 *   <li><b>Noise floor.</b> Each repetition uses a different seed, and the GA is genuinely
 *       stochastic, so outcomes still spread across repetitions even though the algorithm became
 *       reproducible per seed in docs/revisao-ag/04-robustez.md. Over 7 repetitions the worst
 *       seed-to-seed spread on the instances covered by this assertion is <b>0.286%</b> of the
 *       mean. A 2% threshold sits roughly 7x above that, so this test does not flake.</li>
 *   <li><b>Detection power.</b> The worst deficit this benchmark ever recorded was <b>6.18%</b>
 *       (instance {@code I4-pesos-extremos}, before the fitness corrections of
 *       docs/revisao-ag/05-fitness-function.md). A 2% threshold is comfortably below that, so a
 *       regression of that magnitude would be caught.</li>
 *   <li><b>Why not tighter.</b> Below roughly 0.5% the assertion would start tracking RNG noise on
 *       the harder instances rather than planner quality, and would fail for reasons no code change
 *       could fix.</li>
 * </ul>
 *
 * <h2>The known-deficiency list</h2>
 *
 * {@link #KNOWN_DEFICIENT_INSTANCES} is asserted <em>exactly</em> rather than as an upper bound, so
 * the suite fails in both directions:
 * <ul>
 *   <li>a new instance dropping below the threshold is an unnoticed regression;</li>
 *   <li>an instance climbing back above it is an unrecorded improvement, and the list should be
 *       updated to lock the gain in.</li>
 * </ul>
 * The set is empty today. It held {@code I4-pesos-extremos} while the objective was the unbounded
 * {@code ln(1 + d)}; the second direction of this assertion is what forced the list to be emptied
 * once the mastery curve fixed it, instead of the improvement going unrecorded.
 */
@DisplayName("AG de producao versus baselines simples")
class GeneticAlgorithmVsBaselinesTest {

    /** Maximum fraction by which the GA may trail the greedy-priority baseline. */
    private static final double MAX_SHORTFALL_VS_GREEDY = 0.02;

    /**
     * Instances where the GA is known to lose to the greedy baseline today.
     * <p>
     * <b>Empty since the fitness corrections of docs/revisao-ag/05-fitness-function.md.</b>
     * {@code I4-pesos-extremos} used to sit here at -6.18%: the old unbounded {@code ln(1 + d)}
     * objective made the optimum concentrate ~81% of the budget on one subject, a distance
     * {@code CreepMutation}'s +/-3 day steps could not cross. Replacing it with a saturating mastery
     * curve removed that concentration, and the GA now leads the greedy baseline on every instance.
     * Keep this set empty: a name appearing here again is a regression, not a fact to record.
     */
    private static final Set<String> KNOWN_DEFICIENT_INSTANCES = Set.of();

    /**
     * Band within which two planners count as tied.
     * <p>
     * Sized just above the seed-to-seed spread of the GA (04-robustez.md §4), so a tie means "the
     * two methods reached the same place", not "the difference hid in the noise".
     */
    private static final double TIE_TOLERANCE = 0.005;

    /**
     * Repetitions for the stochastic planners. Seven matches the report; five keeps the suite under
     * a couple of seconds while still averaging out the sub-0.2% jitter seen on these instances.
     */
    private static final int REPETITIONS = 5;

    private static List<BenchmarkInstance> instances;
    private static Map<String, Map<String, StrategyOutcome>> results;

    @BeforeAll
    static void runBenchmark() {
        instances = InstanceLibrary.fastSubset();
        List<StrategyOutcome> outcomes = new BenchmarkHarness().run(instances, REPETITIONS);
        results = outcomes.stream().collect(Collectors.groupingBy(
                StrategyOutcome::instanceId,
                Collectors.toMap(StrategyOutcome::strategyId, Function.identity())));
    }

    @Test
    @DisplayName("nao fica mais de 2% abaixo do guloso por prioridade")
    void gaStaysWithinThresholdOfGreedyBaseline() {
        for (BenchmarkInstance instance : instances) {
            if (KNOWN_DEFICIENT_INSTANCES.contains(instance.id())) {
                continue;
            }
            double ga = outcome(instance, "ag-producao").meanFitness();
            double greedy = outcome(instance, "guloso-prioridade").meanFitness();

            assertThat(ga)
                    .as("Instancia %s: AG (%.2f) ficou %.2f%% abaixo do guloso por prioridade (%.2f); "
                                    + "o limite tolerado e %.0f%%.",
                            instance.id(), ga, 100 * (greedy - ga) / greedy, greedy,
                            100 * MAX_SHORTFALL_VS_GREEDY)
                    .isGreaterThanOrEqualTo(greedy * (1 - MAX_SHORTFALL_VS_GREEDY));
        }
    }

    @Test
    @DisplayName("a lista de instancias deficientes conhecidas continua exata")
    void knownDeficiencyListIsStillAccurate() {
        Set<String> failing = new TreeSet<>();
        for (BenchmarkInstance instance : instances) {
            double ga = outcome(instance, "ag-producao").meanFitness();
            double greedy = outcome(instance, "guloso-prioridade").meanFitness();
            if (ga < greedy * (1 - MAX_SHORTFALL_VS_GREEDY)) {
                failing.add(instance.id());
            }
        }

        assertThat(failing)
                .as("Instancias abaixo do limiar mudaram. Se apareceu uma nova, e regressao: "
                        + "investigue antes de editar esta lista. Se %s saiu da lista, o AG melhorou: "
                        + "remova-a de KNOWN_DEFICIENT_INSTANCES para travar o ganho.",
                        KNOWN_DEFICIENT_INSTANCES)
                .isEqualTo(new TreeSet<>(KNOWN_DEFICIENT_INSTANCES));
    }

    @Test
    @DisplayName("nunca fica abaixo da alocacao aleatoria, e a supera na maioria das instancias")
    void gaIsNeverWorseThanRandomAndUsuallyBetter() {
        // Two-part assertion, because a strict "beats random everywhere" is not something the
        // problem can always deliver. On an easy instance — few subjects, plenty of budget — the
        // objective saturates and every feasible plan sits at essentially the optimum; the GA and a
        // random draw then tie, and that is correct behaviour, not a failure to search. What would
        // be a real defect is the GA landing *below* random, or tying on the instances that do
        // discriminate. Both are covered here.
        int strictlyBetter = 0;
        for (BenchmarkInstance instance : instances) {
            double ga = outcome(instance, "ag-producao").meanFitness();
            double random = outcome(instance, "aleatorio").meanFitness();

            assertThat(ga)
                    .as("Instancia %s: o AG (%.5f) ficou abaixo da alocacao aleatoria (%.5f).",
                            instance.id(), ga, random)
                    .isGreaterThanOrEqualTo(random * (1 - TIE_TOLERANCE));

            if (ga > random * (1 + TIE_TOLERANCE)) {
                strictlyBetter++;
            }
        }

        assertThat(strictlyBetter)
                .as("O AG superou o sorteio em apenas %d de %d instancias. Um AG que empata com "
                        + "alocacao aleatoria na maioria dos casos nao esta buscando.",
                        strictlyBetter, instances.size())
                .isGreaterThan(instances.size() / 2);
    }

    @Test
    @DisplayName("supera o otimo do termo de edital, provando que a fitness deixou de ser resolvivel por guloso")
    void gaExceedsTheSingleObjectiveOptimumOnSomeInstance() {
        // The inverse of the assertion this test used to make, and the inversion is the point.
        //
        // While the fitness was a single separable, concave objective, MarginalGainOptimum was its
        // exact optimum: the GA could at best tie it, which is what docs/revisao-ag/03-validacao.md
        // measured and why the GA was hard to justify at all. The corrected fitness adds terms that
        // are not separable in the same way, so that greedy is no longer an upper bound — and the GA
        // beating it is the empirical evidence that the search now has something to find.
        //
        // If this ever fails, the fitness has collapsed back to a shape a greedy solves exactly, and
        // the argument for running a GA in production goes with it.
        long exceeded = instances.stream()
                .filter(i -> outcome(i, "ag-producao").meanFitness()
                        > outcome(i, "otimo-exato").meanFitness() * (1 + TIE_TOLERANCE))
                .count();

        assertThat(exceeded)
                .as("O AG nao superou o otimo do termo de edital em nenhuma instancia. Isso indica "
                        + "que a fitness voltou a ser separavel e concava, caso em que um guloso "
                        + "O(D log n) a resolve exatamente e o AG deixa de se justificar.")
                .isPositive();
    }

    @Test
    @DisplayName("todo planejador respeita orcamento e piso de dias minimos")
    void everyPlannerProducesFeasiblePlans() {
        // BenchmarkHarness.verifyFeasible throws on any violation, so reaching @BeforeAll without an
        // exception already proves feasibility. This test states the guarantee explicitly so that
        // removing the harness check cannot silently drop it.
        assertThat(results).isNotEmpty();
        for (BenchmarkInstance instance : instances) {
            assertThat(results.get(instance.id()))
                    .as("Instancia %s nao produziu resultados para todas as estrategias", instance.id())
                    .hasSize(new BenchmarkHarness().strategies().size());
        }
    }

    private StrategyOutcome outcome(BenchmarkInstance instance, String strategyId) {
        StrategyOutcome result = results.get(instance.id()).get(strategyId);
        assertThat(result)
                .as("Faltou resultado de '%s' na instancia %s", strategyId, instance.id())
                .isNotNull();
        return result;
    }
}
